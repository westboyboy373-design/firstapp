import SwiftUI

@main
struct WestboyTechCameraApp: App {
    // Registers AppDelegate for audio-session lifecycle handling
    // (interruptions, route changes, background teardown).
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            RootView()
                .preferredColorScheme(.dark)
        }
    }
}
