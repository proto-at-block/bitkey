import Shared
import SwiftUI

struct TextAreaView: View {
    private enum FocusedField {
        case textArea
    }
    
    let viewModel: FormMainContentModelTextArea
    
    @FocusState
    private var focusedField: FocusedField?
    
    var body: some View {
        VStack(spacing: 8) {
            if let title = viewModel.title {
                ModeledText(model: .standard(title, font: .title2))
            }
            
            TextFieldViewRepresentable(viewModel: viewModel.fieldModel)
                .focused($focusedField, equals: .textArea)
                .background(
                    RoundedRectangle(cornerRadius: 32)
                        .fill(Color.foreground10)
                )
                .foregroundColor(.foreground)
                .tint(.foreground)
                .onAppear {
                    if (viewModel.fieldModel.focusByDefault) {
                        focusedField = .textArea
                    }
                }
        }
    }
}


/**
 * We need to create a wrapper around `ExpandableTextField`,
 *   because the SwiftUI `TextField` doesn't work well with our KMP state machine / view models
 *   controlling the text to display and receiving the onValueChange callback.
 */
private struct TextFieldViewRepresentable: UIViewRepresentable {
    var viewModel: TextFieldModel
    
    func makeUIView(context: UIViewRepresentableContext<TextFieldViewRepresentable>) -> ExpandableTextField {
    
        let uiView = ExpandableTextField(frame: .zero)
        // Manually constrain the width here or else the field will expand beyond it's bounds.
        // We constrain it to the bounds minus the horizontal padding on either side
        let width = UIScreen.main.bounds.width - (2 * DesignSystemMetrics.horizontalPadding)
        uiView.widthAnchor.constraint(equalToConstant: width).isActive = true
        return uiView
    }
    
    func updateUIView(_ uiView: ExpandableTextField, context: Context) {
        uiView.apply(
            model: .standardExpandable(
                placeholder: viewModel.placeholderText,
                font: .body2Regular,
                textLabelModel: .standard(
                    viewModel.value,
                    font: .body2Regular
                )
            ),
            onEnteredTextChanged: { text, selection in
                // The range is ignored on iOS side, so pass (0, 0)
                viewModel.onValueChange(text, .init(start: 0, endInclusive: 0))
            }
        )
    }
}
