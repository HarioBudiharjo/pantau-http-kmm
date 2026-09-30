import UIKit
import PantauHTTP

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {

    var window: UIWindow?

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        #if DEBUG
        // Start BEFORE any URLSession is created so default-configuration sessions pick up the capture engine.
        let dashboardURL = ProcessInfo.processInfo.environment["PANTAU_DASHBOARD_URL"]
        PantauHttp.shared.start(configuration: PantauHttpConfiguration(dashboardUrl: dashboardURL))
        #endif

        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = UINavigationController(rootViewController: ViewController())
        window.makeKeyAndVisible()
        self.window = window
        return true
    }
}
