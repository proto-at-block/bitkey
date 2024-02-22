import Shared
import SwiftUI

// MARK: -

struct IconView: View {
    
    let model: Shared.IconModel
    var colorOverride: Color? = nil

    var body: some View {
        ZStack(alignment: .center) {
            // Background
            switch model.iconBackgroundType {
            case let circle as IconBackgroundTypeCircle:
                Circle()
                    .fill(circle.fillColor)
                    .frame(iconSize: circle.circleSize)
            case _ as IconBackgroundTypeTransient: Color.clear
            default: Color.clear
            }

            // Content
            icon
                .foregroundColor(colorOverride ?? model.iconTint?.color ?? .foreground)

        }.frame(iconSize: model.totalSize)
    }
    
    @ViewBuilder
    private var icon: some View {
        switch model.iconImage {
        case let image as IconImage.UrlImage:
            if let url = URL(string: image.url) {
                AsyncUrlImageView(
                    url: url,
                    fallbackIcon: image.fallbackIcon
                )
                .frame(
                    width: model.iconSize.value.f,
                    height: model.iconSize.value.f
                )

            } else {
                Image(uiImage: image.fallbackIcon.uiImage)
            }
        
        case let icon as IconImage.LocalImage:
            Image(uiImage: icon.icon.uiImage)
                .resizable()
                .frame(iconSize: model.iconSize)

        case _ as IconImage.Loader:
            RotatingLoadingIcon(size: model.iconSize, tint: .black)

        default:
            fatalError("\(model.iconImage) not supported")
        }
    }
}

extension View {
    @ViewBuilder
    func frame(iconSize: IconSize) -> some View {
        self.frame(width: iconSize.value.f, height: iconSize.value.f)
    }
}

struct IconViewPreview: PreviewProvider {
    static var previews: some View {
        IconView(
            model:
                IconModel(
                    iconImage: .UrlImage(
                        url: "https://upload.wikimedia.org/wikipedia/commons/c/c5/Square_Cash_app_logo.svg",
                        fallbackIcon: .bitcoin
                    ),
                    iconSize: .small,
                    iconBackgroundType: IconBackgroundTypeTransient(),
                    iconTint: nil,
                    text: nil
                )
        )
    }
}

private extension IconBackgroundTypeCircle {
    var fillColor: Color {
        switch color {
        case .foreground10: return .foreground10
        case .translucentblack: return .black.opacity(0.1)
        case .translucentwhite: return .white.opacity(0.2)
        default: return .foreground10
        }
    }
}
