import SwiftUI
import ComposeApp

@main
struct KuroMusicApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView()
                // Compose draws behind the bars and applies the safe-area insets itself.
                .ignoresSafeArea()
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
