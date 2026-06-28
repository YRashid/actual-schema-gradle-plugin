package io.github.yrashid.actualschema

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.testcontainers.DockerClientFactory
import java.nio.file.Files
import java.nio.file.Path

class GenerateActualSchemaFunctionalTest {
    @TempDir
    lateinit var projectDir: Path

    @Test
    fun `tracks included resources and produces stable final schema with configuration cache`() {
        assumeDockerAvailable()
        writeProject(testPostgresImage())

        val firstResult = runner("generateActualSchema", "--configuration-cache", "--rerun-tasks").build()
        assertEquals(TaskOutcome.SUCCESS, firstResult.task(":generateActualSchema")?.outcome)
        val output = projectDir.resolve("build/generated/actual-schema/schema.sql")
        assertTrue(Files.isRegularFile(output))
        val firstDump = Files.readString(output)
        assertTrue(firstDump.contains("CREATE TABLE public.widget"))
        assertFalse(firstDump.contains("audit_log"))
        assertFalse(firstDump.contains("ignored_widget"))
        assertFalse(firstDump.contains("databasechangelog", ignoreCase = true))
        assertFalse(firstDump.contains("Dumped from database version"))
        assertFalse(firstDump.contains("\\restrict "))

        val secondResult = runner("generateActualSchema", "--configuration-cache", "--rerun-tasks").build()
        assertEquals(TaskOutcome.SUCCESS, secondResult.task(":generateActualSchema")?.outcome)
        assertTrue(secondResult.output.contains("Configuration cache entry reused"))
        assertEquals(firstDump, Files.readString(output))

        Files.writeString(projectDir.resolve("src/main/resources/db/changes/002-extra.sql"), "CREATE TABLE extra_widget(id BIGINT);\n")
        Files.writeString(
            projectDir.resolve("src/main/resources/db/changelog.yml"),
            masterChangelog(includeExtra = true)
        )
        val changedResult = runner("generateActualSchema", "--configuration-cache").build()
        assertEquals(TaskOutcome.SUCCESS, changedResult.task(":generateActualSchema")?.outcome)
        assertTrue(Files.readString(output).contains("CREATE TABLE public.extra_widget"))

        val checkResult = runner("checkActualSchema", "--configuration-cache").build()
        assertEquals(TaskOutcome.SUCCESS, checkResult.task(":checkActualSchema")?.outcome)

        Files.writeString(output, Files.readString(output) + "-- stale snapshot\n")
        val staleResult = runner("checkActualSchema", "--configuration-cache").buildAndFail()
        assertTrue(staleResult.output.contains("Actual database schema is out of date"))
        assertTrue(staleResult.output.contains("First differing line"))
        assertTrue(staleResult.output.contains("diff -u"))
        assertTrue(staleResult.output.contains("check/schema.sql"))
        assertTrue(staleResult.output.contains("generateActualSchema"))
    }

    @Test
    fun `reports missing changelog clearly before Docker execution`() {
        Files.createDirectories(projectDir.resolve("src/main/resources"))
        Files.writeString(projectDir.resolve("settings.gradle.kts"), "rootProject.name = \"missing-changelog\"\n")
        Files.writeString(
            projectDir.resolve("build.gradle.kts"),
            """
            plugins { id("io.github.yrashid.actual-schema") }
            actualSchema {
                changelogFile.set(layout.projectDirectory.file("src/main/resources/missing.yml"))
            }
            """.trimIndent()
        )

        val result = runner("generateActualSchema").buildAndFail()

        assertTrue(result.output.contains("missing.yml"))
        assertTrue(result.output.contains("doesn't exist") || result.output.contains("not found"))
    }

    @Test
    fun `reports invalid configuration clearly before Docker execution`() {
        Files.createDirectories(projectDir.resolve("src/main/resources/db"))
        Files.writeString(projectDir.resolve("settings.gradle.kts"), "rootProject.name = \"invalid-configuration\"\n")
        Files.writeString(projectDir.resolve("src/main/resources/db/changelog.yml"), "databaseChangeLog: []\n")
        Files.writeString(
            projectDir.resolve("build.gradle.kts"),
            """
            plugins { id("io.github.yrashid.actual-schema") }
            actualSchema {
                changelogFile.set(layout.projectDirectory.file("src/main/resources/db/changelog.yml"))
                postgresImage.set(" ")
            }
            """.trimIndent()
        )

        val result = runner("generateActualSchema").buildAndFail()

        assertTrue(result.output.contains("actualSchema.postgresImage must not be blank"))
    }

    @Test
    fun `check reports a missing expected snapshot with the update command`() {
        assumeDockerAvailable()
        writeProject(testPostgresImage())

        val result = runner("checkActualSchema", "--configuration-cache").buildAndFail()

        assertTrue(result.output.contains("Expected schema snapshot does not exist"))
        assertTrue(result.output.contains("generateActualSchema"))
    }

