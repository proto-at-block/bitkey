import Shared
import SwiftUI

struct TextInputView: View {

    // MARK: - Private Types

    private enum FocusedField {
        case text
    }

    // MARK: - Private Properties

    private let viewModel: FormMainContentModel.TextInput

    // MARK: - Life Cycle

    init(viewModel: FormMainContentModel.TextInput) {
        self.viewModel = viewModel
    }

    @FocusState
    private var focusedField: FocusedField?

    // MARK: - View

    var body: some View {
        VStack(spacing: 8) {
            if let title = viewModel.title {
                ModeledText(model: .standard(title, font: .title2))
            }

            TextFieldView(viewModel: viewModel.fieldModel)
                .focused($focusedField, equals: .text)
                .onAppear {
                    if viewModel.fieldModel.focusByDefault {
                        focusedField = .text
                    }
                }
        }
    }

}
