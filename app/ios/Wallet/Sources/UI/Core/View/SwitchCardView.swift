import Shared
import SwiftUI

// MARK: -

struct SwitchCardView: View {

    // MARK: - Public Properties

    public var viewModel: SwitchCardModel

    // MARK: - Life Cycle

    init(viewModel: SwitchCardModel) {
        self.viewModel = viewModel
    }

    // MARK: - View

    public var body: some View {
        VStack {
            VStack {
                Toggle(
                    "",
                    isOn: .init(
                        get: { viewModel.switchModel.checked },
                        set: { viewModel.switchModel.onCheckedChange(.init(bool: $0)) }
                    )
                )
                .labelsHidden()
                .tint(.primary)

                Spacer()
                    .frame(height: 16)

                ModeledText(model: .standard(viewModel.title, font: .title2, textAlignment: .center))
                Spacer()
                    .frame(height: 8)
                ModeledText(model: .standard(viewModel.subline, font: .body3Regular, textAlignment: .center, textColor: .foreground60))
            }
            .padding(.vertical, 32)

            ForEach(viewModel.actionRows, id: \.title) { actionRow in
                ActionRowView(viewModel: actionRow)
            }
        }
        .padding(.horizontal, DesignSystemMetrics.horizontalPadding)
        .padding(.vertical, 16)
        .overlay(
            RoundedRectangle(cornerRadius: 24)
                .stroke(Color.foreground10, lineWidth: 2)
        )
    }

}

// MARK: -

private struct ActionRowView: View {

    let viewModel: SwitchCardModel.ActionRow

    var body: some View {
        VStack {
            Divider()
            Spacer()
                .frame(height: 16)
            Button(action: viewModel.onClick) {
                HStack {
                    ModeledText(
                        model: .standard(viewModel.title, font: .body2Regular, textAlignment: nil)
                    )
                    Spacer()
                    ModeledText(
                        model: .standard(
                            viewModel.sideText,
                            font: .body2Regular,
                            textAlignment: .trailing,
                            textColor: .foreground60
                        )
                    )
                    Image(uiImage: .smallIconCaretRight)
                        .foregroundColor(.foreground30)
                }
            }
        }
    }

}
