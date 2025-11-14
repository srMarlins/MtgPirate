import SwiftUI
import shared  // This is your compiled Kotlin framework

/// Example SwiftUI App that uses the Kotlin Compose UI
@main
struct MtgPirateApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

/// SwiftUI wrapper for the Kotlin Compose UI
struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}

/// UIViewControllerRepresentable wrapper to embed Compose UI in SwiftUI
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        // Call the Kotlin function that creates the Compose ViewController
        return Main_iosKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // Nothing to update
    }
}

// Alternative: Pure UIKit approach (if not using SwiftUI)
/*
class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        window = UIWindow(frame: UIScreen.main.bounds)
        let viewController = Main_iosKt.MainViewController()
        window?.rootViewController = viewController
        window?.makeKeyAndVisible()
        return true
    }
}
*/

