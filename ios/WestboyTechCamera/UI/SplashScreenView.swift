import SwiftUI

struct SplashScreenView: View {
    @State private var opacity: Double = 0

    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()
            VStack(spacing: 12) {
                Image(systemName: "video.fill")
                    .font(.system(size: 44, weight: .light))
                    .foregroundStyle(Theme.neonCyan)
                Text(Branding.splashTagline)
                    .font(.system(size: 17, weight: .medium, design: .rounded))
                    .foregroundStyle(.white.opacity(0.9))
                    .tracking(1.2)
            }
            .opacity(opacity)
        }
        .onAppear {
            withAnimation(.easeIn(duration: 1.1)) {
                opacity = 1
            }
        }
    }
}
