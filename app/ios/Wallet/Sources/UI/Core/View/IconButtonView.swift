import Shared
import SwiftUI

public struct IconButtonView: View {

    let model: IconButtonModel
    
    public var body: some View {
        Button(action: model.onClick.invoke) {
            VStack(spacing: 8) {
                IconButtonContentView(model: model)
                if let title = model.iconModel.text {
                    ModeledText(model: .standard(title, font: .label2, textAlignment: nil, textColor: model.enabled ? .secondaryForeground : .secondaryForeground30))
                }
            }
        }
    }

}

// MARK: -

private struct IconButtonContentView: View {
    let model: IconButtonModel
    public var body: some View {
        IconView(model: model.iconModel)
            .aspectRatio(contentMode: .fit)
            .frame(
                maxWidth: model.iconModel.totalSize.value.f,
                maxHeight: model.iconModel.totalSize.value.f
            )
    }
}

// MARK: -

struct IconButton_Preview: PreviewProvider {
    static var previews: some View {
        IconButtonView(
            model:
                .init(
                    iconModel: .init(
                        iconImage: IconImage.LocalImage(icon: .largeiconsend),
                        iconSize: .avatar,
                        iconBackgroundType: IconBackgroundTypeCircle(
                            circleSize: .avatar,
                            color: .foreground10
                        ),
                        iconTint: nil,
                        iconOpacity: nil,
                        iconTopSpacing: nil,
                        text: "Send"
                    ),
                    onClick: StandardClick {},
                    enabled: true
                )
        )
    }
}
