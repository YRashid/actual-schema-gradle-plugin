# Security policy

## Supported versions

Security fixes are provided for the latest released `0.1.x` version while the project is in its
initial development phase.

## Reporting a vulnerability

Please use GitHub's private vulnerability reporting form:

https://github.com/YRashid/actual-schema-gradle-plugin/security/advisories/new

Do not open a public issue with exploit details. Include the affected plugin version, impact,
reproduction steps, and any suggested mitigation. You should receive an acknowledgement within
seven days. Public disclosure will be coordinated after a fix or mitigation is available.

## Security model

This plugin starts containers through the configured Docker daemon and executes trusted Liquibase
changelogs, SQL, and custom change classes. These inputs can execute code or SQL during the build.
Running the plugin on untrusted repository contents is outside the supported threat model.
