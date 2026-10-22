// The iOS app's Kotlin side (Compose UI, CoreLocation glue), built into the static SpeedoKit
// framework that iosApp/ links. Kotlin compiles here on Linux; linking needs macOS (CI does it).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    iosArm64 {
        binaries.framework {
            baseName = "SpeedoKit"
            isStatic = true
        }
    }

    sourceSets {
        iosMain.dependencies {
            implementation(project(":engine"))
            implementation(libs.cmp.runtime)
            implementation(libs.cmp.foundation)
            implementation(libs.cmp.ui)
        }
    }
}
