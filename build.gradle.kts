plugins {
    id("java")
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.23"
    id("xyz.jpenilla.run-paper") version "3.1.0"
    id("com.github.spotbugs") version "6.5.11"
    id("com.gradleup.shadow") version "9.6.1"
}

group = "me.adamix.loretemplateengine"
version = "0.5.1"

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

    // Explicitly retain SpotBugs AND add JetBrains annotations to its runtime
    spotbugs("com.github.spotbugs:spotbugs:4.10.4")
    spotbugs("org.jetbrains:annotations:26.1.0")
    
    spotbugsPlugins("com.h3xstream.findsecbugs:findsecbugs-plugin:1.14.0")
    compileOnly("org.projectlombok:lombok:1.18.48")
    annotationProcessor("org.projectlombok:lombok:1.18.48")
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")
    implementation("com.github.kdl-org:kdl4j:v1.0.1")
    implementation("co.aikar:acf-paper:0.5.1-SNAPSHOT")

    // Integrations
    compileOnly("com.nexomc:nexo:1.25.0")
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
}

spotbugs {
    toolVersion = "4.10.4"
    excludeFilter.set(rootProject.file("spotbugs-exclude.xml"))
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}