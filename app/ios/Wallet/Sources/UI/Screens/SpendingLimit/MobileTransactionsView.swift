import Foundation
import Shared
import SwiftUI

// MARK: -

struct MobileTransactionsView: View {

    // MARK: - Public Properties

    public var viewModel: MobilePayStatusModel

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
                .frame(height: 32)

            viewModel.spendingLimitCardModel.map { SpendingLimitCardView(viewModel: $0) }

            Spacer()
        }
        .padding(.horizontal, DesignSystemMetrics.horizontalPadding)
        .alert(
            viewModel.disableAlertModel?.title ?? "",
            isPresented: .constant(viewModel.disableAlertModel != nil),
            presenting: viewModel.disableAlertModel,
            actions: { model in
                Button(
                    model.primaryButtonText,
                    role: .destructive,
                    action: model.onPrimaryButtonClick
                )
                if let secondaryText = model.secondaryButtonText,
                   let secondaryAction = model.onSecondaryButtonClick
                {
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

struct MobileTransactionsView_Previews: PreviewProvider {
    static var previews: some View {
        MobileTransactionsView(
            viewModel: MobilePayStatusModel(
                onBack: {},
                switchIsChecked: true,
                onSwitchCheckedChange: { _ in },
                dailyLimitRow: .init(title: "Daily limit", sideText: "$100.00", onClick: {}),
                disableAlertModel: nil,
                spendingLimitCardModel: .init(
                    titleText: "Today’s limit",
                    dailyResetTimezoneText: "Resets at 3:00am",
                    spentAmountText: "$40 spent",
                    remainingAmountText: "$60 remaining",
                    progressPercentage: 0.5
                )
            )
        )
    }
}
