import UIKit
import PantauHTTP

/// Mirrors the original Swift example: every button exercises a different capture path.
final class ViewController: UIViewController {

    private let log = UITextView()

    /// A custom session: `PantauHttpIos.enable(configuration:)` injects the capture engine.
    private lazy var customSession: URLSession = {
        let configuration = URLSessionConfiguration.default
        PantauHttpIos.shared.enable(configuration: configuration)
        return URLSession(configuration: configuration)
    }()

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "PantauHTTP Sample"
        view.backgroundColor = .systemBackground
        navigationItem.rightBarButtonItem = UIBarButtonItem(title: "Inspector", style: .done, target: self, action: #selector(openInspector))

        let buttons: [(String, Selector)] = [
            ("GET JSON", #selector(getJSON)),
            ("POST JSON (Bearer token)", #selector(postJSON)),
            ("404 Not Found", #selector(notFound)),
            ("Redirect chain", #selector(redirect)),
            ("Download image", #selector(image)),
            ("GET via custom session", #selector(customSessionRequest)),
            ("Failing host", #selector(failingHost)),
            ("Open Inspector", #selector(openInspector)),
        ]
        let stack = UIStackView(arrangedSubviews: buttons.map { title, action in
            let button = UIButton(type: .system)
            button.setTitle(title, for: .normal)
            button.titleLabel?.font = .systemFont(ofSize: 16, weight: .semibold)
            button.backgroundColor = .systemBlue
            button.setTitleColor(.white, for: .normal)
            button.layer.cornerRadius = 10
            button.heightAnchor.constraint(equalToConstant: 40).isActive = true
            button.addTarget(self, action: action, for: .touchUpInside)
            return button
        })
        stack.axis = .vertical
        stack.spacing = 10
        stack.translatesAutoresizingMaskIntoConstraints = false

        log.isEditable = false
        log.font = .monospacedSystemFont(ofSize: 12, weight: .regular)
        log.translatesAutoresizingMaskIntoConstraints = false
        log.text = "Shake the device (⌃⌘Z in the Simulator) to open the inspector.\n"

        view.addSubview(stack)
        view.addSubview(log)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 16),
            stack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            stack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            log.topAnchor.constraint(equalTo: stack.bottomAnchor, constant: 16),
            log.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            log.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            log.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -16),
        ])

        let environment = ProcessInfo.processInfo.environment
        if environment["AUTOFIRE"] == "1" {
            DispatchQueue.main.asyncAfter(deadline: .now() + 1) { [weak self] in
                self?.getJSON(); self?.postJSON(); self?.notFound(); self?.redirect(); self?.image(); self?.customSessionRequest(); self?.failingHost()
            }
        }
        if environment["AUTOPRESENT"] == "1" {
            DispatchQueue.main.asyncAfter(deadline: .now() + 8) { [weak self] in self?.openInspector() }
        }
    }

    // MARK: Requests

    @objc private func getJSON() {
        fire(URLRequest(url: URL(string: "https://jsonplaceholder.typicode.com/users")!))
    }

    @objc private func postJSON() {
        var request = URLRequest(url: URL(string: "https://jsonplaceholder.typicode.com/posts")!)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer super-secret-token", forHTTPHeaderField: "Authorization")
        request.httpBody = try? JSONSerialization.data(withJSONObject: ["title": "pantau", "body": "hello", "userId": 1])
        fire(request)
    }

    @objc private func notFound() {
        fire(URLRequest(url: URL(string: "https://jsonplaceholder.typicode.com/nope/404")!))
    }

    @objc private func redirect() {
        fire(URLRequest(url: URL(string: "https://httpbin.org/redirect/2")!))
    }

    @objc private func image() {
        fire(URLRequest(url: URL(string: "https://httpbin.org/image/png")!))
    }

    @objc private func customSessionRequest() {
        fire(URLRequest(url: URL(string: "https://jsonplaceholder.typicode.com/todos/1")!), session: customSession)
    }

    @objc private func failingHost() {
        fire(URLRequest(url: URL(string: "https://this-host-does-not-exist.invalid/api")!))
    }

    @objc private func openInspector() {
        PantauHttp.shared.present()
    }

    private func fire(_ request: URLRequest, session: URLSession = .shared) {
        let started = Date()
        session.dataTask(with: request) { [weak self] data, response, error in
            let ms = Int(Date().timeIntervalSince(started) * 1000)
            let status = (response as? HTTPURLResponse)?.statusCode
            let line = "\(request.httpMethod ?? "GET") \(request.url?.path ?? "") → \(status.map(String.init) ?? (error?.localizedDescription ?? "?")) (\(data?.count ?? 0) B, \(ms) ms)\n"
            DispatchQueue.main.async { self?.log.text += line }
        }.resume()
    }
}
