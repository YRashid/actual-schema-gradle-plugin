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
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

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
                buildStaleSnapshotMessage(expected, generated)
            )
        }
        logger.lifecycle("Actual database schema is up to date: {}", expected)
    }

    private fun buildStaleSnapshotMessage(expected: File, generated: File): String =
        "Actual database schema is out of date: ${expected.absolutePath}. " +
            "Generated candidate: ${generated.absolutePath}. " +
            firstDifferenceDescription(expected.toPath(), generated.toPath()) + " " +
            "Inspect the diff with: diff -u ${shellQuote(expected.absolutePath)} ${shellQuote(generated.absolutePath)}. " +
            "Run ./gradlew generateActualSchema and commit the updated schema snapshot."
}

internal fun firstDifferenceDescription(expected: Path, generated: Path, maxPreviewChars: Int = 160): String {
    val expectedLines = try {
        Files.readAllLines(expected, StandardCharsets.UTF_8)
    } catch (_: Exception) {
        return "The first byte difference is at offset ${Files.mismatch(expected, generated)}."
    }
    val generatedLines = try {
        Files.readAllLines(generated, StandardCharsets.UTF_8)
    } catch (_: Exception) {
        return "The first byte difference is at offset ${Files.mismatch(expected, generated)}."
    }

    val lineCount = maxOf(expectedLines.size, generatedLines.size)
    for (index in 0 until lineCount) {
        val expectedLine = expectedLines.getOrNull(index)
        val generatedLine = generatedLines.getOrNull(index)
        if (expectedLine != generatedLine) {
            return buildString {
                append("First differing line: ")
                append(index + 1)
                append(". Expected: ")
                append(previewLine(expectedLine, maxPreviewChars))
                append(". Generated: ")
                append(previewLine(generatedLine, maxPreviewChars))
                append(".")
            }
        }
    }

    return "The files differ in bytes outside the UTF-8 line comparison."
}

internal fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

private fun previewLine(value: String?, maxPreviewChars: Int): String {
    val preview = value ?: "<missing line>"
    return if (preview.length <= maxPreviewChars) {
        preview
    } else {
        preview.take(maxPreviewChars) + "..."
    }
}
