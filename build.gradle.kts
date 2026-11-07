import net.ltgt.gradle.errorprone.errorprone
import net.ltgt.gradle.nullaway.nullaway
import net.ltgt.gradle.errorprone.CheckSeverity

plugins {
    id("java")
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.23"
    id("xyz.jpenilla.run-paper") version "3.1.0"
    id("com.gradleup.shadow") version "9.6.1"

    id("net.ltgt.errorprone") version "5.2.0"
    id("net.ltgt.nullaway") version "3.0.0"
}

group = "me.adamix.loretemplateengine"
version = "0.7.3"

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

    errorprone("com.google.errorprone:error_prone_core:2.42.0")
    errorprone("com.uber.nullaway:nullaway:0.14.3")
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

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        nullaway {
            error()
            disableWarningsInGeneratedCode.set(true) // Recommended for lombok
            annotatedPackages.add("me.adamix")
        }
    }
}

tasks.named<JavaCompile>("compileTestJava") {
    options.errorprone.nullaway {
        severity.set(CheckSeverity.OFF)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.isFork = true
    options.forkOptions.jvmArgs?.add("--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED")
    options.forkOptions.jvmArgs?.add("--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED")
    options.forkOptions.jvmArgs?.add("--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED")
    options.forkOptions.jvmArgs?.add("--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED")
    options.forkOptions.jvmArgs?.add("--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}