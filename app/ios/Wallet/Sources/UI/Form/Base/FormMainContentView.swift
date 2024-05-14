import Foundation
import Shared
import SwiftUI
import WebKit

// MARK: -

public struct FormMainContentView: View {

    // MARK: - Private Properties

    private let viewModel: FormMainContentModel

    // MARK: - Life Cycle

    init(viewModel: FormMainContentModel) {
        self.viewModel = viewModel
    }

    // MARK: - View

    public var body: some View {
        switch viewModel {
        case let mainContentModel as FormMainContentModelListGroup:
            ListGroupView(viewModel: mainContentModel.listGroupModel)

        case let mainContentModel as FormMainContentModelDataList:
            DataListView(viewModel: mainContentModel)

        case let mainContentModel as FormMainContentModelExplainer:
            ExplainerView(viewModel: mainContentModel)

        case let mainContentModel as FormMainContentModelFeeOptionList:
            FeeOptionListView(viewModel: mainContentModel)

        case let mainContentModel as FormMainContentModelVerificationCodeInput:
            VerificationCodeInputView(viewModel: mainContentModel)

        case let mainContentModel as FormMainContentModelTextInput:
            TextInputView(viewModel: mainContentModel)
            
        case let mainContentModel as FormMainContentModelTextArea:
            TextAreaView(viewModel: mainContentModel)

        case let mainContentModel as FormMainContentModelAddressInput:
            AddressInputView(viewModel: mainContentModel)
            
        case let mainContentModel as FormMainContentModelDatePicker:
            DatePickerView(viewModel: mainContentModel)
            
        case let mainContentModel as FormMainContentModelPicker:
            ItemPickerView(viewModel: mainContentModel)

        case let mainContentModel as FormMainContentModelTimer:
            TimerView(viewModel: mainContentModel)

        case let mainContentModel as FormMainContentModelSpacer:
            Spacer()
                .frame(height: mainContentModel.height.map { CGFloat($0.floatValue) })

        case let mainContentModel as FormMainContentModelWebView:
            AnyView(WebView(viewModel: mainContentModel))

        case let mainContentModel as FormMainContentModelButton:
            ButtonView(model: mainContentModel.item)

        case _ as FormMainContentModelLoader:
            RotatingLoadingIcon(size: .regular, tint: .black)

        case let mainContentModel as FormMainContentModelMoneyHomeHero:
            MoneyHomeHeroView(viewModel: mainContentModel)

        case let mainContentModel as FormMainContentModelStepperIndicator:
            StepperView(viewModel: mainContentModel)

        default:
            fatalError("Unexpected form main content model")
        }
    }
}
