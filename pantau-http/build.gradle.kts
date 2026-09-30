import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.skie)
    `maven-publish`
}

kotlin {
    explicitApi()

    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.ui.ExperimentalComposeUiApi",
            "androidx.compose.foundation.ExperimentalFoundationApi",
            "kotlinx.coroutines.FlowPreview",
            "kotlinx.cinterop.ExperimentalForeignApi",
            "kotlin.experimental.ExperimentalNativeApi",
        )
    }

    android {
        namespace = "com.pantauhttp.ui"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdkCompose.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
        androidResources {
            enable = true
        }
        withHostTest { }
    }

    val xcf = XCFramework("PantauHTTP")
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "PantauHTTP"   // differs from the Kotlin object name so `PantauHttp.shared` resolves in Swift
            isStatic = true
            // Umbrella framework: Swift sees the core types through this one binary.
            export(project(":pantau-http-core"))
            xcf.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":pantau-http-core"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.compose.materialIconsCore)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.kotlin.testJunit)
        }
    }
}

skie {
    features {
        // Only our own API: wrappers for dependency APIs (Ktor) would reference symbols the framework does not export.
        group("com.pantauhttp") {
            co.touchlab.skie.configuration.DefaultArgumentInterop.Enabled(true)
        }
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/HarioBudiharjo/pantau-http-kmm")
            credentials {
                username = (project.findProperty("gpr.user") as String?) ?: System.getenv("GITHUB_ACTOR")
                password = (project.findProperty("gpr.key") as String?) ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
    publications.withType<MavenPublication> {
        pom {
            name.set("PantauHTTP")
            description.set("In-app HTTP inspector for Kotlin Multiplatform with a shared Compose Multiplatform UI.")
            url.set("https://github.com/HarioBudiharjo/pantau-http")
            licenses { license { name.set("MIT"); url.set("https://opensource.org/licenses/MIT") } }
        }
    }
}
