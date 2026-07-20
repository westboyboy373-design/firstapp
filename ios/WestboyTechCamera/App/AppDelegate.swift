import UIKit
import AVFoundation

final class AppDelegate: NSObject, UIApplicationDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        configureAudioSession()
        return true
    }

    /// Configures the shared AVAudioSession for simultaneous playback
    /// (background track monitoring) while recording video WITHOUT
    /// capturing microphone input. `.playback` (not `.playAndRecord`)
    /// is intentional here — we never want the OS to open a mic input.
    private func configureAudioSession() {
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playback, mode: .moviePlayback, options: [.mixWithOthers])
            try session.setActive(true)
        } catch {
            print("⚠️ Failed to configure AVAudioSession: \(error)")
        }
    }

    func applicationDidEnterBackground(_ application: UIApplication) {
        // Belt-and-suspenders: CameraManager/AudioManager also listen for
        // this notification directly and tear down their own hardware
        // loops. See CameraManager.handleAppBackgrounded().
        NotificationCenter.default.post(name: .westboyAppDidEnterBackground, object: nil)
    }
}

extension Notification.Name {
    static let westboyAppDidEnterBackground = Notification.Name("westboyAppDidEnterBackground")
}
