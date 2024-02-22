import SwiftUI

import Foundation
import Shared
import SwiftUI

// MARK: -
struct ElectrumServerSettingsView: View {

    // MARK: - Public Properties
    public var viewModel: CustomElectrumServerBodyModel

    // MARK: - View
    public var body: some View {
        VStack {
            ToolbarView(
                viewModel: .init(
                    leadingAccessory: ToolbarAccessoryModel.IconAccessory.companion.BackAccessory(
                        onClick: viewModel.onBack
                    ),
                    middleAccessory: nil,
                    trailingAccessory: nil
                )
            )

            SwitchCardView(viewModel: viewModel.switchCardModel)

            Spacer()
        }
        .padding(.horizontal, DesignSystemMetrics.horizontalPadding)
        .alert(
            viewModel.disableAlertModel?.title ?? "",
            isPresented: .constant(viewModel.disableAlertModel != nil),
            presenting: viewModel.disableAlertModel,
            actions: { model in
                Button(model.primaryButtonText, role: .destructive, action: model.onPrimaryButtonClick)
                if let secondaryText = model.secondaryButtonText,
                   let secondaryAction = model.onSecondaryButtonClick {
                Button(secondaryText, role: .cancel, action: secondaryAction)
            }
            },
            message: { model in
                Text(model.subline ?? "")
            }
        )
    }

}

// MARK: -
struct ElectrumServerSettingsView_Previews: PreviewProvider {
    static var previews: some View {
        ElectrumServerSettingsView(
            viewModel: CustomElectrumServerBodyModel(
                onBack: {},
                switchIsChecked: true,
                electrumServerRow: .init(title: "Connected to:", sideText: "ssl://bitkey.mempool.space:50002", onClick: {}),
                onSwitchCheckedChange: { _ in },
                disableAlertModel: nil
            )
        )
        
        ElectrumServerSettingsView(
            viewModel: CustomElectrumServerBodyModel(
                onBack: {},
                switchIsChecked: false,
                electrumServerRow: nil,
                onSwitchCheckedChange: { _ in },
                disableAlertModel: nil
            )
        )
    }
}
