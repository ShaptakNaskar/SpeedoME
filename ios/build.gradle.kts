// The iOS app's Kotlin side, built into the static SpeedoKit framework that iosApp/ links.
// commonMain: screens shared with the Linux desktop preview. iosMain: CoreLocation, UIKit glue.
// desktopMain: the preview (`./gradlew :ios:preview` window, `:ios:shots` PNGs), fed by the simulator.
// Kotlin compiles for iOS here on Linux; linking the framework needs macOS (CI does it).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(17)
    jvm("desktop")
    iosArm64 {
        binaries.framework {
            baseName = "SpeedoKit"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":engine"))
            implementation(libs.cmp.runtime)
            implementation(libs.cmp.foundation)
            implementation(libs.cmp.ui)
        }
        named("desktopMain").dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

val desktopMain = kotlin.jvm("desktop").compilations["main"]

tasks.register<JavaExec>("preview") {
    group = "ios"
    description = "Opens the iOS UI in an iPhone-sized desktop window, driven by the simulator."
    classpath(desktopMain.output.allOutputs, desktopMain.runtimeDependencyFiles)
    mainClass = "com.sappy.speedome.ios.DesktopKt"
}

tasks.register<JavaExec>("shots") {
    group = "ios"
    description = "Renders the iOS screens to PNGs in build/shots, no display needed."
    classpath(desktopMain.output.allOutputs, desktopMain.runtimeDependencyFiles)
    mainClass = "com.sappy.speedome.ios.DesktopKt"
    args(layout.buildDirectory.dir("shots").get().asFile.absolutePath)
}
