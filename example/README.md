# PantauHTTP example (published SDK)

Standalone consumer of the *published* SDK; unlike `sample/`, nothing here builds the library from source. Copy this folder anywhere, it has its own Gradle wrapper (add a `local.properties` with `sdk.dir`).

- `androidApp/`: Gradle project that resolves `com.pantauhttp:pantau-http:2.0.2` from
  `https://hariobudiharjo.github.io/pantau-http-kmm/maven` (no token). Build with JDK 17:
  `./gradlew :androidApp:installDebug -PdashboardUrl=http://10.0.2.2:9435`
- `iosApp/`: XcodeGen project that pulls the `PantauHTTP` Swift package from
  `https://github.com/HarioBudiharjo/pantau-http-kmm.git`. `xcodegen generate && open PantauDemo.xcodeproj`.
