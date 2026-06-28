package io.github.yrashid.actualschema

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ActualSchemaPluginTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `extension has documented defaults`() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply("io.github.yrashid.actual-schema")
        val extension = project.extensions.getByType(ActualSchemaExtension::class.java)

        assertEquals(project.layout.projectDirectory.dir("src/main/resources").asFile, extension.resourceBaseDir.get().asFile)
        assertEquals("postgres:16", extension.postgresImage.get())
        assertEquals("postgres", extension.postgresImageCompatibleSubstituteFor.get())
        assertEquals(60, extension.postgresStartupTimeoutSeconds.get())
        assertEquals("actual_schema", extension.databaseName.get())
        assertEquals("actual_schema", extension.username.get())
        assertEquals("actual_schema", extension.password.get())
        assertEquals(listOf("public"), extension.schemas.get())
        assertEquals(emptySet<String>(), extension.excludeTables.get())
        assertEquals(emptyList<String>(), extension.liquibaseContexts.get())
        assertEquals(emptyList<String>(), extension.liquibaseLabels.get())
        assertEquals(emptyMap<String, String>(), extension.liquibaseParameters.get())
        assertFalse(extension.liquibaseDefaultSchema.isPresent)
        assertFalse(extension.liquibaseSchema.isPresent)
        assertEquals("databasechangelog", extension.liquibaseChangeLogTable.get())
        assertEquals("databasechangeloglock", extension.liquibaseChangeLogLockTable.get())
        assertFalse(extension.includeLiquibaseTables.get())
        assertTrue(extension.normalizeOutput.get())
        assertEquals(
            project.layout.buildDirectory.file("generated/actual-schema/schema.sql").get().asFile,
            extension.outputFile.get().asFile
        )
        assertNotNull(project.configurations.findByName("actualSchemaLiquibaseRuntime"))
        assertNotNull(project.tasks.findByName("generateActualSchema"))
        assertNotNull(project.tasks.findByName("checkActualSchema"))
    }

    @Test
    fun `first difference description reports the changed line`() {
        val expected = tempDir.resolve("expected.sql")
        val generated = tempDir.resolve("generated.sql")
        Files.writeString(
            expected,
            """
            CREATE TABLE public.widget (
                id bigint
            );
            """.trimIndent()
        )
        Files.writeString(
            generated,
            """
            CREATE TABLE public.widget (
                id integer
            );
            """.trimIndent()
        )

        val description = firstDifferenceDescription(expected, generated)

        assertTrue(description.contains("First differing line: 2"))
        assertTrue(description.contains("id bigint"))
        assertTrue(description.contains("id integer"))
    }

    @Test
    fun `shell quote handles paths with spaces and apostrophes`() {
        assertEquals("'/tmp/schema dir/owner'\"'\"'s schema.sql'", shellQuote("/tmp/schema dir/owner's schema.sql"))
    }
}
