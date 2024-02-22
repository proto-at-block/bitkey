import Shared
import SwiftUI

// MARK: -

struct AsyncUrlImageView: View {
    let url: URL
    // in the event of an error we show the fallbackIcon
    let fallbackIcon: Icon

    var body: some View {
        AsyncImage(url: url) { phase in
            switch phase {
            case .success(let image):
                image
                    .resizable()
            case .empty:
                RotatingLoadingIcon(size: .small, tint: .black)
            default:
                Image(uiImage: fallbackIcon.uiImage)
                    .resizable()
            }
        }
    }
}

// MARK: -

struct AsyncUrlImageView_Previews: PreviewProvider {
    static var previews: some View {
        AsyncUrlImageView(
            url: URL(string: "https://upload.wikimedia.org/wikipedia/commons/c/c5/Square_Cash_app_logo.svg")!,
            fallbackIcon: .largeiconwarningfilled
        )
    }
}
