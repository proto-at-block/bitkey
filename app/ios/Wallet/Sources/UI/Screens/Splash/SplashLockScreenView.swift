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
                .opacity(0.2)
                .frame(height: 25)
                .foregroundColor(.white)
            Spacer()
            Image(uiImage: .smallIconLock)
                .renderingMode(.template)
                .frame(height: 25)
                .foregroundColor(.white)
                .opacity(0.2)
            ModeledText(model: .standard("Locked", font: .body1Medium, textAlignment: .center, textColor: .white))
                .opacity(0.2)
            Spacer()
            ButtonView(model: viewModel.unlockButtonModel)
        }
        .frame(
          minWidth: 0,
          maxWidth: .infinity,
          minHeight: 0,
          maxHeight: .infinity,
          alignment: .center
        )
        .background(.black)
    }
}

