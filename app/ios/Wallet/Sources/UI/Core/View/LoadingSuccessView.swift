import Foundation
import Lottie
import Shared
import SwiftUI

// MARK: -

class LoadingSuccessViewModel: ObservableObject {
    @Published
    var bodyModel: LoadingSuccessBodyModel

    init(bodyModel: LoadingSuccessBodyModel) {
        self.bodyModel = bodyModel
    }

    func updateModel(bodyModel: LoadingSuccessBodyModel) {
        self.bodyModel = bodyModel
    }
}

// MARK: -

struct LoadingSuccessView: View {

    @SwiftUI.ObservedObject
    var viewModel: LoadingSuccessViewModel

    @SwiftUI.State
    private var progress: AnimationProgressTime = 0.f

    init(viewModel: LoadingSuccessBodyModel) {
        self.viewModel = .init(bodyModel: viewModel)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Spacer()
                .frame(height: DesignSystemMetrics.toolbarHeight)

            switch viewModel.bodyModel.state {
            case _ as LoadingSuccessBodyModelStateLoading:
                // The `loadingAndSuccess` animation shows both loading and success, and here we just want it
                // to loop the loading portion (which is from progress 0 to 0.3)
                // In the below `LoadingSuccessBodyModelStateSuccess` case we will let the rest of the animation play
                LottieView(animation: .loadingAndSuccess)
                    .playing(.fromProgress(0, toProgress: 0.3, loopMode: .loop))
                    .getRealtimeAnimationProgress($progress)
                    .frame(iconSize: .avatar)

            case _ as LoadingSuccessBodyModelStateSuccess:
                // The `loadingAndSuccess` animation shows both loading and success, and here
                // we want it to play out the rest of the animation, starting with where the loading in
                // the above `LoadingSuccessBodyModelStateLoading` case left off
                LottieView(animation: .loadingAndSuccess)
                    .playing(.fromProgress(progress, toProgress: 1, loopMode: .playOnce))
                    .frame(iconSize: .avatar)

            default:
                EmptyView()
            }

            // Explicitly show text here, even if the message is null and state is loading to make sure it aligns
            ModeledText(model: .standard(viewModel.bodyModel.message ?? " ", font: .title1))

            Spacer()
        }
        .padding(.horizontal, DesignSystemMetrics.horizontalPadding)
    }
}
