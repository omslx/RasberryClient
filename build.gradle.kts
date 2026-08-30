plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.serialization") version "2.3.21"
    id("com.gradleup.shadow") version "9.0.2"
}

group = "xyz.mslx.rasberryClient"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.viaversion.com/everything/")
}

dependencies {
    // 1.21 API برای player.transfer() (api-version '1.21' در paper-plugin.yml)
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")

    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("com.viaversion:viaversion-api:4.9.3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("redis.clients:jedis:5.1.0")
}

kotlin {
    jvmToolchain(23)
    compilerOptions {
        // Paper 1.21 روی Java 21 اجرا می‌شود؛ بایت‌کد باید 21 بماند
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

tasks.withType<JavaCompile> {
    options.release.set(21)
}

tasks {
    assemble {
        dependsOn(shadowJar)
    }

    shadowJar {
        archiveClassifier.set("")
        val relocatePattern = "xyz.mslx.RasberryClient.libs"
        relocate("org.jetbrains.kotlinx", "$relocatePattern.kotlinx")
        relocate("com.zaxxer.hikari", "$relocatePattern.hikari")
        relocate("redis.clients.jedis", "$relocatePattern.jedis")
        relocate("com.google.gson", "$relocatePattern.gson")
    }
}