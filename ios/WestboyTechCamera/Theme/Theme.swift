import SwiftUI

enum Theme {
    /// #09090A — ultra-dark cinematic base
    static let background = Color(red: 9 / 255, green: 9 / 255, blue: 10 / 255)
    static let neonCyan = Color(red: 0.0, green: 0.95, blue: 1.0)
    static let panelStroke = Color.white.opacity(0.12)
    static let dangerRed = Color(red: 1.0, green: 0.24, blue: 0.24)
}

/// Reusable frosted-glass panel background. Applies a real system blur
/// (not a flat translucent color) so it behaves correctly over moving
/// camera video underneath.
struct GlassPanel: ViewModifier {
    var cornerRadius: CGFloat = 20
    var strokeOpacity: Double = 0.12

    func body(content: Content) -> some View {
        content
            .background(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .fill(.ultraThinMaterial)
            )
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .strokeBorder(Color.white.opacity(strokeOpacity), lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
    }
}

extension View {
    func glassPanel(cornerRadius: CGFloat = 20, strokeOpacity: Double = 0.12) -> some View {
        modifier(GlassPanel(cornerRadius: cornerRadius, strokeOpacity: strokeOpacity))
    }
}
