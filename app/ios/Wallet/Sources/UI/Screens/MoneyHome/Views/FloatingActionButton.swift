import Foundation
import Shared
import SwiftUI

// MARK: -

/**
 * A button that is meant to sit overlaid on top of other content at the bottom of the screen.
 * Includes positioning to put it in the bottom right corner of the screen.
 */
public struct FloatingActionButton: View {

    // MARK: - Public Types

    public enum Metrics {
        static let width = 102.f
        static let height = 64.f

        static let shadowOpacity = 0.3
        static let shadowRadius = 24.f
        static let shadowYOffset = 4.f

        static let bottomPadding = 20.f
    }

    // MARK: - Public Properties

    public var model: FloatingActionButtonModel

    @Binding
    public var buttonWidth: CGFloat

    @Binding
    public var buttonImageXOffset: CGFloat

    @Binding
    public var buttonTextOpacity: CGFloat

    // MARK: - View

    public var body: some View {
        VStack {
            Spacer()
            HStack {
                Spacer()
                Button(action: model.onClick) {
                    HStack {
                        Image(uiImage: .smallIconScan)
                            .renderingMode(.template)
                            .resizable().frame(width: 20, height: 20)
                            .offset(x: buttonTextOpacity > 0 ? buttonImageXOffset : 0)
                        if buttonTextOpacity > 0 {
                            Text(model.text)
                                .font(FontTheme.body2Bold.font)
                                .opacity(buttonTextOpacity)
                        }
                    }
                    .frame(width: buttonWidth, height: Metrics.height)
                    .background(Color.primaryIcon)
                    .foregroundColor(.primaryForeground)
                    .clipShape(Capsule())
                }
                .shadow(
                    color: .black.opacity(Metrics.shadowOpacity),
                    radius: Metrics.shadowRadius,
                    y: Metrics.shadowYOffset
                )
            }
            .padding(.horizontal)
        }.padding(.bottom, Metrics.bottomPadding)
    }

}
