import UIKit
import PantauHTTP

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        #if DEBUG
        PantauHttp.shared.start(configuration: PantauHttpConfiguration(
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
