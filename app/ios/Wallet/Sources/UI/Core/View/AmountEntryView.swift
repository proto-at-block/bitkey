import Shared
import SwiftUI

// MARK: -

struct AmountEntryView: View {
    let viewModel: MoneyAmountEntryModel
    let onSwapCurrencyClick: (() -> Void)?
    let disabled: Bool

    init(
        viewModel: MoneyAmountEntryModel,
        onSwapCurrencyClick: (() -> Void)? = nil,
        disabled: Bool
    ) {
        self.viewModel = viewModel
        self.onSwapCurrencyClick = onSwapCurrencyClick
        self.disabled = disabled
    }

    var body: some View {
        VStack(spacing: 8) {
            // Primary amount
            Text(primaryAmountAttributedString)
                .font(FontTheme.display1.font)
                .fixedSize(horizontal: false, vertical: true)
                .lineLimit(1)
                .allowsTightening(true)
                .minimumScaleFactor(0.5)
                .padding(.horizontal, 20)
                .if(disabled) { view in
                    view.foregroundColor(.foreground30)
                }

            if let secondaryAmount = viewModel.secondaryAmount {
                // Secondary amount
                // We should wrap it in a button if it's actually actionable. Else, we just use the
                // HStack.
                let content = HStack {
                    ModeledText(
                        model: .standard(
                            secondaryAmount,
                            font: .body1Medium,
                            textAlignment: nil,
                            textColor: disabled ? .foreground30 : .foreground60
                        )
                    )
                }

                if let onSwapCurrencyClick {
                    Button(action: onSwapCurrencyClick) {
                        content
                        Image(uiImage: .smallIconSwap)
                            .foregroundColor(disabled ? .foreground30 : .foreground60)
                    }
                } else {
                    content
                }
            }
        }
    }

    private var primaryAmountAttributedString: AttributedString {
        var attributedString = AttributedString(viewModel.primaryAmount)
        guard let primaryAmountGhostedSubstringRange = viewModel.primaryAmountGhostedSubstringRange
        else {
            return attributedString
        }
        let substringStart = attributedString.index(to: primaryAmountGhostedSubstringRange.start)
        let substringEnd = attributedString
            .index(to: primaryAmountGhostedSubstringRange.endInclusive)
        attributedString[substringStart ... substringEnd].foregroundColor = .foreground30
        return attributedString
    }
}
