import org.gradle.plugin.compatibility.compatibility

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "2.1.1"
    id("org.jetbrains.dokka-javadoc") version "2.2.0"
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    withSourcesJar()
    withJavadocJar()
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("org.liquibase:liquibase-core:5.0.3")
    implementation("org.postgresql:postgresql:42.7.11")
    implementation("org.testcontainers:testcontainers-postgresql:2.0.5")

    constraints {
        implementation("org.apache.commons:commons-compress:1.28.0") {
            because("Keep Testcontainers archive handling on a patched baseline")
        }
        implementation("org.apache.commons:commons-lang3:3.20.0") {
            because("Keep Liquibase/OpenCSV transitive utility code on a patched baseline")
        }
        implementation("commons-io:commons-io:2.22.0") {
            because("Keep Liquibase transitive file handling on a patched baseline")
        }
        implementation("org.yaml:snakeyaml:2.6") {
            because("Keep Liquibase YAML parsing on a patched baseline")
        }
        implementation("com.opencsv:opencsv:5.12.0") {
            because("Keep Liquibase CSV parsing on a patched baseline")
        }
    }

    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

gradlePlugin {
    website.set("https://github.com/YRashid/actual-schema-gradle-plugin")
    vcsUrl.set("https://github.com/YRashid/actual-schema-gradle-plugin")

    plugins {
        create("actualSchema") {
            id = "io.github.yrashid.actual-schema"
            implementationClass = "io.github.yrashid.actualschema.ActualSchemaPlugin"
            displayName = "Actual Schema Gradle Plugin"
            description =
                "Generates actual PostgreSQL schema DDL by applying Liquibase migrations to a temporary database."
            tags.set(listOf("postgresql", "liquibase", "schema", "database", "migration", "testcontainers"))
            compatibility {
                features {
                    configurationCache.set(true)
                }
            }
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("Actual Schema Gradle Plugin")
            description.set(
                "Generates actual PostgreSQL schema DDL by applying Liquibase migrations to a temporary database."
            )
            url.set("https://github.com/YRashid/actual-schema-gradle-plugin")
            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    distribution.set("repo")
                }
            }
            developers {
                developer {
                    id.set("YRashid")
                    name.set("YRashid")
                    url.set("https://github.com/YRashid")
                }
            }
            scm {
                connection.set("scm:git:https://github.com/YRashid/actual-schema-gradle-plugin.git")
                developerConnection.set("scm:git:ssh://git@github.com/YRashid/actual-schema-gradle-plugin.git")
                url.set("https://github.com/YRashid/actual-schema-gradle-plugin")
            }
        }
    }
}

val functionalTestSourceSet = sourceSets.create("functionalTest")

configurations[functionalTestSourceSet.implementationConfigurationName]
    .extendsFrom(configurations.testImplementation.get())
configurations[functionalTestSourceSet.runtimeOnlyConfigurationName]
    .extendsFrom(configurations.testRuntimeOnly.get())

gradlePlugin.testSourceSets(functionalTestSourceSet)

val functionalTest = tasks.register<Test>("functionalTest") {
    description = "Runs Gradle TestKit functional tests (Docker is required)."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = functionalTestSourceSet.output.classesDirs
    classpath = functionalTestSourceSet.runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<Jar>("javadocJar") {
    dependsOn(tasks.dokkaGeneratePublicationJavadoc)
    from(tasks.dokkaGeneratePublicationJavadoc.flatMap { it.outputDirectory })
}

tasks.check {
    dependsOn(functionalTest)
}
