// The gauge toolkit and themes, shared by Android, iOS and the desktop preview. Android draws text
// with android.graphics and AGSL (androidMain); iOS and desktop share a Skia version (skikoMain).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.roborazzi)
}

kotlin {
    jvmToolchain(17)

    android {
        namespace = "com.sappy.speedome.gauges"
        compileSdk = 37
        minSdk = 26
        androidResources { enable = true }
        withHostTestBuilder {}.configure { isIncludeAndroidResources = true }
    }
    jvm("desktop")
    iosArm64()

    // skikoMain: what iOS and the desktop preview share (both draw with Skia).
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("skiko") {
                withJvm()
                withIos()
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.cmp.runtime)
            implementation(libs.cmp.foundation)
            implementation(libs.cmp.ui)
            implementation(libs.cmp.resources)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.graphics)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui.tooling.preview)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.androidx.test.ext.junit)
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.compose.ui.test.junit4)
            implementation(libs.compose.ui.test.manifest)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
            implementation(libs.roborazzi.junit.rule)
        }
    }
}

// iOS and desktop read the fonts through Compose resources, straight from the Android font folder.
compose.resources {
    packageOfResClass = "com.sappy.speedome.gauges.resources"
    generateResClass = always
    customDirectory("skikoMain", provider { layout.projectDirectory.dir("src/androidMain/res") })
}

// Golden images live with the sources; `./gradlew check` verifies every theme still renders the same.
roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

tasks.named("check") { dependsOn("verifyRoborazziAndroidHostTest") }
