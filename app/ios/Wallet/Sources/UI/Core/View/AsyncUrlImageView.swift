import Shared
import SVGView
import SwiftUI

// MARK: -

struct AsyncUrlImageView<T: View>: View {
    let url: URL
    let size: IconSize
    let opacity: Double

    @ViewBuilder
    let fallbackContent: () -> T

    @SwiftUI.State private var svgState: SvgImageState = .loading

    var body: some View {
        if url.pathExtension.lowercased() == "svg" {
            switch svgState {
            case let .loaded(svg):
                SVGView(svg: svg)
                    .frame(width: CGFloat(size.value.f), height: CGFloat(size.value.f))
                    .opacity(opacity)
                    .aspectRatio(contentMode: .fit)
            case .loading:
                RotatingLoadingIcon(size: size, tint: .black)
                    .task {
                        let (data, _, error) = await getUrlData(from: url)
                        if let data, let svg = SVGParser.parse(data: data) {
                            svgState = .loaded(svg: svg)
                        } else if let error {
                            svgState = .error
                        }
                    }
            case .error:
                fallbackContent()
            }
        } else {
            AsyncImage(url: url) { phase in
                switch phase {
                case let .success(image):
                    image.resizable().opacity(opacity)
                case .empty:
                    RotatingLoadingIcon(size: size, tint: .black)
                default:
                    fallbackContent()
                }
            }
        }
    }

    private func getUrlData(from url: URL) async -> (Data?, URLResponse?, Error?) {
        await withCheckedContinuation { continuation in
            URLSession.shared.dataTask(with: url, completionHandler: { data, response, error in
                continuation.resume(returning: (data, response, error))
            }).resume()
        }
    }

    private enum SvgImageState {
        case loaded(svg: SVGNode)
        case loading
        case error
    }
}

// MARK: -

struct AsyncUrlImageView_Previews: PreviewProvider {
    static var previews: some View {
        AsyncUrlImageView(
            url: URL(
                string: "https://upload.wikimedia.org/wikipedia/commons/c/c5/Square_Cash_app_logo.svg"
            )!,
            size: .small,
            opacity: 0.5,
            fallbackContent: {
                Image(uiImage: .smallIconWarningFilled)
            }
        )
    }
}
