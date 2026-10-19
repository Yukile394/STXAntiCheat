plugins {
    java
}

group = "dev.stx"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // En düşük hedef (1.21) ile derlenir; yeni sürümlerde aynı API çalışır (api-version: 1.21).
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    filesMatching("plugin.yml") { expand("version" to project.version) }
}

// JUnit gerektirmeyen çekirdek test paketi:  ./gradlew coreTests
tasks.register<JavaExec>("coreTests") {
    group = "verification"
    description = "Bukkit'ten bağımsız tespit mantığı testleri (vanilla fizik simülasyonu dahil)"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.stx.anticheat.core.CoreTests")
    jvmArgs("-Dstdout.encoding=UTF-8")
}
tasks.named("check") { dependsOn("coreTests") }
