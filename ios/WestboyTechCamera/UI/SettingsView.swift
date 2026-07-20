import SwiftUI

struct SettingsView: View {
    var body: some View {
        NavigationStack {
            List {
                Section("Recording") {
                    Text("Resolution cap: 1080p")
                    Text("Frame rate: 60fps (falls back to 30fps)")
                }
                Section("Credits") {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(Branding.creditsTitle)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        Text(Branding.creditsBody)
                            .font(.title2.bold())
                            .foregroundStyle(Theme.neonCyan)
                    }
                    .padding(.vertical, 8)
                }
            }
            .scrollContentBackground(.hidden)
            .background(Theme.background)
            .navigationTitle(Branding.appName)
        }
        .preferredColorScheme(.dark)
    }
}
