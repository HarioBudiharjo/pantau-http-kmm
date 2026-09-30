import UIKit
import PantauHTTP

final class ViewController: UIViewController {
    private let log = UITextView()

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "PantauHTTP example"
        view.backgroundColor = .systemBackground
        navigationItem.rightBarButtonItem = UIBarButtonItem(title: "Inspector", style: .done, target: self, action: #selector(openInspector))

        let buttons: [(String, Selector)] = [
            ("GET JSON", #selector(getJSON)),
            ("POST with Bearer", #selector(postJSON)),
            ("Failing host", #selector(failingHost)),
            ("Open Inspector", #selector(openInspector)),
        ]
        let stack = UIStackView(arrangedSubviews: buttons.map { title, action in
            let b = UIButton(type: .system)
            b.setTitle(title, for: .normal)
            b.backgroundColor = .systemBlue; b.setTitleColor(.white, for: .normal); b.layer.cornerRadius = 10
            b.heightAnchor.constraint(equalToConstant: 40).isActive = true
            b.addTarget(self, action: action, for: .touchUpInside)
            return b
        })
        stack.axis = .vertical; stack.spacing = 10; stack.translatesAutoresizingMaskIntoConstraints = false
        log.isEditable = false; log.font = .monospacedSystemFont(ofSize: 12, weight: .regular)
        log.translatesAutoresizingMaskIntoConstraints = false
        log.text = "PantauHTTP \(PantauHttp.shared.isStarted ? "is running" : "is NOT running") — SDK resolved from GitHub via SPM\n"
        view.addSubview(stack); view.addSubview(log)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 16),
            stack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            stack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            log.topAnchor.constraint(equalTo: stack.bottomAnchor, constant: 16),
            log.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            log.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            log.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor),
        ])
        let env = ProcessInfo.processInfo.environment
        if env["AUTOFIRE"] == "1" { DispatchQueue.main.asyncAfter(deadline: .now() + 1) { [weak self] in self?.getJSON(); self?.postJSON(); self?.failingHost() } }
        if env["AUTOPRESENT"] == "1" { DispatchQueue.main.asyncAfter(deadline: .now() + 6) { [weak self] in self?.openInspector() } }
    }

    @objc private func getJSON() { fire(URLRequest(url: URL(string: "https://jsonplaceholder.typicode.com/users/1")!)) }
    @objc private func postJSON() {
        var r = URLRequest(url: URL(string: "https://jsonplaceholder.typicode.com/posts")!)
        r.httpMethod = "POST"; r.setValue("application/json", forHTTPHeaderField: "Content-Type")
        r.setValue("Bearer secret-token", forHTTPHeaderField: "Authorization")
        r.httpBody = #"{"title":"demo"}"#.data(using: .utf8)
        fire(r)
    }
    @objc private func failingHost() { fire(URLRequest(url: URL(string: "https://this-host-does-not-exist.invalid/x")!)) }
    @objc private func openInspector() { PantauHttp.shared.present() }

    private func fire(_ request: URLRequest) {
        URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
            let status = (response as? HTTPURLResponse)?.statusCode
            let line = "\(request.httpMethod ?? "GET") \(request.url?.path ?? "") → \(status.map(String.init) ?? (error?.localizedDescription ?? "?")) (\(data?.count ?? 0) B)\n"
            DispatchQueue.main.async { self?.log.text += line }
        }.resume()
    }
}
