package io.github.yrashid.actualschema

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import java.nio.charset.StandardCharsets
import java.nio.file.Files

interface GenerateSchemaWorkParameters : WorkParameters {
    val changelogFile: RegularFileProperty
    val resourceBaseDir: DirectoryProperty
    val outputFile: RegularFileProperty
    val postgresImage: Property<String>
    val postgresImageCompatibleSubstituteFor: Property<String>
    val databaseName: Property<String>
    val username: Property<String>
    val password: Property<String>
    val schemas: ListProperty<String>
    val excludedTables: SetProperty<String>
    val liquibaseContexts: ListProperty<String>
    val liquibaseLabels: ListProperty<String>
    val liquibaseParameters: MapProperty<String, String>
    val liquibaseDefaultSchema: Property<String>
    val liquibaseSchema: Property<String>
    val liquibaseChangeLogTable: Property<String>
    val liquibaseChangeLogLockTable: Property<String>
    val normalizeOutput: Property<Boolean>
}

abstract class GenerateSchemaWorkAction : WorkAction<GenerateSchemaWorkParameters> {
    override fun execute() {
        val config = PostgreSqlGenerationConfig(
            changelog = parameters.changelogFile.get().asFile,
            resourceBaseDir = parameters.resourceBaseDir.get().asFile,
            image = parameters.postgresImage.get(),
            imageCompatibleSubstituteFor = parameters.postgresImageCompatibleSubstituteFor.get(),
            databaseName = parameters.databaseName.get(),
            username = parameters.username.get(),
            password = parameters.password.get(),
            schemas = parameters.schemas.get(),
            excludedTables = parameters.excludedTables.get(),
            liquibaseContexts = parameters.liquibaseContexts.get(),
            liquibaseLabels = parameters.liquibaseLabels.get(),
            liquibaseParameters = parameters.liquibaseParameters.get(),
            liquibaseDefaultSchema = parameters.liquibaseDefaultSchema.orNull,
            liquibaseSchema = parameters.liquibaseSchema.orNull,
            liquibaseChangeLogTable = parameters.liquibaseChangeLogTable.get(),
            liquibaseChangeLogLockTable = parameters.liquibaseChangeLogLockTable.get(),
            normalizeOutput = parameters.normalizeOutput.get()
        )
        val dump = PostgreSqlSchemaGenerator(LOGGER).generate(config)
        Files.writeString(parameters.outputFile.get().asFile.toPath(), dump, StandardCharsets.UTF_8)
    }

    private companion object {
        val LOGGER = Logging.getLogger(GenerateSchemaWorkAction::class.java)
    }
}
