import Foundation
import Shared
import SwiftUI

// MARK: -

struct FormFooterView: View {

    // MARK: - Private Properties

    private let viewModel: FormBodyModel

    // MARK: - Life Cycle

    init(viewModel: FormBodyModel) {
        self.viewModel = viewModel
    }

    // MARK: - View

    var body: some View {
        VStack(spacing: 16) {
            viewModel.ctaWarning.map { 
                ModeledText(
                    model: .standard(
                        $0,
                        font: .body4Regular,
                        textAlignment: .center,
                        textColor: .foreground60
                    )
                )
            }
            viewModel.primaryButton.map { ButtonView(model: $0) }
            viewModel.secondaryButton.map { ButtonView(model: $0) }
        }
    }

}
