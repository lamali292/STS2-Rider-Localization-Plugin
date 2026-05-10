import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.3.0"
}

group = "com.lamali"
version = "1.0.4"

// --- ADD THIS BLOCK ---
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
// ----------------------

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    intellijPlatform {
        if (System.getenv("CI") != null) {
            rider("2025.1", useInstaller = false)
        } else {
            local("C:/Program Files/JetBrains/JetBrains Rider 2025.3.3")
        }
    }
}

intellijPlatform {
    buildSearchableOptions = false
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "241"
            untilBuild = "999.*"
        }
    }
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
    }
}

tasks.register<JavaExec>("runPreview") {
    group = "application"
    // Remove .kt and add Kt to the end of the filename
    mainClass.set("com.lamali.cardloc.TestLauncherKt")
    classpath = sourceSets["main"].runtimeClasspath

    // This forces the preview to use Java 21 so it matches your Kotlin target
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    })
}