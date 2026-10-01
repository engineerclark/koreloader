plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "org.mmclark.koreloader.application"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }
    // Plain JVM target: keeps commonMain honest and runs unit tests without Android.
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":domain"))
            api(libs.kotlinx.coroutines.core)
            api(libs.okio)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
