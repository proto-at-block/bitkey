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
            viewModel.ctaWarning.map { CallToActionView(viewModel: $0) }
            viewModel.primaryButton.map { ButtonView(model: $0) }
            viewModel.secondaryButton.map { ButtonView(model: $0) }
            viewModel.tertiaryButton.map { ButtonView(model: $0) }
        }
    }

    
    struct CallToActionView: View {
        // MARK: - Private Properties

        private let viewModel: CallToActionModel

        // MARK: - Life Cycle

        init(viewModel: CallToActionModel) {
            self.viewModel = viewModel
        }
        
        var body: some View {
            let textColor: Color = switch viewModel.treatment {
            case .secondary: .foreground60
            case .warning: .warningForeground
            default: .foreground60
            }
            
            
            ModeledText(
                model: .standard(
                    viewModel.text,
                    font: .body4Regular,
                    textAlignment: .center,
                    textColor: textColor
                )
            )
        }
    }
}
