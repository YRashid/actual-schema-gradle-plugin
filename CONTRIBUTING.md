# Contributing

Thank you for helping improve Actual Schema Gradle Plugin.

## Development setup

You need JDK 17 or newer and a Docker-compatible daemon. Clone the repository, then run:

```bash
./gradlew clean check --configuration-cache
```

`check` includes unit tests, plugin validation, and TestKit functional tests against a real
PostgreSQL container. To run only the fast checks:

```bash
./gradlew test validatePlugins
```

## Proposing a change

1. Open an issue first for substantial behavior or public API changes.
2. Keep each pull request focused on one problem.
3. Add or update tests for observable behavior.
4. Update `README.md`, `README.ru.md`, and `CHANGELOG.md` when user-facing behavior changes.
5. Run the full check before requesting review.

The project supports Gradle 8.5 and newer on Java 17 and newer. Avoid Gradle APIs introduced after
8.5 unless the minimum supported version is intentionally changed in the same pull request.

## Pull requests

Describe the problem, the chosen approach, and how you verified it. Include a minimal reproducer for
bug fixes when practical. Do not include publishing credentials, Docker registry credentials,
database passwords, generated build output, or unrelated formatting changes.

By participating, you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).
