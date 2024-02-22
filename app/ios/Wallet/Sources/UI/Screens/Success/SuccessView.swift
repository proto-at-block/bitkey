import Foundation
import Lottie
import Shared
import SwiftUI

// MARK: -

public struct SuccessView: View {

    // MARK: - Private Properties

    private let viewModel: SuccessBodyModel

    // MARK: - Life Cycle

    public init(viewModel: SuccessBodyModel) {
        self.viewModel = viewModel
    }

    // MARK: - View

    public var body: some View {
        switch viewModel.style {
        case let explicit as SuccessBodyModelStyleExplicit:
            FormView(viewModel: viewModel.formBodyModel(explicitStyle: explicit, id: viewModel.id))

        case _ as SuccessBodyModelStyleImplicit:
            ImplicitSuccessView(viewModel: viewModel)

        default:
            fatalError("Unexpected success screen style case")
        }
    }
}

// MARK: -

extension SuccessBodyModel {
    func formBodyModel(explicitStyle: SuccessBodyModelStyleExplicit, id: EventTrackerScreenId?) -> FormBodyModel {
        return .init(
            id: id,
            onBack: {}, 
            onSwipeToDismiss: nil,
            toolbar: nil,
            header: .init(
                icon: .largeiconcheckfilled,
                headline: title,
                subline: message,
                sublineTreatment: .regular,
                alignment: .leading
            ),
            mainContentList: [],
            primaryButton: .init(
                text: explicitStyle.primaryButton.text,
                isEnabled: true,
                isLoading: false,
                leadingIcon: nil,
                treatment: .primary,
                size: .footer,
                testTag: nil,
                onClick: ClickCompanion().standardClick {
                    explicitStyle.primaryButton.onClick()
                }
            ),
            secondaryButton: nil,
            ctaWarning: nil,
            keepScreenOn: false,
            renderContext: RenderContext.screen,
            onLoaded: { _ in },
            eventTrackerScreenIdContext: nil,
            eventTrackerShouldTrack: true
        )
    }
}

// MARK: -

struct ImplicitSuccessView: View {
    let viewModel: SuccessBodyModel
    var body: some View {
        VStack(alignment: .center, spacing: 16) {
            Spacer()
            AnimatedCheckmarkView()
            ModeledText(model: .standard(viewModel.title, font: .title1, textAlignment: .center))
            viewModel.message.map {
                ModeledText(model: .standard($0, font: .body1Regular, textAlignment: .center))
            }
            Spacer()
        }
    }
}

// MARK: -

struct AnimatedCheckmarkView: View {
    var body: some View {
        ZStack {
            Circle()
                .foregroundColor(.primary)
            LottieView(animation: .success)
                .playing(loopMode: .playOnce)
                .aspectRatio(contentMode: .fill)
        }
        .frame(width: 64, height: 64)
    }
}
