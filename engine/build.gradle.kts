// Pure Kotlin: no Android imports allowed here (see docs/plan.md §4).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}
