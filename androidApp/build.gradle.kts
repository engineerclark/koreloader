plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.mmclark.koreloader"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.mmclark.koreloader"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.compileSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(21)
}

/** Ships the repository's LICENSE (GPL-3.0) inside the APK, so the app can show it offline. */
abstract class CopyLicenseTask : DefaultTask() {
    @get:InputFile
    abstract val license: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        license.get().asFile.copyTo(outputDir.get().file("LICENSE.txt").asFile, overwrite = true)
    }
}

val copyLicenseAsset = tasks.register<CopyLicenseTask>("copyLicenseAsset") {
    license.set(rootProject.layout.projectDirectory.file("LICENSE"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyLicenseAsset, CopyLicenseTask::outputDir)
    }
}

dependencies {
    implementation(project(":presentation"))
    implementation(project(":infrastructure"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
