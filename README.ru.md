# Actual Schema Gradle Plugin

[English](README.md) | [Русский](README.ru.md)

[![CI](https://github.com/YRashid/actual-schema-gradle-plugin/actions/workflows/ci.yml/badge.svg)](https://github.com/YRashid/actual-schema-gradle-plugin/actions/workflows/ci.yml)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/io.github.yrashid.actual-schema)](https://plugins.gradle.org/plugin/io.github.yrashid.actual-schema)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

`io.github.yrashid.actual-schema` генерирует итоговый DDL PostgreSQL, который получается после
применения миграций Liquibase.

Для работы не нужны существующая база данных, установленная PostgreSQL, `pg_dump` или Liquibase
CLI. Плагин сам запускает временный контейнер PostgreSQL, применяет changelog, выгружает полученную
схему и удаляет контейнер после завершения задачи.

Плагин полезен, когда требуется:

- хранить в Git понятный для code review снимок `schema.sql`;
- обнаруживать миграции, изменившие итоговую схему без обновления снимка;
- передавать актуальный DDL генераторам кода, инструментам документирования или ревьюерам;
- видеть конечное состояние базы, а не отдельные SQL-команды миграций.

В отличие от `liquibase update-sql`, который описывает шаги миграции, плагин фиксирует состояние
реальной базы PostgreSQL после выполнения этих шагов.

## Требования

- Gradle 8.5 или новее.
- Gradle должен запускаться на Java 17 или новее.
- Запущенный Docker Desktop, Docker Engine или другая совместимая с Testcontainers среда.
- Changelog Liquibase в проекте.

Если нужного Docker-образа PostgreSQL ещё нет локально, Testcontainers скачает его автоматически.
Устанавливать PostgreSQL server или клиентские утилиты на компьютер не требуется.

## Быстрый старт

### 1. Подключите плагин

Kotlin DSL:

```kotlin
plugins {
    id("io.github.yrashid.actual-schema") version "0.1.2"
}
```

Опубликованные версии перечислены на
[Gradle Plugin Portal](https://plugins.gradle.org/plugin/io.github.yrashid.actual-schema).

### 2. Укажите changelog

```kotlin
actualSchema {
    changelogFile.set(
        layout.projectDirectory.file("src/main/resources/db/changelog/db.changelog-master.yaml")
    )
}
```

По умолчанию корнем ресурсов служит `src/main/resources`, а результат записывается в
`build/generated/actual-schema/schema.sql`, если не задан `outputFile`.

### 3. Сгенерируйте схему

```bash
./gradlew generateActualSchema
```

Плагин при необходимости скачает `postgres:16`, запустит временный контейнер, применит changelog,
выполнит `pg_dump --schema-only`, запишет SQL-файл и остановит контейнер.

## Типовой проект на Spring Boot, Liquibase и PostgreSQL

Обычная структура проекта может выглядеть так:

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

Настройте Spring Boot и плагин на один корневой changelog:

```kotlin
plugins {
    id("org.springframework.boot") version "<ваша версия Spring Boot>"
    id("io.spring.dependency-management") version "<совместимая версия>"
    id("io.github.yrashid.actual-schema") version "0.1.2"
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

    // Храним снимок в Git, чтобы checkActualSchema мог проверять его в CI.
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

Создайте и добавьте в Git первый снимок:

```bash
./gradlew generateActualSchema
git add database/schema.sql
```

Приложение и Gradle-плагин настраивают Liquibase независимо. Если приложение использует contexts,
labels или параметры changelog, укажите те же значения в `actualSchema`, как показано ниже. Сейчас
плагин выполняет миграции через Liquibase 5.0.3.

## Задачи

### `generateActualSchema`

Применяет выбранные миграции к новой временной базе и записывает итоговый DDL в `outputFile`.

```bash
./gradlew generateActualSchema
```

Запускайте эту команду после добавления или изменения миграций. Если снимок хранится в репозитории,
проверьте и закоммитьте ожидаемые изменения.

### `checkActualSchema`

Создаёт отдельный снимок-кандидат, не перезаписывая `outputFile`, побайтово сравнивает файлы и
завершается с ошибкой, если сохранённый снимок отсутствует или устарел.

```bash
./gradlew checkActualSchema
```

Чтобы проверка была полезна, задайте `outputFile` вне каталога `build/`, например
`database/schema.sql`, и добавьте этот файл в Git.

## Конфигурация

### Output file, схемы, образ PostgreSQL и timeout запуска

```kotlin
actualSchema {
    outputFile.set(layout.projectDirectory.file("database/schema.sql"))

    postgresImage.set("postgres:16")
    postgresStartupTimeoutSeconds.set(60)
    schemas.set(listOf("public", "reporting"))
    excludeTables.set(setOf("public.audit_log"))

    includeLiquibaseTables.set(false)
    normalizeOutput.set(true)
}
```

- Пустой список `schemas` означает «выгрузить все схемы».
- Значения `excludeTables` передаются в `pg_dump --exclude-table`.
- Служебные таблицы Liquibase по умолчанию не попадают в снимок.
- Нормализация удаляет заголовки с версиями PostgreSQL, случайные ключи
  `\\restrict`/`\\unrestrict` и различия в завершающих пробелах.

### Liquibase contexts, labels и parameters

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

Указывайте только те значения, которые нужны вашему changelog. Пустые списки contexts и labels
оставляют стандартное поведение выбора миграций Liquibase.

### Справочник параметров

| Свойство | По умолчанию | Назначение |
| --- | --- | --- |
| `changelogFile` | обязательный параметр | Корневой changelog Liquibase |
| `resourceBaseDir` | `src/main/resources` | Базовый каталог подключаемых ресурсов changelog |
| `outputFile` | `build/generated/actual-schema/schema.sql` | Снимок сгенерированной схемы |
| `postgresImage` | `postgres:16` | PostgreSQL-совместимый Docker-образ |
| `postgresImageCompatibleSubstituteFor` | `postgres` | Каноническое имя образа для Testcontainers |
| `postgresStartupTimeoutSeconds` | `60` | Timeout запуска временного контейнера PostgreSQL |
| `databaseName` | `actual_schema` | Имя временной базы |
| `username` / `password` | `actual_schema` | Учётные данные только для временной базы |
| `schemas` | `public` | Схемы для `pg_dump`; пустой список означает все схемы |
| `excludeTables` | пусто | Шаблоны таблиц, исключаемых из dump |
| `liquibaseContexts` | пусто | Включённые contexts Liquibase |
| `liquibaseLabels` | пусто | Включённые labels Liquibase |
| `liquibaseParameters` | пусто | Параметры changelog |
| `liquibaseDefaultSchema` | не задан | Default schema для Liquibase |
| `liquibaseSchema` | не задан | Схема служебных таблиц Liquibase |
| `liquibaseChangeLogTable` | `databasechangelog` | Имя служебной таблицы changelog Liquibase |
| `liquibaseChangeLogLockTable` | `databasechangeloglock` | Имя lock-таблицы changelog Liquibase |
| `includeLiquibaseTables` | `false` | Включать служебные таблицы в снимок |
| `normalizeOutput` | `true` | Удалять изменчивые строки `pg_dump` и группировать блоки, создающие индексы, по таблицам |

## Образы PostgreSQL и PostGIS

Testcontainers сам скачивает настроенный образ через Docker. Чтобы использовать другую версию
PostgreSQL, задайте `postgresImage`:

```kotlin
actualSchema {
    postgresImage.set("postgres:17")
}
```

PostgreSQL-совместимые образы, например PostGIS, можно объявить заменой официального образа:

```kotlin
actualSchema {
    postgresImage.set("postgis/postgis:16-3.4")
    postgresImageCompatibleSubstituteFor.set("postgres")
}
```

Для воспроизводимых снимков закрепите образ по digest. Для авторизации в registry Docker и
Testcontainers используют обычную конфигурацию реестра на компьютере пользователя.

## Расширения Liquibase и custom changes

Добавляйте JAR-файлы расширений, парсеры, сериализаторы или классы проекта в изолированный runtime
Liquibase плагина:

```kotlin
dependencies {
    add("actualSchemaLiquibaseRuntime", "org.liquibase.ext:liquibase-hibernate6:VERSION")
    add("actualSchemaLiquibaseRuntime", project(":database-custom-changes"))
}
```

Расширения должны быть совместимы с версией Liquibase, которую использует плагин.

## Groovy DSL

```groovy
plugins {
    id 'io.github.yrashid.actual-schema' version '0.1.2'
}

actualSchema {
    changelogFile.set(
        layout.projectDirectory.file('src/main/resources/db/changelog/db.changelog-master.yaml')
    )
    resourceBaseDir.set(layout.projectDirectory.dir('src/main/resources'))
    outputFile.set(layout.projectDirectory.file('database/schema.sql'))

    postgresImage.set('postgres:16')
    postgresStartupTimeoutSeconds.set(60)
    schemas.set(['public'])
    excludeTables.set(['public.audit_log'] as Set)

    liquibaseContexts.set(['production'])
    liquibaseLabels.set(['core'])
    liquibaseParameters.put('schemaName', 'public')

    includeLiquibaseTables.set(false)
    normalizeOutput.set(true)
}
```

## Использование в CI

Сгенерируйте `database/schema.sql` локально и добавьте его в Git. После этого CI должен запускать
только `checkActualSchema`:

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

На GitHub-hosted Ubuntu runners Docker уже установлен. На self-hosted runner Docker-совместимый
daemon должен быть доступен пользователю, запускающему build.

## Частые проблемы

### Docker недоступен

Запустите Docker и проверьте доступ от того же пользователя, который запускает Gradle:

```bash
docker info
```

Для Colima, Podman, remote Docker и других совместимых runtimes настройте Testcontainers через
обычные environment variables и параметры socket выбранного runtime.

### Changelog не найден

Используйте project-relative Gradle file provider и убедитесь, что `resourceBaseDir` содержит
ресурсы changelog:

```kotlin
actualSchema {
    changelogFile.set(
        layout.projectDirectory.file("src/main/resources/db/changelog/db.changelog-master.yaml")
    )
    resourceBaseDir.set(layout.projectDirectory.dir("src/main/resources"))
}
```

Также проверьте имя и расширение файла: `.yaml`, `.yml`, `.xml`, `.json` или formatted SQL.

### Snapshot устарел

Пересоздайте его, проверьте diff и закоммитьте ожидаемое изменение:

```bash
./gradlew generateActualSchema
git diff -- database/schema.sql
```

### Testcontainers не может скачать образ PostgreSQL

Попробуйте скачать настроенный образ напрямую:

```bash
docker pull postgres:16
```

Проверьте сеть, учётные данные registry, proxy, имя образа и запрошенный tag. Перед запуском Gradle
необходимо отдельно авторизовать Docker в private registry.

Если образ доступен, но PostgreSQL медленно стартует на self-hosted runner, увеличьте
`postgresStartupTimeoutSeconds`.

### Расширение Liquibase не обнаружено

Добавьте его в `actualSchemaLiquibaseRuntime`, а не только в `implementation` приложения.

### Плагин не разрешается

Убедитесь, что запрошенная версия видна в
[Gradle Plugin Portal](https://plugins.gradle.org/plugin/io.github.yrashid.actual-schema). Если
версия только что опубликована, дайте Gradle caches и зеркалам немного времени на обновление.

## Безопасность и модель доверия

Changelog Liquibase, SQL-файлы и custom change classes являются исполняемыми входными данными.
Запускайте плагин только для доверенного содержимого репозитория, особенно в CI с доступом к Docker
или секретам. Сама база временная и изолированная, но доступ к Docker daemon является привилегией.

О подозрениях на уязвимость сообщайте приватно по инструкции в [SECURITY.md](SECURITY.md).

## Текущие ограничения

- Поддерживается только PostgreSQL.
- Нужен Docker или совместимый с Testcontainers runtime.
- DDL может измениться при обновлении образа PostgreSQL или версии Liquibase.
- Build cache для генерации отключён, потому что Docker tags могут быть изменяемыми.

## Информация о проекте

Пользовательские изменения описаны в [CHANGELOG.md](CHANGELOG.md). Если вы хотите участвовать в
разработке самого плагина, прочитайте [CONTRIBUTING.md](CONTRIBUTING.md).

## Лицензия

Проект распространяется по [Apache License 2.0](LICENSE).
