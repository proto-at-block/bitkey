import Shared
import SwiftUI

struct TextFieldView: View {

    // MARK: - Private Types

    private enum Metrics {
        static let height = 56.f
        static let cornerRadius = 32.f
    }

    // MARK: - Public Properties

    let viewModel: TextFieldModel
    var textContentType: UITextContentType? = nil

    // MARK: - View

    var body: some View {
        TextFieldViewRepresentable(viewModel: viewModel, textContentType: textContentType)
            .frame(height: Metrics.height)
            .background(
                RoundedRectangle(cornerRadius: Metrics.cornerRadius)
                    .fill(Color.foreground10)
            )
            .foregroundColor(.foreground)
            .tint(.foreground)
    }

}

// MARK: -

/**
 * We need to create a wrapper around `ModeledTextField` because the SwiftUI `TextField` doesn't work well with our KMP state
 * machine / view models controlling the text to display and receiving the onValueChange callback
 */
private struct TextFieldViewRepresentable: UIViewRepresentable {

    var viewModel: TextFieldModel
    var textContentType: UITextContentType? = nil

    func makeUIView(context _: UIViewRepresentableContext<TextFieldViewRepresentable>)
        -> ModeledTextField
    {
        return ModeledTextField(frame: .zero)
    }

    func updateUIView(
        _ uiView: ModeledTextField,
        context _: UIViewRepresentableContext<TextFieldViewRepresentable>
    ) {
        let capitalization = switch viewModel.capitalization {
        case .none: UITextAutocapitalizationType.none
        case .characters: UITextAutocapitalizationType.allCharacters
        case .words: UITextAutocapitalizationType.words
        case .sentences: UITextAutocapitalizationType.sentences
        default: UITextAutocapitalizationType.none
        }

        uiView.apply(
            model: .standard(
                placeholder: viewModel.placeholderText,
                text: viewModel.value,
                isSecureTextEntry: viewModel.masksText,
                keyboardType: viewModel.keyboardType.nativeModel,
                textContentType: textContentType ?? viewModel.keyboardType.textContentType,
                enableAutoCorrect: viewModel.enableAutoCorrect,
                capitalization: capitalization,
                maxLength: viewModel.maxLength?.intValue
            ),
            // The range is ignored on iOS side, so pass (0, 0)
            onEnteredTextChanged: { text in viewModel.onValueChange(
                text,
                .init(start: 0, endInclusive: 0)
            ) },
            onDone: viewModel.onDone
        )
    }

}
