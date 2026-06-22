# Actual Schema Gradle Plugin

[English](README.md) | [Русский](README.ru.md)

[![CI](https://github.com/YRashid/actual-schema-gradle-plugin/actions/workflows/ci.yml/badge.svg)](https://github.com/YRashid/actual-schema-gradle-plugin/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

`io.github.yrashid.actual-schema` generates the final PostgreSQL DDL produced by your Liquibase
migrations.

You do not need an existing database, a PostgreSQL installation, `pg_dump`, or the Liquibase CLI.
The plugin starts a disposable PostgreSQL container, applies the changelog, exports the resulting
schema, and removes the container when the task finishes.

This is useful when you want to:

- keep a reviewable `schema.sql` snapshot in Git;
- detect migrations that changed the final schema without updating that snapshot;
- provide the current DDL to code generators, documentation tools, or reviewers;
- inspect the final database state instead of the individual migration statements.

Unlike `liquibase update-sql`, which describes migration steps, this plugin captures the state of a
real PostgreSQL database after those steps have been executed.

## Requirements

- Gradle 8.5 or newer.
- Gradle running on Java 17 or newer.
- A running Docker-compatible daemon accessible to the current user.
- A Liquibase changelog in your project.

The PostgreSQL image is downloaded automatically by Testcontainers when it is not already available
locally. No PostgreSQL server or client tools need to be installed on the host.

## Quick start

### 1. Apply the plugin

Kotlin DSL:

```kotlin
plugins {
    id("io.github.yrashid.actual-schema") version "0.1.1"
}
```

### 2. Point the plugin to your changelog

```kotlin
actualSchema {
    changelogFile.set(
        layout.projectDirectory.file("src/main/resources/db/changelog/db.changelog-master.yaml")
    )
}
```

The default resource root is `src/main/resources`. The generated file is written to
`build/generated/actual-schema/schema.sql` unless `outputFile` is configured.

### 3. Generate the schema

```bash
./gradlew generateActualSchema
```

The plugin will pull `postgres:16` if necessary, start a temporary container, apply the changelog,
run `pg_dump --schema-only`, write the SQL file, and stop the container.

## Typical Spring Boot, Liquibase, and PostgreSQL setup

A common project layout looks like this:

```text
src/main/resources/
├── application.yaml
└── db/changelog/
    ├── db.changelog-master.yaml
    └── changes/
        ├── 001-create-users.yaml
        └── 002-add-user-status.yaml
database/
└── schema.sql
```

Configure Spring Boot and the plugin to use the same root changelog:

```kotlin
plugins {
    id("org.springframework.boot") version "<your Spring Boot version>"
    id("io.spring.dependency-management") version "<compatible version>"
    id("io.github.yrashid.actual-schema") version "0.1.1"
}

dependencies {
    implementation("org.liquibase:liquibase-core")
    runtimeOnly("org.postgresql:postgresql")
}

actualSchema {
    changelogFile.set(
        layout.projectDirectory.file("src/main/resources/db/changelog/db.changelog-master.yaml")
    )
    resourceBaseDir.set(layout.projectDirectory.dir("src/main/resources"))

    // Store the snapshot in Git so checkActualSchema can verify it in CI.
    outputFile.set(layout.projectDirectory.file("database/schema.sql"))

    postgresImage.set("postgres:16")
    schemas.set(listOf("public"))
}
```

```yaml
# src/main/resources/application.yaml
spring:
  liquibase:
    change-log: classpath:/db/changelog/db.changelog-master.yaml
```

Create and commit the initial snapshot:

```bash
./gradlew generateActualSchema
git add database/schema.sql
```

The application and the plugin configure Liquibase independently. If your application uses
contexts, labels, or changelog parameters, configure the same values in `actualSchema` as shown
below. The plugin currently runs migrations with Liquibase 4.33.0.

## Tasks

### `generateActualSchema`

Applies all selected migrations to a new temporary database and writes the resulting DDL to
`outputFile`.

```bash
./gradlew generateActualSchema
```

Use this command after adding or changing migrations. Review and commit the updated snapshot if it
is stored in the repository.

### `checkActualSchema`

Generates a separate candidate without overwriting `outputFile`, compares the files byte-for-byte,
and fails when the committed snapshot is missing or stale.

```bash
./gradlew checkActualSchema
```

For this task to be useful, configure `outputFile` outside `build/`, for example
`database/schema.sql`, and commit that file.

## Configuration

### Output file, schemas, and PostgreSQL image

```kotlin
actualSchema {
    outputFile.set(layout.projectDirectory.file("database/schema.sql"))

    postgresImage.set("postgres:16")
    schemas.set(listOf("public", "reporting"))
    excludeTables.set(setOf("public.audit_log"))

    includeLiquibaseTables.set(false)
    normalizeOutput.set(true)
}
```

- An empty `schemas` list means “dump all schemas”.
- `excludeTables` values are passed to `pg_dump --exclude-table`.
- Liquibase metadata tables are omitted by default.
- Output normalization removes PostgreSQL version headers, random `\\restrict`/`\\unrestrict`
  keys, and trailing whitespace differences.

### Liquibase contexts, labels, and parameters

```kotlin
actualSchema {
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
}
```

Only configure values that your changelog needs. Empty context and label lists apply migrations
using Liquibase's default selection behavior.

### Configuration reference

| Property | Default | Purpose |
| --- | --- | --- |
| `changelogFile` | required | Root Liquibase changelog |
| `resourceBaseDir` | `src/main/resources` | Base directory for included changelog resources |
| `outputFile` | `build/generated/actual-schema/schema.sql` | Generated schema snapshot |
| `postgresImage` | `postgres:16` | PostgreSQL-compatible container image |
| `postgresImageCompatibleSubstituteFor` | `postgres` | Canonical image name used by Testcontainers |
| `databaseName` | `actual_schema` | Name of the temporary database |
| `username` / `password` | `actual_schema` | Credentials used only for the temporary database |
| `schemas` | `public` | Schemas passed to `pg_dump`; empty means all schemas |
| `excludeTables` | empty | Table patterns excluded from the dump |
| `liquibaseContexts` | empty | Liquibase contexts to enable |
| `liquibaseLabels` | empty | Liquibase labels to enable |
| `liquibaseParameters` | empty | Changelog parameters |
| `liquibaseDefaultSchema` | unset | Default schema used by Liquibase |
| `liquibaseSchema` | unset | Schema containing Liquibase metadata tables |
| `includeLiquibaseTables` | `false` | Include Liquibase metadata tables in the snapshot |
| `normalizeOutput` | `true` | Remove volatile `pg_dump` output |

## PostgreSQL and PostGIS images

Testcontainers automatically pulls the configured image through Docker. To use another PostgreSQL
version, set `postgresImage`:

```kotlin
actualSchema {
    postgresImage.set("postgres:17")
}
```

PostgreSQL-compatible images such as PostGIS can be declared as substitutes for the official image:

```kotlin
actualSchema {
    postgresImage.set("postgis/postgis:16-3.4")
    postgresImageCompatibleSubstituteFor.set("postgres")
}
```

For reproducible snapshots, pin the image by digest. Registry authentication is handled by Docker
and Testcontainers using the host's normal registry configuration.

## Liquibase extensions and custom changes

Add extension JARs, parsers, serializers, or project classes to the plugin's isolated Liquibase
runtime:

```kotlin
dependencies {
    add("actualSchemaLiquibaseRuntime", "org.liquibase.ext:liquibase-hibernate6:VERSION")
    add("actualSchemaLiquibaseRuntime", project(":database-custom-changes"))
}
```

Extensions must be compatible with the Liquibase version used by the plugin.

## Groovy DSL

```groovy
plugins {
    id 'io.github.yrashid.actual-schema' version '0.1.1'
}

actualSchema {
    changelogFile.set(
        layout.projectDirectory.file('src/main/resources/db/changelog/db.changelog-master.yaml')
    )
    resourceBaseDir.set(layout.projectDirectory.dir('src/main/resources'))
    outputFile.set(layout.projectDirectory.file('database/schema.sql'))

    postgresImage.set('postgres:16')
    schemas.set(['public'])
    excludeTables.set(['public.audit_log'] as Set)

    liquibaseContexts.set(['production'])
    liquibaseLabels.set(['core'])
    liquibaseParameters.put('schemaName', 'public')

    includeLiquibaseTables.set(false)
    normalizeOutput.set(true)
}
```

## CI usage

Generate `database/schema.sql` locally and commit it. CI then needs to run only
`checkActualSchema`:

```yaml
name: Check database schema

on:
  pull_request:
  push:
    branches: [main]

jobs:
  schema:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6
      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: "17"
      - uses: gradle/actions/setup-gradle@v6
      - run: ./gradlew checkActualSchema --configuration-cache
```

GitHub-hosted Ubuntu runners already provide Docker. A self-hosted runner must expose a compatible
Docker daemon to the build user.

## Troubleshooting

### Docker is unavailable

Start Docker and verify that it is reachable from the same user that runs Gradle:

```bash
docker info
```

For Colima, Podman, remote Docker, or other compatible runtimes, configure Testcontainers using the
runtime's normal environment variables and socket settings.

### Changelog not found

Use a project-relative Gradle file provider and make sure `resourceBaseDir` contains the changelog
resources:

```kotlin
actualSchema {
    changelogFile.set(
        layout.projectDirectory.file("src/main/resources/db/changelog/db.changelog-master.yaml")
    )
    resourceBaseDir.set(layout.projectDirectory.dir("src/main/resources"))
}
```

Also verify the file name and extension: `.yaml`, `.yml`, `.xml`, `.json`, or formatted SQL.

### Snapshot is out of date

Regenerate it, inspect the diff, and commit the expected change:

```bash
./gradlew generateActualSchema
git diff -- database/schema.sql
```

### Testcontainers cannot pull the PostgreSQL image

Try pulling the configured image directly:

```bash
docker pull postgres:16
```

Check network access, registry credentials, proxy configuration, the image name, and the requested
tag. Private registries must be authenticated through Docker before running Gradle.

### Liquibase extension is not discovered

Add it to `actualSchemaLiquibaseRuntime`, not only to the application's `implementation`
configuration.

### Plugin cannot be resolved

Confirm that the requested version is visible on the
[Gradle Plugin Portal](https://plugins.gradle.org/plugin/io.github.yrashid.actual-schema). The first
public version remains unavailable to consumer builds until the Portal's initial approval is
complete.

## Security and trust model

Liquibase changelogs, SQL files, and custom change classes are executable build inputs. Run the
plugin only on trusted repository contents, particularly in CI jobs with Docker or secret access.
The database itself is temporary and isolated, but access to the Docker daemon is privileged.

Report suspected vulnerabilities privately as described in [SECURITY.md](SECURITY.md).

## Current limitations

- PostgreSQL is the only supported database.
- Docker or a Testcontainers-compatible runtime is required.
- Generated DDL can change when the PostgreSQL image or Liquibase version changes.
- Build-cache storage is disabled because Docker image tags may be mutable.

## Project information

User-visible changes are documented in [CHANGELOG.md](CHANGELOG.md). If you want to contribute to
the plugin itself, see [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Licensed under the [Apache License 2.0](LICENSE).
