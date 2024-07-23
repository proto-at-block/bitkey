import Shared
import SwiftUI

// MARK: -

struct SpendingLimitCardView: View {

    let viewModel: SpendingLimitCardModel

    var body: some View {
        VStack {
            HStack {
                ModeledText(model: .standard(viewModel.titleText, font: .title2))
                Spacer()
                ModeledText(
                    model: .standard(
                        viewModel.dailyResetTimezoneText,
                        font: .body4Regular,
                        textAlignment: .trailing,
                        textColor: .foreground60
                    )
                )
            }

            ProgressView(value: viewModel.progressPercentage)
                .frame(height: 8.0)
                .scaleEffect(x: 1, y: 2, anchor: .center)
                .clipShape(RoundedRectangle(cornerRadius: 4))
                .tint(.bitkeyPrimary)

            Spacer()
                .frame(height: 10)

            HStack {
                ModeledText(
                    model: .standard(
                        viewModel.spentAmountText,
                        font: .body4Regular,
                        textColor: .foreground60
                    )
                )
                Spacer()
                ModeledText(
                    model: .standard(
                        viewModel.remainingAmountText,
                        font: .body4Medium,
                        textAlignment: .trailing,
                        textColor: .foreground60
                    )
                )
            }
        }
        .padding(.horizontal, DesignSystemMetrics.horizontalPadding)
        .padding(.vertical, DesignSystemMetrics.verticalPadding)
        .overlay(
            RoundedRectangle(cornerRadius: 24)
                .stroke(Color.foreground10, lineWidth: 2)
        )
    }

}
