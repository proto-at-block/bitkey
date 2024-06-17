import Shared
import SwiftUI

// MARK: -

struct SplashLockScreenView: View {

    private let viewModel: SplashLockModel

    init(viewModel: SplashLockModel) {
        self.viewModel = viewModel
    }

    var body: some View {
        VStack(alignment: .center) {
            Image(uiImage: .bitkeyFullLogo)
                .resizable()
                .aspectRatio(contentMode: .fit)
                .opacity(0.5)
                .frame(height: 25)
                .foregroundColor(.white)
            Spacer()
            Image(uiImage: .smallIconLock)
                .resizable()
                .renderingMode(.template)
                .frame(width: 48, height: 48)
                .foregroundColor(.white)
                .opacity(0.5)
            ModeledText(model: .standard(
                "Locked",
                font: .body1Medium,
                textAlignment: .center,
                textColor: .white
            ))
            .opacity(0.5)
            Spacer()
            ButtonView(model: viewModel.unlockButtonModel)
                .padding(EdgeInsets(top: 0, leading: 4, bottom: 0, trailing: 4))
        }
        .frame(
            minWidth: 0,
            maxWidth: .infinity,
            minHeight: 0,
            maxHeight: .infinity,
            alignment: .center
        )
        .padding(EdgeInsets(top: 20, leading: 20, bottom: 20, trailing: 20))
        .background(.black)
    }
}
