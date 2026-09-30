import UIKit
import PantauHTTP

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        #if DEBUG
        // 2.0.1 exposes the full initializer to Swift (SKIE generates partial-argument overloads
        // only for up to 5 defaulted parameters); pass every field explicitly.
        PantauHttp.shared.start(configuration: PantauHttpConfiguration(
            maxTransactions: 200,
            bodySizeLimit: 1_048_576,
            shakeEnabled: true,
            notificationPolicy: .whenBackgrounded,
            redactedHeaders: ["Authorization", "Cookie", "Set-Cookie", "Proxy-Authorization"],
            ignoredHosts: [],
            dashboardUrl: ProcessInfo.processInfo.environment["PANTAU_DASHBOARD_URL"],
            dashboardDeviceName: "Example app (iOS)"
        ))
        #endif
        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = UINavigationController(rootViewController: ViewController())
        window.makeKeyAndVisible()
        self.window = window
        return true
    }
}
