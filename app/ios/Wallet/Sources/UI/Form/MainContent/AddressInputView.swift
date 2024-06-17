import Shared
import SwiftUI

struct AddressInputView: View {

    // MARK: - Private Types

    private enum FocusedField {
        case address
    }

    // MARK: - Private Properties

    private let viewModel: FormMainContentModel.AddressInput

    // MARK: - Life Cycle

    init(viewModel: FormMainContentModel.AddressInput) {
        self.viewModel = viewModel
    }

    @FocusState
    private var focusedField: FocusedField?

    // MARK: - View

    var body: some View {
        TextFieldViewRepresentable(
            viewModel: viewModel.fieldModel,
            trailingButtonModel: viewModel.trailingButtonModel
        )
        .focused($focusedField, equals: .address)
        .background(
            RoundedRectangle(cornerRadius: 32)
                .fill(Color.foreground10)
        )
        .foregroundColor(.foreground)
        .tint(.foreground)
        .onAppear {
            focusedField = .address
        }
    }

}

// MARK: -

/**
 * We need to create a wrapper around `ExpandableTextField` because the SwiftUI `TextField` doesn't work well with our KMP state
 * machine / view models controlling the text to display and receiving the onValueChange callback
 */
private struct TextFieldViewRepresentable: UIViewRepresentable {

    var viewModel: TextFieldModel
    var trailingButtonModel: ButtonModel?

    func makeUIView(context _: UIViewRepresentableContext<TextFieldViewRepresentable>)
        -> ExpandableTextField
    {
        let uiView = ExpandableTextField(frame: .zero)
        // Manually constrain the width here or else the field will expand beyond it's bounds.
        // We constrain it to the bounds minus the horizontal padding on either side
        let width = UIScreen.main.bounds.width - (2 * DesignSystemMetrics.horizontalPadding)
        uiView.widthAnchor.constraint(equalToConstant: width).isActive = true
        return uiView
    }

    func updateUIView(
        _ uiView: ExpandableTextField,
        context _: UIViewRepresentableContext<TextFieldViewRepresentable>
    ) {
        uiView.apply(
            model: .standardExpandable(
                placeholder: viewModel.placeholderText,
                font: .body2Mono,
                textLabelModel: .standard(
                    viewModel.value,
                    font: .body2Mono
                ),
                trailingButtonModel: trailingButtonModel.map { trailingButtonModel in
                    .makeButton(
                        backgroundColor: .secondary,
                        title: trailingButtonModel.text,
                        titleColor: .secondaryForeground,
                        titleFont: .label3,
                        height: 32,
                        icon: trailingButtonModel.leadingIcon?.uiImage,
                        action: trailingButtonModel.onClick.invoke
                    )
                }
            ),
            // The range is ignored on iOS side, so pass (0, 0)
            onEnteredTextChanged: { text, _ in viewModel.onValueChange(
                text,
                .init(start: 0, endInclusive: 0)
            ) }
        )
    }

}
