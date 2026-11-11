import SwiftUI
import AVFoundation
import TeeraKit

/// Teera on the iPhone. The game itself is the same Kotlin code as on Android and Windows
/// (teera/iphone, built into TeeraKit); this only gives it the screen.
@main
struct TeeraApp: App {
    @Environment(\.scenePhase) private var phase

    init() {
        // Our sounds mix with the player's own music rather than stopping it.
        try? AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default, options: [.mixWithOthers])
        try? AVAudioSession.sharedInstance().setActive(true)
    }

    var body: some Scene {
        WindowGroup {
            GameView()
                .ignoresSafeArea(.all)
                .preferredColorScheme(.dark)
                // A room's link: teera://join/CODE
                .onOpenURL { url in MainKt.openLink(link: url.absoluteString) }
        }
        .onChange(of: phase) { now in
            MainKt.appInBackground(background: now != .active)
        }
    }
}

/// The Kotlin screen, full screen; it keeps clear of the notch and the home bar itself.
struct GameView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainKt.MainViewController()
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}
