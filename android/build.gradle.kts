// Top-level build file. Plugin versions are declared in settings.gradle.kts
// (pluginManagement block) and applied per-module in app/build.gradle.kts.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
