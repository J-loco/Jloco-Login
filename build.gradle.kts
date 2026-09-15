import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    application
    alias(libs.plugins.spotless)
    alias(libs.plugins.errorprone)
}

group = "org.jloco.locos"
// Version from git ("dev" without git, e.g. in the Docker build).
version = runCatching {
    providers.exec {
        commandLine("git", "describe", "--tags", "--always", "--dirty")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()
}.getOrNull()?.takeIf { it.isNotEmpty() } ?: "dev"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(libs.netty.bom))
    implementation(libs.netty.handler)
    implementation(libs.netty.codec)
    implementation(libs.netty.transport)
    implementation(libs.hikaricp)
    implementation(libs.slf4j.api)
    implementation(libs.jjwt.api)

    runtimeOnly(libs.mariadb)
    runtimeOnly(libs.logback.classic)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.mariadb)
    testRuntimeOnly(libs.junit.platform.launcher)

    errorprone(libs.errorprone.core)
}

application {
    mainClass = "org.jloco.locos.Main"
    applicationName = "login"
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    options.errorprone {
        disableWarningsInGeneratedCode = true
        // Netty write/close futures are fire-and-forget by design (failures reach exceptionCaught); periodic
        // java.util.concurrent tasks keep their futures explicitly.
        check("FutureReturnValueIgnored", CheckSeverity.OFF)
    }
}

tasks.jar {
    archiveFileName = "login.jar"
    manifest {
        attributes(
            "Main-Class" to application.mainClass,
            "Implementation-Version" to project.version,
        )
    }
}

// Unit tests run everywhere (including the Docker build); tests tagged "integration" need Docker for Testcontainers.
tasks.test {
    useJUnitPlatform { excludeTags("integration") }
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Runs the integration tests (MariaDB in Testcontainers, the server on random ports)."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("integration") }
    shouldRunAfter(tasks.test)
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

spotless {
    // LF everywhere (see .gitattributes), so Windows checkouts and the Linux Docker build agree.
    lineEndings = com.diffplug.spotless.LineEnding.UNIX
    java {
        palantirJavaFormat(libs.versions.palantir.java.format.get())
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
    }
}
