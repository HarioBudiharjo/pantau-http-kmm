plugins {
    // Declared once here so every subproject shares the same plugin classloader.
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.skie) apply false
}

val pantauVersion = libs.versions.pantau.get()

allprojects {
    group = "com.pantauhttp"
    version = pantauVersion
}
