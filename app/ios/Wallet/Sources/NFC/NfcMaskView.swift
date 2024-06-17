import SwiftUI

// MARK: -

public struct NfcMaskView: View {

    @ObservedObject
    var viewModel: ViewModel

    public var body: some View {
        VStack {
            ZStack {
                Capsule()
                    .fill(Color.nfcBlue)
                    .blur(radius: 28)
                    .frame(height: 133)

                VStack(spacing: 6) {
                    Image(uiImage: .nfcTapIOS)
                        .padding(.top, 44)

                    ModeledText(
                        model: .standard(
                            viewModel.title,
                            font: .title1,
                            textAlignment: .center,
                            textColor: .translucentForeground
                        )
                    )

                    ModeledText(
                        model: .standard(
                            viewModel.subtitle,
                            font: .body2Regular,
                            textAlignment: .center,
                            textColor: .translucentForeground
                        )
                    )
                }
            }
            Spacer()
        }
        .background(LinearGradient(
            colors: [.black, .nfcBlue],
            startPoint: .top,
            endPoint: .bottom
        ))
    }

}

// MARK: -

public extension NfcMaskView {
    class ViewModel: ObservableObject {
        @Published
        var title: String

        @Published
        var subtitle: String

        init(title: String, subtitle: String) {
            self.title = title
            self.subtitle = subtitle
        }

        func update(from model: ViewModel) {
            self.title = model.title
            self.subtitle = model.subtitle
        }
    }
}
