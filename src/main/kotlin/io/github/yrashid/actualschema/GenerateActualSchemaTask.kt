package io.github.yrashid.actualschema

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject

@DisableCachingByDefault(
    because = "The output is produced by an external Docker image tag, which may be mutable; Gradle up-to-date checks still apply"
)
abstract class GenerateActualSchemaTask : DefaultTask() {
    init {
        // Included files next to a changelog outside resourceBaseDir are resolved at runtime but
        // cannot be declared precisely as inputs. Always rerun in that uncommon configuration.
        outputs.upToDateWhen {
            val resourceRoot = resourceBaseDir.get().asFile.toPath().toAbsolutePath().normalize()
            val changelog = changelogFile.get().asFile.toPath().toAbsolutePath().normalize()
            changelog.startsWith(resourceRoot)
        }
    }

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val changelogFile: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resourceBaseDir: DirectoryProperty

    @get:Classpath
    abstract val liquibaseRuntimeClasspath: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Input
    abstract val postgresImage: Property<String>

    @get:Input
    abstract val postgresImageCompatibleSubstituteFor: Property<String>

    @get:Input
    abstract val databaseName: Property<String>

    @get:Input
    abstract val username: Property<String>

    // The password does not affect generated DDL and must not leak into build-cache metadata.
    @get:Internal
    abstract val password: Property<String>

    @get:Input
    abstract val schemas: ListProperty<String>

    @get:Input
    abstract val excludeTables: SetProperty<String>

    @get:Input
    abstract val liquibaseContexts: ListProperty<String>

    @get:Input
    abstract val liquibaseLabels: ListProperty<String>

    @get:Input
    abstract val liquibaseParameters: MapProperty<String, String>

    @get:Input
    @get:Optional
    abstract val liquibaseDefaultSchema: Property<String>

    @get:Input
    @get:Optional
    abstract val liquibaseSchema: Property<String>

    @get:Input
    abstract val liquibaseChangeLogTable: Property<String>

    @get:Input
    abstract val liquibaseChangeLogLockTable: Property<String>

    @get:Input
    abstract val includeLiquibaseTables: Property<Boolean>

    @get:Input
    abstract val normalizeOutput: Property<Boolean>

    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    @TaskAction
    fun generate() {
        val changelog = changelogFile.get().asFile
        validateChangelog(changelog)
        val destination = outputFile.get().asFile
        prepareOutputDirectory(destination)
        val temporaryPath = createTemporaryOutput(destination)

        try {
            val workQueue = workerExecutor.classLoaderIsolation {
                classpath.from(liquibaseRuntimeClasspath)
            }
            workQueue.submit(GenerateSchemaWorkAction::class.java) {
                changelogFile.set(this@GenerateActualSchemaTask.changelogFile)
                resourceBaseDir.set(this@GenerateActualSchemaTask.resourceBaseDir)
                outputFile.set(temporaryPath.toFile())
                postgresImage.set(this@GenerateActualSchemaTask.postgresImage)
                postgresImageCompatibleSubstituteFor.set(
                    this@GenerateActualSchemaTask.postgresImageCompatibleSubstituteFor
                )
                databaseName.set(this@GenerateActualSchemaTask.databaseName)
                username.set(this@GenerateActualSchemaTask.username)
                password.set(this@GenerateActualSchemaTask.password)
                schemas.set(this@GenerateActualSchemaTask.schemas)
                excludedTables.set(effectiveExcludedTables())
                liquibaseContexts.set(this@GenerateActualSchemaTask.liquibaseContexts)
                liquibaseLabels.set(this@GenerateActualSchemaTask.liquibaseLabels)
                liquibaseParameters.set(this@GenerateActualSchemaTask.liquibaseParameters)
                liquibaseDefaultSchema.set(this@GenerateActualSchemaTask.liquibaseDefaultSchema)
                liquibaseSchema.set(this@GenerateActualSchemaTask.liquibaseSchema)
                liquibaseChangeLogTable.set(this@GenerateActualSchemaTask.liquibaseChangeLogTable)
                liquibaseChangeLogLockTable.set(this@GenerateActualSchemaTask.liquibaseChangeLogLockTable)
                normalizeOutput.set(this@GenerateActualSchemaTask.normalizeOutput)
            }
            workQueue.await()
            moveAtomically(temporaryPath.toFile(), destination)
            logger.lifecycle("Actual PostgreSQL schema written to {}", destination)
        } finally {
            Files.deleteIfExists(temporaryPath)
        }
    }

    private fun validateChangelog(changelog: File) {
        if (!changelog.isFile) {
            throw GradleException("Liquibase changelog not found: ${changelog.absolutePath}")
        }
        if (!changelog.canRead()) {
            throw GradleException("Liquibase changelog is not readable: ${changelog.absolutePath}")
        }
    }

    private fun prepareOutputDirectory(destination: File) {
        val directory = destination.absoluteFile.parentFile
            ?: throw GradleException("Output file must have a parent directory: $destination")
        try {
            Files.createDirectories(directory.toPath())
        } catch (exception: Exception) {
            throw GradleException("Cannot create output directory: ${directory.absolutePath}", exception)
        }
        if (!directory.isDirectory || !directory.canWrite()) {
            throw GradleException("Output directory is not writable: ${directory.absolutePath}")
        }
    }

    private fun createTemporaryOutput(destination: File) = try {
        Files.createTempFile(destination.toPath().parent, destination.name, ".tmp")
    } catch (exception: Exception) {
        throw GradleException("Cannot create temporary output file in ${destination.parent}", exception)
    }

    private fun moveAtomically(source: File, destination: File) {
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (exception: Exception) {
            throw GradleException("Cannot write generated schema to ${destination.absolutePath}", exception)
        }
    }

    private fun effectiveExcludedTables(): Set<String> = buildSet {
        addAll(excludeTables.get())
        if (!includeLiquibaseTables.get()) {
            val changeLogTable = liquibaseChangeLogTable.get()
            val lockTable = liquibaseChangeLogLockTable.get()
            val selectedSchemas = schemas.get()
            if (selectedSchemas.isEmpty()) {
                add("*.$changeLogTable")
                add("*.$lockTable")
            } else {
                val metadataSchema = liquibaseSchema.orNull
                    ?: liquibaseDefaultSchema.orNull
                    ?: "public"
                add("$metadataSchema.$changeLogTable")
                add("$metadataSchema.$lockTable")
            }
        }
    }
}
