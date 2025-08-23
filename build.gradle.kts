plugins {
    kotlin("jvm") version "2.2.0"
    id("com.gradleup.shadow") version "9.0.0-beta13"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.17"
}

group = "org.hn.wanderingplus"
version = "1.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    paperweight.paperDevBundle("1.21.8-R0.1-SNAPSHOT")
}

kotlin {
    jvmToolchain(21)
}

tasks {
    shadowJar {
        mergeServiceFiles()
        archiveClassifier.set("")
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.named("reobfJar") {
    dependsOn("jar")
}

tasks.named("assemble") {
    dependsOn("reobfJar")
}