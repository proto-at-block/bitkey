import Shared
import SwiftUI
import SVGView

// MARK: -

struct AsyncUrlImageView<T: View>: View {
    let url: URL
    let size: IconSize
    let opacity: Double

    @ViewBuilder
    let fallbackContent: () -> T

    var body: some View {
        if url.pathExtension.lowercased() == "svg" {
            SVGView(contentsOf: url)
                .frame(width: CGFloat(size.value.f), height: CGFloat(size.value.f))
                .opacity(opacity)
                .aspectRatio(contentMode: .fit)
        } else {
            AsyncImage(url: url) { phase in
                switch phase {
                case .success(let image):
                    image.resizable().opacity(opacity)
                case .empty:
                    RotatingLoadingIcon(size: size, tint: .black)
                default:
                    fallbackContent()
                }
            }
        }
    }
}

// MARK: -

struct AsyncUrlImageView_Previews: PreviewProvider {
    static var previews: some View {
        AsyncUrlImageView(
            url: URL(string: "https://upload.wikimedia.org/wikipedia/commons/c/c5/Square_Cash_app_logo.svg")!,
            size: .small,
            opacity: 0.5,
            fallbackContent: {
                Image(uiImage: .smallIconWarningFilled)
            }
        )
    }
}
