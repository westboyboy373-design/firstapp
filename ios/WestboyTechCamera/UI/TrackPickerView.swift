import SwiftUI

struct TrackPickerView: View {
    @ObservedObject var audioManager: AudioManager
    @Environment(\.dismiss) private var dismiss

    // Bundle these as royalty-free/owned sample assets — see the "Open
    // Questions" flag in the design doc about licensing for any track
    // library shipped with the app.
    private let sampleTracks: [BackgroundTrack] = [
        BackgroundTrack(title: "Neon Drive", fileURL: Bundle.main.url(forResource: "neon_drive", withExtension: "m4a") ?? URL(fileURLWithPath: "")),
        BackgroundTrack(title: "Midnight Loop", fileURL: Bundle.main.url(forResource: "midnight_loop", withExtension: "m4a") ?? URL(fileURLWithPath: "")),
        BackgroundTrack(title: "Retro Wave", fileURL: Bundle.main.url(forResource: "retro_wave", withExtension: "m4a") ?? URL(fileURLWithPath: ""))
    ]

    var body: some View {
        NavigationStack {
            List(sampleTracks) { track in
                Button {
                    audioManager.loadTrack(track)
                    dismiss()
                } label: {
                    HStack {
                        Image(systemName: "music.note")
                        Text(track.title)
                        Spacer()
                        if audioManager.selectedTrack == track {
                            Image(systemName: "checkmark").foregroundStyle(Theme.neonCyan)
                        }
                    }
                }
            }
            .navigationTitle("Background Track")
            .scrollContentBackground(.hidden)
            .background(Theme.background)
        }
        .preferredColorScheme(.dark)
    }
}
