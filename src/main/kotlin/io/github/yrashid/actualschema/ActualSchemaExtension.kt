package io.github.yrashid.actualschema

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.file.RegularFileProperty

abstract class ActualSchemaExtension {
    /** Root Liquibase changelog to apply. */
    abstract val changelogFile: RegularFileProperty

    /** Root directory used to resolve changelog resources. */
    abstract val resourceBaseDir: DirectoryProperty

    /** Destination of the generated schema snapshot. */
    abstract val outputFile: RegularFileProperty

    /** PostgreSQL-compatible Docker image used for generation. */
    abstract val postgresImage: Property<String>

    /** Temporary database name. */
    abstract val databaseName: Property<String>

    /** Temporary database user. */
    abstract val username: Property<String>

    /** Temporary database password; it does not affect generated DDL. */
    abstract val password: Property<String>

    /** PostgreSQL schemas passed to `pg_dump`; an empty list selects all schemas. */
    abstract val schemas: ListProperty<String>

    /** Table patterns passed to `pg_dump --exclude-table`. */
    abstract val excludeTables: SetProperty<String>

    /** Liquibase contexts enabled while applying the changelog. */
    abstract val liquibaseContexts: ListProperty<String>

    /** Liquibase labels enabled while applying the changelog. */
    abstract val liquibaseLabels: ListProperty<String>

    /** Parameters made available to the Liquibase changelog. */
    abstract val liquibaseParameters: MapProperty<String, String>

    /** Optional default schema configured on the Liquibase database. */
    abstract val liquibaseDefaultSchema: Property<String>

    /** Optional schema containing Liquibase metadata tables. */
    abstract val liquibaseSchema: Property<String>

    /** Name of the Liquibase changelog metadata table. */
    abstract val liquibaseChangeLogTable: Property<String>

    /** Name of the Liquibase changelog lock table. */
    abstract val liquibaseChangeLogLockTable: Property<String>

    /** Whether Liquibase metadata tables should remain in the generated dump. */
    abstract val includeLiquibaseTables: Property<Boolean>

    /** Whether volatile `pg_dump` lines should be removed from the output. */
    abstract val normalizeOutput: Property<Boolean>

    /** Canonical image name used by Testcontainers for compatible substitutes such as PostGIS. */
    abstract val postgresImageCompatibleSubstituteFor: Property<String>
}
