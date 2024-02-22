import Shared
import SwiftUI

// MARK: -

public struct ButtonView: View {

    // MARK: - Private Properties

    private let model: ButtonModel
    private let cornerRadius: Int
    private let horizontalPadding: CGFloat?

    // MARK: - Life Cycle

    init(
        model: ButtonModel,
        cornerRadius: Int = 16,
        horizontalPadding: CGFloat? = nil
    ) {
        self.model = model
        self.cornerRadius = cornerRadius
        self.horizontalPadding = horizontalPadding
    }

    // MARK: - View

    public var body: some View {
        switch model.treatment {
        case .tertiary, .tertiarydestructive, .tertiaryprimary, .tertiarynounderline, .tertiaryprimarynounderline:
            Button(action: model.onClick.invoke) {
                ButtonContentView(model: model)
                    .frame(size: model.size)
            }

        default:
            Button(action: model.onClick.invoke) {
                ButtonContentView(model: model)
                    .frame(size: model.size)
                    .padding(.horizontal, horizontalPadding)
            }
            .background(model.backgroundColor)
            .clipShape(RoundedCorners(radius: cornerRadius, corners: .allCorners))
            .disabled(!model.isEnabled)
        }
    }

}

// MARK: -

private struct ButtonContentView: View {

    let model: ButtonModel

    public var body: some View {
        HStack {
            if model.isLoading {
                RotatingLoadingIcon(size: .small, tint: model.loadingTint)
            } else {
                model.leadingIcon.map {
                    Image(uiImage: $0.uiImage)
                        .renderingMode(.template)
                        .resizable()
                        .frame(iconSize: model.treatment.leadingIconSize)
                }
                Text(model.text)
                    .font(FontTheme.label1.font)
                    .if(model.treatment == .tertiary || model.treatment == .tertiaryprimary) { text in
                        text.underline()
                    }
            }
        }
        .foregroundColor(model.isEnabled ? model.titleColor : .foreground30)
        .ifNonnull(model.testTag) { view, testTag in
            view.accessibilityIdentifier(testTag)
         }
    }

}

// MARK: -

public extension View {
    @ViewBuilder
    func frame(size: ButtonModel.Size) -> some View {
        switch size {
        case .footer:
            frame(height: DesignSystemMetrics.Button.height)
                .frame(maxWidth: .infinity)

        case .floating, .regular:
            frame(width: nil, height: DesignSystemMetrics.Button.height)

        case .compact:
            frame(height: 32)

        default:
            fatalError("Unexpected button size")
        }
    }
}
