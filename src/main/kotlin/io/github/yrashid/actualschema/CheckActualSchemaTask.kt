package io.github.yrashid.actualschema

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.nio.file.Files

@DisableCachingByDefault(because = "This verification task has no outputs")
abstract class CheckActualSchemaTask : DefaultTask() {
    // InputFiles deliberately permits a missing snapshot so the task can report an actionable
    // generateActualSchema command instead of Gradle failing during input validation.
    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val expectedSchemaFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val generatedSchemaFile: RegularFileProperty

    @TaskAction
    fun checkSchema() {
        val expected = expectedSchemaFile.get().asFile
        val generated = generatedSchemaFile.get().asFile
        if (!expected.isFile) {
            throw GradleException(
                "Expected schema snapshot does not exist: ${expected.absolutePath}. " +
                    "Run ./gradlew generateActualSchema to create it."
            )
        }
        if (Files.mismatch(expected.toPath(), generated.toPath()) != -1L) {
            throw GradleException(
                "Actual database schema is out of date: ${expected.absolutePath}. " +
                    "Generated candidate: ${generated.absolutePath}. " +
                    "Run ./gradlew generateActualSchema and commit the updated schema snapshot."
            )
        }
        logger.lifecycle("Actual database schema is up to date: {}", expected)
    }
}
