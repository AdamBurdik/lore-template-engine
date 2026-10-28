plugins {
    id("java")
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.23"
    id("xyz.jpenilla.run-paper") version "3.1.0"
    id("com.gradleup.shadow") version "9.6.1"
}

group = "me.adamix.loretemplateengine"
version = "0.7.1"

repositories {
    gradlePluginPortal()
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://repo.aikar.co/content/groups/aikar/") }
    maven("https://repo.nexomc.com/releases")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")
    paperweight.paperDevBundle("26.1.2.build.+")

    compileOnly("org.jetbrains:annotations:26.1.0")

    compileOnly("org.projectlombok:lombok:1.18.48")
    annotationProcessor("org.projectlombok:lombok:1.18.48")
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")
    implementation("com.github.AdamBurdik:kdl4j:19afde84f4")
    implementation("com.github.AdamBurdik:kidl:30937d85e1")
    implementation("co.aikar:acf-paper:0.5.1-SNAPSHOT")

    // Integrations
    compileOnly("com.nexomc:nexo:1.25.0")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.papermc.paper:paper-api:26.1.2.build.74-stable")
}

tasks {
    runServer {
        minecraftVersion("26.1.2")
    }

    shadowJar {
        archiveClassifier.set("")
        relocate("co.aikar.commands", "me.adamix.lte.acf")
        relocate("co.aikar.locales", "me.adamix.lte.locales")
    }
    
    build {
        dependsOn(shadowJar)
    }

    test {
        useJUnitPlatform()
    }
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}