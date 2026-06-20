# Actual Schema Gradle Plugin

[![CI](https://github.com/YRashid/actual-schema-gradle-plugin/actions/workflows/ci.yml/badge.svg)](https://github.com/YRashid/actual-schema-gradle-plugin/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

Generates the actual PostgreSQL schema DDL that exists after all Liquibase migrations have been
applied. The plugin starts a temporary PostgreSQL container with Testcontainers, runs Liquibase
against the real database, executes `pg_dump --schema-only --no-owner --no-privileges` inside the
same container, writes a stable SQL snapshot, and always removes the temporary container.

This is intentionally different from `liquibase update-sql`: update SQL describes migration steps,
while this plugin captures the final state of a real PostgreSQL database after those steps.

## Requirements

- Gradle running on Java 17 or newer.
- A Docker-compatible daemon available to Testcontainers.
- Access to the configured PostgreSQL container image.
- A Liquibase changelog and all resources it includes.

No locally installed PostgreSQL client or Liquibase CLI is required.

## Installation

After the plugin is available on the Gradle Plugin Portal:

```kotlin
plugins {
    id("io.github.yrashid.actual-schema") version "0.1.0"
}
```

For local development of this repository, use `./gradlew publishToMavenLocal` or TestKit's plugin
classpath instead of publishing credentials.

## Minimal Kotlin DSL configuration

```kotlin
actualSchema {
    changelogFile.set(
        layout.projectDirectory.file("src/main/resources/db/changelog/db.changelog-master.yaml")
    )
}
```

The default resource root is `src/main/resources`. The generated file is written to
`build/generated/actual-schema/schema.sql`.

## Full Kotlin DSL configuration

```kotlin
actualSchema {
    changelogFile.set(
        layout.projectDirectory.file("src/main/resources/db/changelog/db.changelog-master.yaml")
    )
    resourceBaseDir.set(layout.projectDirectory.dir("src/main/resources"))
    outputFile.set(layout.projectDirectory.file("database/schema.sql"))

    postgresImage.set("postgres:16")
    postgresImageCompatibleSubstituteFor.set("postgres")
    databaseName.set("app")
    username.set("app")
    password.set("app")

    schemas.set(listOf("public"))
    excludeTables.set(setOf("public.audit_log"))

    liquibaseContexts.set(listOf("production"))
    liquibaseLabels.set(listOf("core"))
    liquibaseParameters.putAll(
        mapOf(
            "schemaName" to "public",
            "tablePrefix" to "app_"
        )
    )
    liquibaseDefaultSchema.set("public")
    liquibaseSchema.set("public")
    liquibaseChangeLogTable.set("databasechangelog")
    liquibaseChangeLogLockTable.set("databasechangeloglock")

    includeLiquibaseTables.set(false)
    normalizeOutput.set(true)
}
```

Defaults:

| Property | Default |
| --- | --- |
| `resourceBaseDir` | `src/main/resources` |
| `outputFile` | `build/generated/actual-schema/schema.sql` |
| `postgresImage` | `postgres:16` |
| database, username, password | `actual_schema` |
| `schemas` | `public` |
| Liquibase changelog tables | `databasechangelog`, `databasechangeloglock` |
| `includeLiquibaseTables` | `false` |
| `normalizeOutput` | `true` |

An empty `schemas` list means “dump all schemas”. In that mode Liquibase metadata tables are
excluded with cross-schema patterns unless `includeLiquibaseTables` is enabled.

## Groovy DSL

```groovy
plugins {
    id 'io.github.yrashid.actual-schema' version '0.1.0'
}

actualSchema {
    changelogFile.set(
        layout.projectDirectory.file('src/main/resources/db/changelog/db.changelog-master.yaml')
    )
    resourceBaseDir.set(layout.projectDirectory.dir('src/main/resources'))
    outputFile.set(layout.projectDirectory.file('database/schema.sql'))
    schemas.set(['public'])
    normalizeOutput.set(true)
}
```

## Tasks

### `generateActualSchema`

Creates the configured snapshot by applying the changelog to an empty temporary PostgreSQL database.

```bash
./gradlew generateActualSchema
```

### `checkActualSchema`

Generates a separate candidate without overwriting `outputFile`, compares both files byte-for-byte,
and fails with an update command when the committed snapshot is missing or stale.

```bash
./gradlew checkActualSchema
```

For useful CI verification, configure `outputFile` to point to a version-controlled file such as
`database/schema.sql`.

## CI example

```yaml
- uses: actions/setup-java@v4
  with:
    distribution: temurin
    java-version: "17"
- uses: gradle/actions/setup-gradle@v4
- run: ./gradlew checkActualSchema --configuration-cache
```

The CI runner must expose Docker. GitHub-hosted Ubuntu runners provide a compatible Docker daemon.

## Liquibase extensions and custom changes

Add extension JARs, parsers, serializers, or project classes to the isolated runtime configuration:

```kotlin
dependencies {
    add("actualSchemaLiquibaseRuntime", "org.liquibase.ext:liquibase-hibernate6:VERSION")
    add("actualSchemaLiquibaseRuntime", project(":database-custom-changes"))
}
```

`actualSchemaLiquibaseRuntime` is resolvable and non-consumable. The generator runs through Gradle's
Worker API with classloader isolation. Liquibase resolves resources from `resourceBaseDir`, the root
changelog directory, and this isolated runtime classloader.

## PostGIS and custom PostgreSQL images

Images compatible with the official PostgreSQL image can be declared as substitutes:

```kotlin
actualSchema {
    postgresImage.set("postgis/postgis:16-3.4")
    postgresImageCompatibleSubstituteFor.set("postgres")
}
```

The compatible substitute defaults to `postgres`, so the second line is normally unnecessary.

## Stable output

Output normalization is enabled by default. It removes PostgreSQL/`pg_dump` version headers,
random `\\restrict` and `\\unrestrict` keys, and normalizes the final newline. This makes generated
snapshots suitable for code review and CI comparison. Set `normalizeOutput` to `false` to retain the
raw dump.

## Incremental build and Configuration Cache

The task declares the root changelog, complete resource directory, isolated runtime classpath,
configuration values, and output file through Gradle's lazy property APIs. Included resource changes
therefore invalidate local up-to-date state. Configuration Cache is covered by functional tests.

Remote/local build-cache storage for schema generation is deliberately disabled. Mutable Docker
image tags can change the generated DDL without changing Gradle inputs. Pin the PostgreSQL image by
digest if strict image reproducibility is required; local up-to-date checks still work.

## Troubleshooting

- **Changelog not found:** use `layout.projectDirectory.file(...)` and point `resourceBaseDir` to the
  resources root.
- **Docker unavailable:** start Docker and verify that the current user/CI runner can access it.
- **Image pull failed:** authenticate Docker/Testcontainers with the target registry and verify the
  configured image name.
- **Liquibase extension is not discovered:** add it to `actualSchemaLiquibaseRuntime`, not only to the
  application's `implementation` configuration.
- **Liquibase migration failed:** inspect the nested exception and verify contexts, labels,
  parameters, and schema settings.
- **`pg_dump` failed:** the task reports the process exit code and stderr. Check schema and exclusion
  patterns.
- **Expected snapshot is missing:** run `./gradlew generateActualSchema` before
  `checkActualSchema`.

## Limitations

- PostgreSQL is the only implemented database strategy.
- Docker/Testcontainers is required.
- Build-cache storage is disabled because Docker image tags can be mutable.
- The generated schema reflects the configured PostgreSQL image and Liquibase runtime versions.

## Development

Docker is required because `check` includes TestKit functional tests with a real PostgreSQL
container.

```bash
./gradlew clean check --configuration-cache
./gradlew publishPlugins --validate-only
```

Publishing uses the Plugin Portal `publishPlugins` task. Configure these repository secrets for the
tag workflow; never commit them. Plugin Publish 2.1.1 requires the credentials for both publication
and the server-side `--validate-only` request:

- `GRADLE_PUBLISH_KEY`
- `GRADLE_PUBLISH_SECRET`

Tags matching `v*.*.*` trigger `.github/workflows/publish-gradle-plugin.yml`.

## License

Licensed under the [Apache License 2.0](LICENSE).
