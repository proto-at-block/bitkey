import Shared
import SwiftUI

/*
 Popover-style coachmark
 */
public struct CoachmarkView: View {

    private let model: CoachmarkModel

    private var alignment: HorizontalAlignment {
        switch model.arrowPosition.horizontal {
        case .leading:
            return .leading
        case .centered:
            return .center
        case .trailing:
            return .trailing
        default:
            return .center
        }
    }

    init(
        model: CoachmarkModel
    ) {
        self.model = model
    }

    public var body: some View {
        VStack(alignment: alignment, spacing: 0) {
            // Top arrow if needed
            if model.arrowPosition.vertical == .top {
                Image(uiImage: IconImage.LocalImage(icon: .calloutarrow).icon.uiImage)
                    .resizable()
                    .renderingMode(.template)
                    .foregroundStyle(Color.coachmarkBackground)
                    .frame(width: 24, height: 12)
                    .padding(EdgeInsets(top: 0, leading: 16, bottom: 0, trailing: 16))
            }
            // Main content
            VStack(spacing: 4) {
                HStack {
                    // New badge
                    NewCoachmark(treatment: .dark)
                    Spacer()
                    // Close button
                    IconButtonView(
                        model: IconButtonModel(
                            iconModel: .init(
                                iconImage: IconImage.LocalImage(icon: .smalliconxfilled),
                                iconSize: .Small(),
                                iconBackgroundType: IconBackgroundTypeTransient(),
                                iconAlignmentInBackground: .center,
                                iconTint: .on60,
                                iconOpacity: nil,
                                iconTopSpacing: nil,
                                text: nil,
                                badge: nil
                            ),
                            onClick: StandardClick {
                                model.dismiss()
                            },
                            enabled: true
                        )
                    )
                }
                // Title
                ModeledText(
                    model: .standard(
                        model.title,
                        font: FontTheme.body2Bold,
                        textAlignment: .leading,
                        textColor: Color.white
                    )
                )
                // Description
                ModeledText(
                    model: .standard(
                        model.description_,
                        font: FontTheme.body3Regular,
                        textAlignment: .leading,
                        textColor: Color.white
                    )
                )
                // Button, if any
                if let button = model.button {
                    ButtonView(model: button)
                        .padding(.top, 12)
                }
            }
            .padding(EdgeInsets(top: 24, leading: 24, bottom: 24, trailing: 24))
            .background(Color.coachmarkBackground)
            .cornerRadius(20)
            .shadow(color: .black.opacity(0.04), radius: 4, x: 0, y: 8)

            // Bottom arrow, if needed
            if model.arrowPosition.vertical == .bottom {
                Image(uiImage: IconImage.LocalImage(icon: .calloutarrow).icon.uiImage)
                    .resizable()
                    .renderingMode(.template)
                    .foregroundStyle(Color.coachmarkBackground)
                    .frame(width: 24, height: 12)
                    .padding(EdgeInsets(top: 0, leading: 16, bottom: 0, trailing: 16))
                    .rotationEffect(.degrees(180))
            }
        }
        .padding(EdgeInsets(top: 0, leading: 16, bottom: 0, trailing: 16))
    }
}

struct CoachmarkView_Preview: PreviewProvider {
    static var previews: some View {
        CoachmarkView(
            model: .init(
                identifier: .biometricunlockcoachmark,
                title: "Multiple fingerprints",
                description: "Now you can add more fingerprints to your Bitkey device.",
                arrowPosition: CoachmarkModel.ArrowPosition(vertical: .top, horizontal: .trailing),
                button: ButtonModel(
                    text: "Add fingerprints",
                    isEnabled: true,
                    isLoading: false,
                    leadingIcon: nil,
                    treatment: .primary,
                    size: .footer,
                    testTag: nil,
                    onClick: StandardClick(onClick: {})
                ),
                image: nil,
                dismiss: {}
            )
        )
        .previewDisplayName("Top trailing")
        CoachmarkView(
            model: .init(
                identifier: .biometricunlockcoachmark,
                title: "Set up Face ID",
                description: "We recommend you secure your app by setting up Face ID to enhance app security.",
                arrowPosition: CoachmarkModel.ArrowPosition(
                    vertical: .bottom,
                    horizontal: .centered
                ),
                button: nil,
                image: nil,
                dismiss: {}
            )
        )
        .previewDisplayName("Bottom center")
    }
}
