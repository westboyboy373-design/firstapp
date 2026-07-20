import SwiftUI

struct RecordButton: View {
    let isRecording: Bool
    /// 0...1, wraps every `maxTrackedDuration` seconds so the glow keeps
    /// animating on very long clips instead of freezing at "full".
    let progress: Double
    let action: () -> Void

    private let diameter: CGFloat = 84

    var body: some View {
        Button(action: {
            let generator = UIImpactFeedbackGenerator(style: .medium)
            generator.impactOccurred()
            action()
        }) {
            ZStack {
                Circle()
                    .trim(from: 0, to: progress)
                    .stroke(
                        AngularGradient(
                            colors: [.cyan, .purple, .pink, .cyan],
                            center: .center
                        ),
                        style: StrokeStyle(lineWidth: 4, lineCap: .round)
                    )
                    .rotationEffect(.degrees(-90))
                    .frame(width: diameter + 14, height: diameter + 14)
                    .opacity(isRecording ? 1 : 0.35)
                    .animation(.linear(duration: 0.2), value: progress)

                RoundedRectangle(cornerRadius: isRecording ? 14 : diameter / 2, style: .continuous)
                    .fill(isRecording ? Theme.dangerRed : Color.white)
                    .frame(
                        width: isRecording ? diameter * 0.42 : diameter * 0.82,
                        height: isRecording ? diameter * 0.42 : diameter * 0.82
                    )
                    .animation(.spring(response: 0.35, dampingFraction: 0.65), value: isRecording)

                Circle()
                    .stroke(Color.white.opacity(0.6), lineWidth: 3)
                    .frame(width: diameter, height: diameter)
            }
        }
        .frame(width: diameter + 20, height: diameter + 20)
    }
}
