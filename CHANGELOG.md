# Changelog

All notable changes to this project are documented in this file.

## [Unreleased]

## [0.1.2] - 2026-06-28

### Changed

- Upgrade Liquibase from 4.24.0 to 5.0.3.
- Upgrade Testcontainers from 1.20.1 to 2.0.5 and migrate to its new PostgreSQL module API.
- Enforce safe transitive baselines for Apache Commons Compress, Apache Commons Lang, Commons IO,
  SnakeYAML, and OpenCSV.
- Upgrade JUnit Jupiter to the latest 5.x baseline.
- Upgrade the Gradle wrapper to 9.6.1 and verify the distribution checksum.
- Publish real Kotlin API documentation in the Javadoc artifact with Dokka.
- Report the first differing line and a ready-to-run `diff -u` command when `checkActualSchema`
  finds a stale snapshot.
- Validate blank or invalid `actualSchema` configuration values before starting Docker.
- Group normalized index-like statements by target table for easier schema review.
- Add `postgresStartupTimeoutSeconds` for slower Docker environments.
- Add Gradle 8.5 compatibility CI, dependency submission, and Dependabot updates.
- Add Russian documentation and GitHub community health files.
- Cover Groovy DSL, formatted SQL changelogs, Liquibase labels, and table exclusions in functional
  tests.

## [0.1.0] - 2026-06-20

Initial public release.

- Generate actual PostgreSQL schema after applying Liquibase migrations.
- Add `generateActualSchema`.
- Add `checkActualSchema`.
- Add Testcontainers-based PostgreSQL execution.
- Add Liquibase runtime classpath isolation.
- Add normalized `pg_dump` output.

[Unreleased]: https://github.com/YRashid/actual-schema-gradle-plugin/compare/v0.1.2...HEAD
[0.1.2]: https://github.com/YRashid/actual-schema-gradle-plugin/compare/v0.1.1...v0.1.2
[0.1.0]: https://github.com/YRashid/actual-schema-gradle-plugin/releases/tag/v0.1.0
