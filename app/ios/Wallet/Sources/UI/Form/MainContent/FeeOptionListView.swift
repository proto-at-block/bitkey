import Foundation
import Shared
import SwiftUI

// MARK: -

struct FeeOptionListView: View {

    // MARK: - Private Properties

    private let viewModel: FormMainContentModelFeeOptionList

    // MARK: - Life Cycle

    init(viewModel: FormMainContentModelFeeOptionList) {
        self.viewModel = viewModel
    }

    // MARK: - View

    var body: some View {
        VStack(spacing: 24) {
            ForEach(viewModel.options, id: \.optionName) { item in
                FeeOptionCardView(viewModel: item)
            }
        }
    }

}

// MARK: -

private struct FeeOptionCardView: View {

    let viewModel: FormMainContentModelFeeOptionList.FeeOption

    var body: some View {
        VStack(spacing: 0) {
            ListItemView(
                viewModel: .init(
                    title: viewModel.optionName,
                    titleAlignment: .left, 
                    listItemTitleBackgroundTreatment: nil,
                    secondaryText: nil,
                    sideText: viewModel.transactionTime,
                    secondarySideText: viewModel.transactionFee,
                    leadingAccessoryAlignment: .center, leadingAccessory: nil,
                    trailingAccessory: nil,
                    treatment: .primary,
                    sideTextTint: .primary,
                    enabled: viewModel.enabled,
                    selected: false,
                    onClick: viewModel.onClick,
                    pickerMenu: nil,
                    testTag: nil,
                    titleLabel: nil
                )
            )

            if let infoText = viewModel.infoText {
                InfoTextView(text: infoText)
            }
        }
        .padding(.horizontal, DesignSystemMetrics.horizontalPadding)
        .padding(.top, 4)
        .padding(.bottom, viewModel.infoText == nil ? 4 : 16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(
                    viewModel.selected && viewModel.enabled
                    ? Color.foreground
                    : .foreground10,
                    lineWidth: 2
                )
        )
    }

}

// MARK: -

private struct InfoTextView: View {
    
    let text: String

    var body: some View {
        ModeledText(model: .standard(text, font: .body3Regular, textAlignment: .center, textColor: .foreground60))
            .frame(maxWidth: .infinity, alignment: .center)
            .padding(8)
            .background(Color.foreground10)
            .cornerRadius(12)
    }
    
}

// MARK: -

struct FeeOptionListView_Previews: PreviewProvider {
    static var previews: some View {
        FeeOptionListView(
            viewModel: .init(
                options: [
                    .init(
                        optionName: "Priority",
                        transactionTime: "~10 min",
                        transactionFee: "$1.36 (4,475 sats)",
                        selected: false,
                        enabled: false,
                        infoText: ""
                    ),
                    .init(
                        optionName: "Standard",
                        transactionTime: "~30 min",
                        transactionFee: "$0.33 (1,086 sats)",
                        selected: true,
                        enabled: true,
                        infoText: ""
                    ),
                    .init(
                        optionName: "Slow",
                        transactionTime: "~1 hour",
                        transactionFee: "$1.95 (494 sats)",
                        selected: false,
                        enabled: true,
                        infoText: ""
                    )
                ]
            )
        )
    }
}
