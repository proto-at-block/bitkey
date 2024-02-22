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
            ModeledText(
                model: .standard(model.primaryAmount, font: .display2, textAlignment: .center)
            )
            ModeledText(
                model: .standard(model.secondaryAmount, font: .body1Medium, textAlignment: .center, textColor: .foreground60)
            )
        }
    }

}
