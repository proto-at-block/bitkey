import Foundation
import Shared
import SwiftUI

// MARK: -

public struct TransferAmountView: View {

    // MARK: - Private Properties

    private let viewModel: TransferAmountBodyModel

    // MARK: - Life Cycle

    public init(viewModel: TransferAmountBodyModel) {
        self.viewModel = viewModel
    }

    // MARK: - View

    let springAnimation = Animation.spring(response: 0.1, dampingFraction: 0.8, blendDuration: 0)

    public var body: some View {
        VStack {
            ToolbarView(viewModel: viewModel.toolbar)
                .padding(.horizontal, DesignSystemMetrics.horizontalPadding)

            Spacer()

            AmountEntryView(
                viewModel: viewModel.amountModel,
                onSwapCurrencyClick: viewModel.onSwapCurrencyClick,
                disabled: viewModel.amountDisabled
            ).animation(springAnimation, value: viewModel.cardModel)

            if let cardModel = viewModel.cardModel {
                Spacer()
                    .frame(height: 24)
                SmartBarView(cardModel: cardModel)
                    .fixedSize(horizontal: true, vertical: true)
            }

            Spacer()

            KeypadView(viewModel: viewModel.keypadModel)
                .frame(maxWidth: .infinity, alignment: .center)

            Spacer()
                .frame(height: 24)

            ButtonView(model: viewModel.primaryButton)
                .padding(.horizontal, DesignSystemMetrics.horizontalPadding)
                .padding(.bottom, DesignSystemMetrics.verticalPadding)
        }.animation(springAnimation, value: viewModel.cardModel)
    }

}

// MARK: -

private struct BannerView: View {
    let viewModel: TransferScreenBannerModel
    var body: some View {
        switch viewModel {
        case is TransferScreenBannerModel.HardwareRequiredBannerModel:
            BannerContentView(
                backgroundColor: .foreground10,
                text: "Bitkey Device required",
                textColor: .foreground,
                leadingIcon: .smalliconbitkey
            )

        default:
            fatalError("Unexpected transfer screen banner")
        }
    }
}

private struct SmartBarView: View {
    let cardModel: CardModel

    var body: some View {
        CardView(
            viewModel: cardModel,
            withHeight: Binding.constant(48),
            withTitleSubtitleToIconSpacing: .constant(6.f)
        )
    }
}

private struct BannerContentView: View {
    let backgroundColor: Color
    let text: String
    let textColor: Color
    let leadingIcon: Icon?
    var body: some View {
        HStack(spacing: 4) {
            leadingIcon.map { Image(uiImage: $0.uiImage) }
            ModeledText(model: .standard(
                text,
                font: .body3Medium,
                textAlignment: nil,
                textColor: textColor
            ))
        }
        .padding(.horizontal, 16)
        .frame(minHeight: 44)
        .background(backgroundColor)
        .clipShape(Capsule())
    }
}
