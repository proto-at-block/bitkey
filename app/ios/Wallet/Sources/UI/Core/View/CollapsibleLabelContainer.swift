import Foundation
import Shared
import SwiftUI

// MARK: -

public struct CollapsibleLabelContainer<
    TopContent: View,
    BottomContent: View,
    CollapsedContent: View
>: View {

    public let collapsed: Bool
    public let topContent: TopContent?
    public let bottomContent: BottomContent?
    public let collapsedContent: CollapsedContent
    public var spacing: CGFloat = 0

    public var body: some View {
        ZStack(alignment: .center) {
            VStack(spacing: spacing) {
                topContent?
                    .accessibilityHidden(collapsed)
                    .allowsHitTesting(!collapsed)
                    .offset(x: 0, y: collapsed ? 10 : 0)
                    .scaleEffect(collapsed ? 0.8 : 1.0)

                bottomContent?
                    .accessibilityHidden(collapsed)
                    .allowsHitTesting(!collapsed)
                    .offset(x: 0, y: collapsed ? -10 : 0)
                    .scaleEffect(collapsed ? 0.8 : 1.0)
            }.opacity(collapsed ? 0 : 1)

            collapsedContent
                .accessibilityHidden(!collapsed)
                .allowsHitTesting(collapsed)
                .opacity(collapsed ? 1 : 0)
                .offset(x: 0, y: collapsed ? 0 : -20)
                .scaleEffect(collapsed ? 1 : 1.2)
        }
        .transition(.scale(scale: 1))
        .animation(.snappy, value: collapsed)
    }
}

public struct CollapsedMoneyView: View {

    public let height: CGFloat
    public var centered: Bool = true

    public var body: some View {
        HStack {
            Spacer()
            Image(uiImage: .hideMoneyAsterisk)
                .resizable()
                .aspectRatio(contentMode: .fit)
                .frame(height: height)
                .foregroundColor(.primaryForeground30)
            if centered {
                Spacer()
            }
        }
    }
}
