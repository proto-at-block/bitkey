import Foundation
import Shared
import SwiftUI

// MARK: -

public struct CustomAmountView: View {

    // MARK: - Private Properties

    private let viewModel: CustomAmountBodyModel

    // MARK: - Life Cycle

    public init(viewModel: CustomAmountBodyModel) {
        self.viewModel = viewModel
    }

    // MARK: - View

    let springAnimation = Animation.spring(response: 0.1, dampingFraction: 0.8, blendDuration: 0)

    public var body: some View {
        VStack {
            ToolbarView(viewModel: viewModel.toolbar)
                .padding(.horizontal, DesignSystemMetrics.horizontalPadding)

            Spacer()

            AmountView(viewModel: viewModel.amountModel)

            Spacer()

            KeypadView(viewModel: viewModel.keypadModel)
                .frame(maxWidth: .infinity, alignment: .center)

            Spacer()
                .frame(height: 24)

            ButtonView(model: viewModel.primaryButton)
                .padding(.horizontal, DesignSystemMetrics.horizontalPadding)
                .padding(.bottom, DesignSystemMetrics.verticalPadding)
        }
    }

}

// MARK: -

private struct AmountView: View {
    let viewModel: MoneyAmountEntryModel

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
