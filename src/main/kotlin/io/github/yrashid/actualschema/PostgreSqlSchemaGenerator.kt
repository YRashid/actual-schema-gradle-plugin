package io.github.yrashid.actualschema

import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import liquibase.resource.CompositeResourceAccessor
import liquibase.resource.DirectoryResourceAccessor
import liquibase.resource.ResourceAccessor
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.postgresql.Driver
import org.testcontainers.DockerClientFactory
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.io.File
import java.sql.Connection
import java.util.Properties

/** Execution boundary that allows another database implementation to be added later. */
internal interface DatabaseSchemaGenerator<C : DatabaseGenerationConfig> {
    fun generate(config: C): String
}

internal sealed interface DatabaseGenerationConfig

internal data class PostgreSqlGenerationConfig(
    val changelog: File,
    val resourceBaseDir: File,
    val image: String,
    val imageCompatibleSubstituteFor: String,
    val databaseName: String,
    val username: String,
    val password: String,
    val schemas: List<String>,
    val excludedTables: Set<String>,
    val liquibaseContexts: List<String>,
    val liquibaseLabels: List<String>,
    val liquibaseParameters: Map<String, String>,
    val liquibaseDefaultSchema: String?,
    val liquibaseSchema: String?,
    val liquibaseChangeLogTable: String,
    val liquibaseChangeLogLockTable: String,
    val normalizeOutput: Boolean
) : DatabaseGenerationConfig

internal class PostgreSqlSchemaGenerator(
    private val logger: Logger
) : DatabaseSchemaGenerator<PostgreSqlGenerationConfig> {
    override fun generate(config: PostgreSqlGenerationConfig): String {
        verifyDocker()
        val imageName = DockerImageName.parse(config.image)
            .asCompatibleSubstituteFor(config.imageCompatibleSubstituteFor)
        val container = PostgreSQLContainer(imageName)
            .withDatabaseName(config.databaseName)
            .withUsername(config.username)
            .withPassword(config.password)
        var started = false

        try {
            logger.lifecycle("Starting temporary PostgreSQL container ({})", config.image)
            try {
                container.start()
                started = true
            } catch (exception: Exception) {
                throw GradleException(
                    "Failed to start temporary PostgreSQL container '${config.image}'. " +
                        "Check that Docker is running and the image is available.",
                    exception
                )
            }

            applyLiquibase(container, config)
            return runPgDump(container, config)
        } finally {
            if (started) {
                logger.lifecycle("Stopping temporary PostgreSQL container")
            }
            // stop() is also safe after a partially failed start and cleans up a container ID
            // that may have been allocated before startup failed.
            try {
                container.stop()
            } catch (exception: Exception) {
                logger.warn("Failed to stop temporary PostgreSQL container; Testcontainers/Ryuk will retry cleanup", exception)
            }
        }
    }

    private fun verifyDocker() {
        val available = try {
            DockerClientFactory.instance().isDockerAvailable
        } catch (exception: Exception) {
            throw GradleException(
                "Docker is unavailable. Start Docker or configure a compatible Docker daemon before running generateActualSchema.",
                exception
            )
        }
        if (!available) {
            throw GradleException(
                "Docker is unavailable. Start Docker or configure a compatible Docker daemon before running generateActualSchema."
            )
        }
    }

    @Suppress("DEPRECATION") // Liquibase 4.x keeps this stable Java facade overload deprecated.
    private fun applyLiquibase(
        container: PostgreSQLContainer,
        config: PostgreSqlGenerationConfig
    ) {
        logger.lifecycle("Applying Liquibase changelog {}", config.changelog)
        try {
            createResourceAccessor(config).use { resourceAccessor ->
                val connectionProperties = Properties().apply {
                    setProperty("user", container.username)
                    setProperty("password", container.password)
                }
                // DriverManager can hide service-loaded JDBC drivers across Gradle plugin
                // classloader boundaries, so invoke the plugin-owned driver directly.
                val connection: Connection = Driver().connect(container.jdbcUrl, connectionProperties)
                    ?: throw IllegalStateException("PostgreSQL JDBC driver rejected URL: ${container.jdbcUrl}")
                connection.use {
                    val database = DatabaseFactory.getInstance()
                        .findCorrectDatabaseImplementation(JdbcConnection(connection))
                    config.liquibaseDefaultSchema?.let(database::setDefaultSchemaName)
                    config.liquibaseSchema?.let(database::setLiquibaseSchemaName)
                    database.databaseChangeLogTableName = config.liquibaseChangeLogTable
                    database.databaseChangeLogLockTableName = config.liquibaseChangeLogLockTable

                    Liquibase(changelogPath(config), resourceAccessor, database).use { liquibase ->
                        config.liquibaseParameters.forEach(liquibase::setChangeLogParameter)
                        liquibase.update(
                            Contexts(config.liquibaseContexts),
                            LabelExpression(config.liquibaseLabels)
                        )
                    }
                }
            }
        } catch (exception: Exception) {
            throw GradleException(
                "Liquibase migration failed for changelog '${config.changelog.absolutePath}'. " +
                    "The temporary database has been discarded.",
                exception
            )
        }
    }

    private fun runPgDump(
        container: PostgreSQLContainer,
        config: PostgreSqlGenerationConfig
    ): String {
        val command = mutableListOf(
            "env",
            "PGPASSWORD=${config.password}",
            "pg_dump",
            "--host=127.0.0.1",
            "--port=5432",
            "--username=${config.username}",
            "--dbname=${config.databaseName}",
            "--schema-only",
            "--no-owner",
            "--no-privileges"
        )
        config.schemas.forEach { schema -> command += "--schema=$schema" }
        config.excludedTables.forEach { table -> command += "--exclude-table=$table" }

        logger.lifecycle("Running pg_dump inside the PostgreSQL container")
        val result = try {
            container.execInContainer(*command.toTypedArray())
        } catch (exception: Exception) {
            throw GradleException("pg_dump failed to start inside the PostgreSQL container.", exception)
        }
        if (result.exitCode != 0) {
            throw GradleException(
                "pg_dump failed with exit code ${result.exitCode}: ${result.stderr.trim()}"
            )
        }
        return if (config.normalizeOutput) normalizePgDump(result.stdout) else result.stdout
    }

    private fun createResourceAccessor(config: PostgreSqlGenerationConfig): CompositeResourceAccessor {
        val accessors = mutableListOf<ResourceAccessor>(DirectoryResourceAccessor(config.resourceBaseDir))
        if (config.changelog.parentFile.canonicalFile != config.resourceBaseDir.canonicalFile) {
            accessors += DirectoryResourceAccessor(config.changelog.parentFile)
        }
        accessors += ClassLoaderResourceAccessor(Thread.currentThread().contextClassLoader)
        return CompositeResourceAccessor(accessors)
    }

    private fun changelogPath(config: PostgreSqlGenerationConfig): String {
        val base = config.resourceBaseDir.toPath().toAbsolutePath().normalize()
        val changelog = config.changelog.toPath().toAbsolutePath().normalize()
        return if (changelog.startsWith(base)) {
            base.relativize(changelog).toString().replace(File.separatorChar, '/')
        } else {
            config.changelog.name
        }
    }

}

internal fun normalizePgDump(dump: String): String = dump
    .lineSequence()
    .filterNot { it.startsWith("-- Dumped from database version") }
    .filterNot { it.startsWith("-- Dumped by pg_dump version") }
    .filterNot { it.startsWith("\\restrict ") }
    .filterNot { it.startsWith("\\unrestrict ") }
    .joinToString("\n")
    .trimEnd() + "\n"
