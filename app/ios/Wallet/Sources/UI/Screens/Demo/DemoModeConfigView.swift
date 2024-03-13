import Shared
import SwiftUI

// MARK: -

public struct DemoModeConfigView: View {

    // MARK: - Private Properties

    private let viewModel: DemoModeConfigBodyModel

    // MARK: - Life Cycle

    public init(viewModel: DemoModeConfigBodyModel) {
        self.viewModel = viewModel
    }

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
