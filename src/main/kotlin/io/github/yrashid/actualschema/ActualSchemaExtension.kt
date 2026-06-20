package io.github.yrashid.actualschema

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.file.RegularFileProperty

abstract class ActualSchemaExtension {
    abstract val changelogFile: RegularFileProperty
    abstract val resourceBaseDir: DirectoryProperty
    abstract val outputFile: RegularFileProperty

    abstract val postgresImage: Property<String>
    abstract val databaseName: Property<String>
    abstract val username: Property<String>
    abstract val password: Property<String>

    abstract val schemas: ListProperty<String>
    abstract val excludeTables: SetProperty<String>

    abstract val liquibaseContexts: ListProperty<String>
    abstract val liquibaseLabels: ListProperty<String>
    abstract val liquibaseParameters: MapProperty<String, String>
    abstract val liquibaseDefaultSchema: Property<String>
    abstract val liquibaseSchema: Property<String>
    abstract val liquibaseChangeLogTable: Property<String>
    abstract val liquibaseChangeLogLockTable: Property<String>

    abstract val includeLiquibaseTables: Property<Boolean>
    abstract val normalizeOutput: Property<Boolean>
    abstract val postgresImageCompatibleSubstituteFor: Property<String>
}
