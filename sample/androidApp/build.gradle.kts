import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// Point the sample at a running dashboard: ./gradlew :sample:androidApp:installDebug -PdashboardUrl=http://10.0.2.2:9435
val dashboardUrl: String = (project.findProperty("dashboardUrl") as? String).orEmpty()

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

android {
    namespace = "com.pantauhttp.sample"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.pantauhttp.sample"
        minSdk = libs.versions.android.minSdkCompose.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "2.0.1"
        buildConfigField("String", "PANTAU_DASHBOARD_URL", "\"$dashboardUrl\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":pantau-http"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
}
