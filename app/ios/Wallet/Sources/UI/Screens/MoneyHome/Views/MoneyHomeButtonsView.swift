import Foundation
import Shared
import SwiftUI

// MARK: -

public struct MoneyHomeButtonsView: View {

    // MARK: - Public Properties

    public var viewModel: MoneyHomeButtonsModel

    // MARK: - View

    public var body: some View {
        switch viewModel {
        case let buttonsModel as MoneyHomeButtonsModelMoneyMovementButtonsModel:
            let buttonCount = buttonsModel.buttons.count
            let spacing: Double = buttonCount > 3 ? 16 : 32

            // Adaptive layout that wraps the buttons to a second row on smaller screens
            // 60 is a bit of a magic number here, but it's the best default for our supported
            // devices
            let columns = [
                GridItem(.adaptive(minimum: 60), spacing: spacing),
            ]

            LazyVGrid(columns: columns, alignment: .center, spacing: spacing) {
                ForEach(buttonsModel.buttons, id: \.self) { model in
                    IconButtonView(model: model)
                }
            }
            .frame(maxWidth: .infinity, alignment: .center)
            .padding(.top, 16)
            .padding(.bottom, 40)
            .padding(.horizontal, spacing)

        case let model as MoneyHomeButtonsModelSingleButtonModel:
            ButtonView(model: model.button)
                .padding(.vertical, 40)

        default:
            fatalError("Unexpected MoneyHomeButtonsModel case")
        }
    }

}

struct MoneyHomeButtons_Preview: PreviewProvider {
    static var previews: some View {
        VStack {
            MoneyHomeButtonsView(
                viewModel: MoneyHomeButtonsModelMoneyMovementButtonsModel(
                    addButton: .init(enabled: false, onClick: {}),
                    sellButton: nil,
                    sendButton: .init(enabled: true, onClick: {}),
                    receiveButton: .init(enabled: true, onClick: {})
                )
            )

            MoneyHomeButtonsView(
                viewModel: MoneyHomeButtonsModelMoneyMovementButtonsModel(
                    addButton: .init(enabled: false, onClick: {}),
                    sellButton: .init(enabled: false, onClick: {}),
                    sendButton: .init(enabled: true, onClick: {}),
                    receiveButton: .init(enabled: true, onClick: {})
                )
            )
        }
    }
}
