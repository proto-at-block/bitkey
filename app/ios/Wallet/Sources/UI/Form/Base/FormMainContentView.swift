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
        case let mainContentModel as FormMainContentModel.ListGroup:
            ListGroupView(viewModel: mainContentModel.listGroupModel)

        case let mainContentModel as FormMainContentModel.DataList:
            DataListView(viewModel: mainContentModel)

        case let mainContentModel as FormMainContentModel.Explainer:
            ExplainerView(viewModel: mainContentModel)

        case let mainContentModel as FormMainContentModel.FeeOptionList:
            FeeOptionListView(viewModel: mainContentModel)

        case let mainContentModel as FormMainContentModel.VerificationCodeInput:
            VerificationCodeInputView(viewModel: mainContentModel)

        case let mainContentModel as FormMainContentModel.TextInput:
            TextInputView(viewModel: mainContentModel)

        case let mainContentModel as FormMainContentModel.TextArea:
            TextAreaView(viewModel: mainContentModel)

        case let mainContentModel as FormMainContentModel.AddressInput:
            AddressInputView(viewModel: mainContentModel)

        case let mainContentModel as FormMainContentModel.DatePicker:
            DatePickerView(viewModel: mainContentModel)

        case let mainContentModel as FormMainContentModel.Picker:
            ItemPickerView(viewModel: mainContentModel)

        case let mainContentModel as FormMainContentModel.Timer:
            TimerView(viewModel: mainContentModel)

        case let mainContentModel as FormMainContentModel.Spacer:
            Spacer()
                .frame(height: mainContentModel.height.map { CGFloat($0.floatValue) })

        case let mainContentModel as FormMainContentModel.WebView:
            AnyView(WebView(viewModel: mainContentModel))

        case let mainContentModel as FormMainContentModel.Button:
            ButtonView(model: mainContentModel.item)

        case _ as FormMainContentModel.Loader:
            RotatingLoadingIcon(size: .Regular(), tint: .black)

        case let mainContentModel as FormMainContentModel.MoneyHomeHero:
            MoneyHomeHeroView(viewModel: mainContentModel)

        case let calloutModel as FormMainContentModel.Callout:
            CalloutView(model: calloutModel.item)

        case let showcaseModel as FormMainContentModel.Showcase:
            ShowcaseView(model: showcaseModel)

        case let dividerModel as FormMainContentModel.Divider:
            Divider()
                .frame(height: 1)
                .overlay(Color.foreground10)

        default:
            fatalError("Unexpected form main content model")
        }
    }
}
