import Foundation
import Shared
import SwiftUI

// MARK: -

public struct BitkeyGetStartedView: View {

    // MARK: - Private Properties

    private let viewModel: BitkeyGetStartedModel

    // MARK: - Life Cycle

    public init(viewModel: BitkeyGetStartedModel) {
        self.viewModel = viewModel
    }

    // MARK: - View

    public var body: some View {
        ZStack {
            ZStack {
                Button {
                    viewModel.onLogoClick()
                } label: {
                    VStack(alignment: .center) {
                        Image(uiImage: .bitkeyFullLogo)
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .frame(width: 160)
                            .foregroundColor(Color.bitkeyGetStartedTint)
                        ModeledText(
                            model: .standard("Take Back Your Bitcoin", font: .title1, textAlignment: .none, textColor: Color.bitkeyGetStartedTint)
                        )
                    }
                }
                .accessibility(identifier: "logo")
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.bitkeyGetStartedBackground)

            VStack {
                Spacer()
                ButtonView(model: viewModel.getStartedButtonModel)
            }
            .padding(.horizontal, DesignSystemMetrics.horizontalPadding)
            .padding(.vertical, DesignSystemMetrics.verticalPadding)
        }
    }

}

// MARK: -

struct BitkeyGetStartedView_Preview: PreviewProvider {
    static var previews: some View {
        BitkeyGetStartedView(
            viewModel: .init(
                onLogoClick: {},
                onGetStartedClick: {}
            )
        )
    }
}
