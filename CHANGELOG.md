# Changelog

## 0.1.0

Initial public release.

- Generate actual PostgreSQL schema after applying Liquibase migrations.
- Add `generateActualSchema`.
- Add `checkActualSchema`.
- Add Testcontainers-based PostgreSQL execution.
- Add Liquibase runtime classpath isolation.
- Add normalized `pg_dump` output.
