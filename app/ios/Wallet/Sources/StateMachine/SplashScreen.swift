import Shared
import SwiftUI

/// Corresponds to the SplashScreen storyboard to use as a seamless transition.
struct SplashScreenView: View {

    // MARK: - Private Properties

    @SwiftUI.State
    private var isBitkeyWordVisible = false

    private let viewModel: SplashBodyModel

    private var bitkeyWordAnimation: Animation {
        return Animation
            .spring(duration: viewModel.bitkeyWordMarkAnimationDurationInSeconds)
            .delay(viewModel.bitkeyWordMarkAnimationDelayInSeconds)
    }

    // MARK: - Life Cycle

    init(viewModel: SplashBodyModel) {
        self.viewModel = viewModel
    }

    // MARK: - View

    var body: some View {
        GeometryReader { reader in
            ZStack {
                HStack(spacing: 8) {
                    Image(uiImage: .bitkeyLogoMark)
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .frame(height: 38)
                        .foregroundColor(.white)

                    if viewModel
                        .bitkeyWordMarkAnimationDurationInSeconds == 0 || isBitkeyWordVisible
                    {
                        Image(uiImage: .bitkeyWordMark)
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .frame(height: 38)
                            .foregroundColor(.white)
                            .padding(.top, 10)
                            .transition(.offset(x: 36).combined(with: .opacity))
                    }
                }
                .frame(alignment: .center)
            }
            .frame(width: reader.size.width, height: reader.size.height)
            .background(.black)
        }
        .ignoresSafeArea()
        .onAppear {
            withAnimation(bitkeyWordAnimation) {
                isBitkeyWordVisible = true
            }
        }
    }

}
