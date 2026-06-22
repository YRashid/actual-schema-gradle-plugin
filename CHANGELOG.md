# Changelog

All notable changes to this project are documented in this file.

## [Unreleased]

### Changed

- Upgrade Liquibase from 4.24.0 to 4.33.0.
- Upgrade Testcontainers from 1.20.1 to 2.0.5 and migrate to its new PostgreSQL module API.
- Enforce safe transitive baselines for Apache Commons Compress and Apache Commons Lang.
- Verify the Gradle distribution checksum.
- Publish real Kotlin API documentation in the Javadoc artifact with Dokka.
- Add Gradle 8.5 compatibility CI, dependency submission, and Dependabot updates.
- Add Russian documentation and GitHub community health files.

## [0.1.0] - 2026-06-20

Initial public release.

- Generate actual PostgreSQL schema after applying Liquibase migrations.
- Add `generateActualSchema`.
- Add `checkActualSchema`.
- Add Testcontainers-based PostgreSQL execution.
- Add Liquibase runtime classpath isolation.
- Add normalized `pg_dump` output.

[Unreleased]: https://github.com/YRashid/actual-schema-gradle-plugin/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/YRashid/actual-schema-gradle-plugin/releases/tag/v0.1.0
