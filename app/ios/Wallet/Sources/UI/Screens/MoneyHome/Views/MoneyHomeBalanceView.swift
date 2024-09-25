import Foundation
import Shared
import SwiftUI

// MARK: -

public struct MoneyHomeBalanceView: View {

    // MARK: - Public Properties

    public var model: MoneyAmountModel
    public let hideBalance: Bool
    public let onHideBalance: () -> Void

    // MARK: - View

    public var body: some View {
        CollapsibleLabelContainer(
            collapsed: hideBalance,
            topContent: HStack {
                Spacer()
                ModeledText(
                    model: .standard(
                        model.primaryAmount,
                        font: .display2,
                        textAlignment: nil,
                        scalingFactor: UIFontTheme.display3.font.pointSize / UIFontTheme.display2
                            .font.pointSize
                    )
                )
                .numericTextAnimation(numericText: model.primaryAmount)
                Spacer()
            },
            bottomContent: ModeledText(
                model: .standard(
                    model.secondaryAmount,
                    font: .body1Medium,
                    textAlignment: .center,
                    textColor: .foreground60
                )
            ),
            collapsedContent: CollapsedMoneyView(height: 36)
        ).onTapGesture(perform: onHideBalance)
    }

}
