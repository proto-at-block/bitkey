import Foundation
import Shared
import SwiftUI

// MARK: -

public struct MoneyHomeBalanceView: View {

    // MARK: - Public Properties

    public var model: MoneyAmountModel

    // MARK: - View
    public var body: some View {
        VStack {
            HStack {
                Spacer()
                ModeledText(
                    model: .standard(model.primaryAmount, font: .display2, textAlignment: nil)
                )
                .numericTextAnimation(numericText: model.primaryAmount)
                Spacer()
            }

            ModeledText(
                model: .standard(model.secondaryAmount, font: .body1Medium, textAlignment: .center, textColor: .foreground60)
            )
        }
    }

}
