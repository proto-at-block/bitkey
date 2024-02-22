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
            HStack(spacing: 40) {
                ForEach(buttonsModel.buttons, id: \.self) { model in
                    IconButtonView(model: model)
                }
            }
            .padding(.top, 16)
            .padding(.bottom, 40)

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
        MoneyHomeButtonsView(
            viewModel: MoneyHomeButtonsModelMoneyMovementButtonsModel(
                sendButton: .init(enabled: true, onClick: {}),
                receiveButton: .init(enabled: true, onClick: {}),
                addButton: .init(enabled: false, onClick: {})
            )
        )
    }
}
