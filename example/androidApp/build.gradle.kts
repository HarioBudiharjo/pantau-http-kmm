import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val dashboardUrl: String = (project.findProperty("dashboardUrl") as? String).orEmpty()

kotlin {
    compilerOptions { jvmTarget = JvmTarget.JVM_17 }
}

android {
    namespace = "com.example.pantaudemo"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.pantaudemo"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "PANTAU_DASHBOARD_URL", "\"$dashboardUrl\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // The SDK, straight from GitHub Pages. Debug-only so it never ships in a release build.
    debugImplementation("com.pantauhttp:pantau-http:2.0.2")
    releaseImplementation("com.pantauhttp:pantau-http-core:2.0.2") // keeps the API compiling; nothing is started in release

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation("io.ktor:ktor-client-core:3.2.3")
    implementation("io.ktor:ktor-client-okhttp:3.2.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
