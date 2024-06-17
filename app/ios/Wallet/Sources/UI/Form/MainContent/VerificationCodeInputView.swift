import Foundation
import Shared
import SwiftUI

// MARK: -

struct VerificationCodeInputView: View {

    // MARK: - Private Types

    private enum FocusedField {
        case verificationCode
    }

    // MARK: - Private Properties

    private let viewModel: FormMainContentModel.VerificationCodeInput

    // MARK: - Life Cycle

    init(viewModel: FormMainContentModel.VerificationCodeInput) {
        self.viewModel = viewModel
    }

    @FocusState
    private var focusedField: FocusedField?

    // MARK: - View

    var body: some View {
        VStack(alignment: .leading, spacing: 24) {
            TextFieldView(viewModel: viewModel.fieldModel, textContentType: .oneTimeCode)
                .focused($focusedField, equals: .verificationCode)
                .onAppear {
                    focusedField = .verificationCode
                }

            VerificationCodeInputResendCodeView(viewModel: viewModel.resendCodeContent)

            VerificationCodeInputSkipForNowView(viewModel: viewModel.skipForNowContent)
        }
    }

}

// MARK: -

private struct VerificationCodeInputResendCodeView: View {
    let viewModel: FormMainContentModelVerificationCodeInputResendCodeContent
    var body: some View {
        HStack {
            switch viewModel {
            case let text as FormMainContentModelVerificationCodeInputResendCodeContentText:
                ModeledText(model: .standard(
                    text.value,
                    font: .body3Regular,
                    textColor: .foreground60
                ))
            case let button as FormMainContentModelVerificationCodeInputResendCodeContentButton:
                ButtonView(model: button.value)
            default:
                fatalError("Unexpected resend code content")
            }

            Spacer()
        }
    }
}

// MARK: -

private struct VerificationCodeInputSkipForNowView: View {
    let viewModel: FormMainContentModelVerificationCodeInputSkipForNowContent
    var body: some View {
        switch viewModel {
        case _ as FormMainContentModelVerificationCodeInputSkipForNowContentHidden:
            EmptyView()
        case let showing as FormMainContentModelVerificationCodeInputSkipForNowContentShowing:
            HStack(alignment: .center, spacing: 8) {
                ModeledText(model: .standard(showing.text, font: .body3Regular, textAlignment: nil))
                ButtonView(model: showing.button)
                Spacer()
            }
        default:
            fatalError("Unexpected skip for now content")
        }
    }
}
