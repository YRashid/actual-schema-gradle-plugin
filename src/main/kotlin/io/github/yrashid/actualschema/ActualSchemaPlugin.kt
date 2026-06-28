package io.github.yrashid.actualschema

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

class ActualSchemaPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("actualSchema", ActualSchemaExtension::class.java).apply {
            resourceBaseDir.convention(project.layout.projectDirectory.dir("src/main/resources"))
            postgresImage.convention("postgres:16")
            postgresImageCompatibleSubstituteFor.convention("postgres")
            postgresStartupTimeoutSeconds.convention(60)
            databaseName.convention("actual_schema")
            username.convention("actual_schema")
            password.convention("actual_schema")
            outputFile.convention(project.layout.buildDirectory.file("generated/actual-schema/schema.sql"))
            schemas.convention(listOf("public"))
            excludeTables.convention(emptySet())
            liquibaseContexts.convention(emptyList())
            liquibaseLabels.convention(emptyList())
            liquibaseParameters.convention(emptyMap())
            liquibaseChangeLogTable.convention("databasechangelog")
            liquibaseChangeLogLockTable.convention("databasechangeloglock")
            includeLiquibaseTables.convention(false)
            normalizeOutput.convention(true)
        }

        val liquibaseRuntime = project.configurations.create("actualSchemaLiquibaseRuntime") {
            isCanBeConsumed = false
            isCanBeResolved = true
            description = "Additional Liquibase extensions and custom change classes used by actual-schema"
        }

        val generateTask = registerGenerator(
            project = project,
            name = "generateActualSchema",
            extension = extension,
            liquibaseRuntime = liquibaseRuntime,
            destination = extension.outputFile
        ) {
            group = "database"
            description = "Generates final PostgreSQL DDL after applying all Liquibase migrations"
        }

        val checkCandidate = registerGenerator(
            project = project,
            name = "generateActualSchemaForCheck",
            extension = extension,
            liquibaseRuntime = liquibaseRuntime,
            destination = project.layout.buildDirectory.file("generated/actual-schema/check/schema.sql")
        ) {
            description = "Generates an isolated schema candidate for checkActualSchema"
        }

        project.tasks.register("checkActualSchema", CheckActualSchemaTask::class.java) {
            group = "verification"
            description = "Checks that the configured schema snapshot matches current Liquibase migrations"
            expectedSchemaFile.set(extension.outputFile)
            generatedSchemaFile.set(checkCandidate.flatMap(GenerateActualSchemaTask::outputFile))
            dependsOn(checkCandidate)
            mustRunAfter(generateTask)
        }
    }

    private fun registerGenerator(
        project: Project,
        name: String,
        extension: ActualSchemaExtension,
        liquibaseRuntime: Configuration,
        destination: Provider<RegularFile>,
        configure: GenerateActualSchemaTask.() -> Unit
    ): TaskProvider<GenerateActualSchemaTask> =
        project.tasks.register(name, GenerateActualSchemaTask::class.java) {
            configure()
            changelogFile.set(extension.changelogFile)
            resourceBaseDir.set(extension.resourceBaseDir)
            outputFile.set(destination)
            liquibaseRuntimeClasspath.from(liquibaseRuntime, extension.resourceBaseDir)
            postgresImage.set(extension.postgresImage)
            postgresImageCompatibleSubstituteFor.set(extension.postgresImageCompatibleSubstituteFor)
            postgresStartupTimeoutSeconds.set(extension.postgresStartupTimeoutSeconds)
            databaseName.set(extension.databaseName)
            username.set(extension.username)
            password.set(extension.password)
            schemas.set(extension.schemas)
            excludeTables.set(extension.excludeTables)
            liquibaseContexts.set(extension.liquibaseContexts)
            liquibaseLabels.set(extension.liquibaseLabels)
            liquibaseParameters.set(extension.liquibaseParameters)
            liquibaseDefaultSchema.set(extension.liquibaseDefaultSchema)
            liquibaseSchema.set(extension.liquibaseSchema)
            liquibaseChangeLogTable.set(extension.liquibaseChangeLogTable)
            liquibaseChangeLogLockTable.set(extension.liquibaseChangeLogLockTable)
            includeLiquibaseTables.set(extension.includeLiquibaseTables)
            normalizeOutput.set(extension.normalizeOutput)
        }
}
