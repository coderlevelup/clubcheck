plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}
android {
    namespace = "app.clubcheck"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.clubcheck"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
        // Base URL of the ClubCheck service (built in a later plan). Empty: check-ins stay on the phone.
        buildConfigField("String", "CLOUD_URL", "\"\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.all { it.useJUnitPlatform() } }
}
dependencies {
    implementation(project(":core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime)
    implementation(libs.okhttp)
    implementation(libs.coroutines.core)
    implementation(libs.mediapipe.vision)
    implementation(libs.mlkit.face)
    implementation(libs.exifinterface)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.coroutines.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
