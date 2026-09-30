import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.skie)
    `maven-publish`
}

kotlin {
    explicitApi()

    compilerOptions {
        optIn.addAll(
            "kotlin.uuid.ExperimentalUuidApi",
            "kotlin.io.encoding.ExperimentalEncodingApi",
            "kotlin.time.ExperimentalTime",
            "kotlinx.coroutines.FlowPreview",
            "kotlinx.cinterop.ExperimentalForeignApi",
            "kotlinx.cinterop.BetaInteropApi",
            "kotlin.experimental.ExperimentalNativeApi",
            "kotlin.experimental.ExperimentalObjCName",
        )
    }

    android {
        namespace = "com.pantauhttp.core"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
        androidResources {
            enable = true
        }
        withHostTest { }
    }

    val xcf = XCFramework("PantauHTTPCore")
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "PantauHTTPCore"
            isStatic = true
            xcf.add(this)
        }
        target.compilations.getByName("main").cinterops.create("PantauShim") {
            definitionFile.set(project.file("src/nativeInterop/cinterop/PantauShim.def"))
            packageName = "com.pantauhttp.shim"
            compilerOpts("-I${project.file("src/nativeInterop/cinterop").absolutePath}")
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.okhttp)
            implementation(libs.androidx.core)
            implementation(libs.kotlinx.coroutines.android)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.kotlin.testJunit)
            implementation(libs.okhttp.mockwebserver)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
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
            name.set("PantauHTTP Core")
            description.set("In-app HTTP inspector core for Kotlin Multiplatform: Ktor plugin, OkHttp interceptor and URLSession capture, exports and dashboard push.")
            url.set("https://github.com/HarioBudiharjo/pantau-http")
            licenses { license { name.set("MIT"); url.set("https://opensource.org/licenses/MIT") } }
        }
    }
}
