import SwiftUI

struct FilterDockView: View {
    let selected: FilterType
    let onSelect: (FilterType) -> Void

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 14) {
                    ForEach(FilterType.allCases) { filter in
                        FilterThumbnail(filter: filter, isSelected: filter == selected)
                            .id(filter)
                            .onTapGesture {
                                withAnimation(.spring(response: 0.3, dampingFraction: 0.75)) {
                                    onSelect(filter)
                                }
                            }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 10)
            }
            .onChange(of: selected) { _, newValue in
                withAnimation { proxy.scrollTo(newValue, anchor: .center) }
            }
        }
        .glassPanel(cornerRadius: 24)
        .padding(.horizontal, 12)
    }
}

private struct FilterThumbnail: View {
    let filter: FilterType
    let isSelected: Bool

    var body: some View {
        VStack(spacing: 6) {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(previewGradient)
                .frame(width: 56, height: 56)
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .strokeBorder(isSelected ? Theme.neonCyan : .clear, lineWidth: 2)
                )
                .shadow(color: isSelected ? Theme.neonCyan.opacity(0.6) : .clear, radius: 8)

            Text(filter.rawValue)
                .font(.system(size: 11, weight: isSelected ? .semibold : .regular))
                .foregroundStyle(isSelected ? .white : .white.opacity(0.6))
                .lineLimit(1)
        }
    }

    private var previewGradient: LinearGradient {
        switch filter {
        case .none:
            return LinearGradient(colors: [.gray.opacity(0.4), .gray.opacity(0.2)], startPoint: .top, endPoint: .bottom)
        case .classicBW:
            return LinearGradient(colors: [.black, .white.opacity(0.8)], startPoint: .top, endPoint: .bottom)
        case .cyberpunk:
            return LinearGradient(colors: [.pink, .purple, .blue], startPoint: .topLeading, endPoint: .bottomTrailing)
        case .vintage90s:
            return LinearGradient(colors: [.orange.opacity(0.8), .brown.opacity(0.6)], startPoint: .top, endPoint: .bottom)
        case .cinematic:
            return LinearGradient(colors: [.gray.opacity(0.6), .black], startPoint: .top, endPoint: .bottom)
        }
    }
}
