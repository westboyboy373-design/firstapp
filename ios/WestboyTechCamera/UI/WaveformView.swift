import SwiftUI

/// Renders `amplitudes` (0...1 each) as animated bars. Driven directly by
/// AudioManager.currentAmplitudes, sampled from the background track
/// actually playing — this is real audio-reactivity, not a decorative loop.
struct WaveformView: View {
    let amplitudes: [Float]

    var body: some View {
        HStack(alignment: .center, spacing: 3) {
            ForEach(Array(amplitudes.enumerated()), id: \.offset) { _, amp in
                RoundedRectangle(cornerRadius: 2)
                    .fill(Theme.neonCyan)
                    .frame(width: 3, height: max(4, CGFloat(amp) * 40))
                    .animation(.easeOut(duration: 0.08), value: amp)
            }
        }
        .frame(height: 44)
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .glassPanel(cornerRadius: 16)
    }
}