    @Test
    fun `supports Groovy DSL formatted SQL changelog and labels`() {
        assumeDockerAvailable()
        Files.createDirectories(projectDir.resolve("src/main/resources/db"))
        Files.writeString(projectDir.resolve("settings.gradle"), "rootProject.name = 'groovy-formatted-sql'\n")
        Files.writeString(
            projectDir.resolve("build.gradle"),
            """
            plugins {
                id 'io.github.yrashid.actual-schema'
            }

            actualSchema {
                changelogFile.set(layout.projectDirectory.file('src/main/resources/db/changelog.sql'))
                resourceBaseDir.set(layout.projectDirectory.dir('src/main/resources'))
                outputFile.set(layout.buildDirectory.file('actual-schema/groovy.sql'))
                postgresImage.set('${testPostgresImage()}')
                postgresStartupTimeoutSeconds.set(120)
                liquibaseLabels.set(['core'])
            }
            """.trimIndent()
        )
        Files.writeString(
            projectDir.resolve("src/main/resources/db/changelog.sql"),
            """
            --liquibase formatted sql

            --changeset test:create-groovy labels:core
            CREATE TABLE public.groovy_widget(id BIGINT PRIMARY KEY);

            --changeset test:create-ignored labels:ignored
            CREATE TABLE public.ignored_label_widget(id BIGINT);
            """.trimIndent()
        )

        val result = runner("generateActualSchema", "--configuration-cache").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateActualSchema")?.outcome)
        val output = Files.readString(projectDir.resolve("build/actual-schema/groovy.sql"))
        assertTrue(output.contains("CREATE TABLE public.groovy_widget"))
        assertFalse(output.contains("ignored_label_widget"))
    }

    private fun writeProject(image: String) {
        Files.writeString(projectDir.resolve("settings.gradle.kts"), "rootProject.name = \"functional-test\"\n")
        Files.writeString(
            projectDir.resolve("build.gradle.kts"),
            """
            plugins {
                id("io.github.yrashid.actual-schema")
            }

            actualSchema {
                changelogFile.set(layout.projectDirectory.file("src/main/resources/db/changelog.yml"))
                resourceBaseDir.set(layout.projectDirectory.dir("src/main/resources"))
                postgresImage.set("$image")
                schemas.set(emptyList())
                excludeTables.set(setOf("public.audit_log"))
                liquibaseContexts.set(listOf("actual-schema"))
                liquibaseParameters.put("tableName", "widget")
            }
            """.trimIndent()
        )
        val resources = projectDir.resolve("src/main/resources/db")
        Files.createDirectories(resources.resolve("changes"))
        Files.writeString(resources.resolve("changelog.yml"), masterChangelog(includeExtra = false))
        Files.writeString(
            resources.resolve("changes/001-widget.yml"),
            """
            databaseChangeLog:
              - changeSet:
                  id: create-widget
                  author: test
                  context: actual-schema
                  changes:
                    - createTable:
                        tableName: ${'$'}{tableName}
                        columns:
                          - column:
                              name: id
                              type: BIGINT
                              constraints:
                                primaryKey: true
                                nullable: false
              - changeSet:
                  id: create-audit-log
                  author: test
                  context: actual-schema
                  changes:
                    - createTable:
                        tableName: audit_log
                        columns:
                          - column:
                              name: id
                              type: BIGINT
              - changeSet:
                  id: ignored-context
                  author: test
                  context: another-context
                  changes:
                    - createTable:
                        tableName: ignored_widget
                        columns:
                          - column:
                              name: id
                              type: BIGINT
            """.trimIndent()
        )
    }

    private fun masterChangelog(includeExtra: Boolean): String = buildString {
        appendLine("databaseChangeLog:")
        appendLine("  - include:")
        appendLine("      file: db/changes/001-widget.yml")
        if (includeExtra) {
            appendLine("  - changeSet:")
            appendLine("      id: include-extra-sql")
            appendLine("      author: test")
            appendLine("      changes:")
            appendLine("        - sqlFile:")
            appendLine("            path: db/changes/002-extra.sql")
        }
    }

    private fun assumeDockerAvailable() {
        val dockerAvailable = runCatching { DockerClientFactory.instance().isDockerAvailable }.getOrDefault(false)
        assumeTrue(dockerAvailable, "Docker is required for the functional test")
    }

    private fun testPostgresImage(): String = System.getenv("ACTUAL_SCHEMA_TEST_POSTGRES_IMAGE") ?: "postgres:16"

    private fun runner(vararg arguments: String): GradleRunner = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withArguments(*arguments, "--stacktrace")
        .withPluginClasspath()
        .forwardOutput()
}
