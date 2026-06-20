package io.github.yrashid.actualschema

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActualSchemaPluginTest {
    @Test
    fun `extension has documented defaults`() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply("io.github.yrashid.actual-schema")
        val extension = project.extensions.getByType(ActualSchemaExtension::class.java)

        assertEquals(project.layout.projectDirectory.dir("src/main/resources").asFile, extension.resourceBaseDir.get().asFile)
        assertEquals("postgres:16", extension.postgresImage.get())
        assertEquals("postgres", extension.postgresImageCompatibleSubstituteFor.get())
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
    fun `normalization removes volatile pg dump lines`() {
        val dump = """
            -- PostgreSQL database dump
            -- Dumped from database version 18.1
            -- Dumped by pg_dump version 18.1
            \restrict random-key
            CREATE TABLE public.widget (id bigint);
            \unrestrict random-key

        """.trimIndent()

        assertEquals(
            "-- PostgreSQL database dump\nCREATE TABLE public.widget (id bigint);\n",
            normalizePgDump(dump)
        )
    }
}
