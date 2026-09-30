pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // PantauHTTP artifacts, served from GitHub Pages (no token needed).
        maven("https://hariobudiharjo.github.io/pantau-http-kmm/maven")
    }
}
rootProject.name = "pantau-http-example"
include(":androidApp")
