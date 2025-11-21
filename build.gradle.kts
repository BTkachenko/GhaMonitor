// Gradle build configuration for the GitHub Actions monitor CLI.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.0.21"
    application
}

group = "com.example"
version = "1.0.0"

repositories {
    mavenCentral()
}

val jacksonVersion = "2.18.2"

dependencies {
    implementation(kotlin("stdlib"))

    // JSON support with Jackson Kotlin module.
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.core:jackson-annotations:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
}

application {
    // Kotlin application entry point (MainKt)
    mainClass.set("com.example.ghamonitor.MainKt")
}

// Configure Java toolchain for JDK 21.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// Use the new compilerOptions DSL instead of deprecated kotlinOptions.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}
