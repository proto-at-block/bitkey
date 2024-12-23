
import Shared
import SwiftUI

/*
 A callout is a component that displays a title, leading icon (optional), subtitle, trailing icon (optional)
 It has 5 treatments: Default, Information, Success, Warning, Danger (see CalloutModel.kt)
 https://www.figma.com/file/ZFPzTqbSeZliQBu8T7CUSc/%F0%9F%94%91-Bitkey-Design-System?type=design&node-id=72-21181
 */
public struct CalloutView: View {

    // MARK: - Private Properties

    private let model: CalloutModel

    init(
        model: CalloutModel
    ) {
        self.model = model
    }

    // Tracks whether the subtitle fits on a single line or not.
    // This is used to determine how to align the leading icon.
    @SwiftUI.State private var iconVerticalAlignment: SwiftUI.VerticalAlignment = .center

    public var body: some View {
        let theme = model.theme
        HStack {
            HStack(alignment: iconVerticalAlignment) {
                if let leadingIcon = self.model.leadingIcon {
                    IconView(
                        model: IconModel(
                            iconImage: .LocalImage(icon: leadingIcon),
                            iconSize: .Accessory(),
                            iconBackgroundType: IconBackgroundTypeTransient(),
                            iconAlignmentInBackground: .center,
                            iconTint: nil,
                            iconOpacity: 1.00,
                            iconTopSpacing: nil,
                            text: nil,
                            badge: nil
                        ),
                        colorOverride: theme.titleColor
                    )
                }

                VStack {
                    if let title = model.title {
                        Text(title)
                            .font(
                                Font.custom("Inter", size: 16)
                                    .weight(.medium)
                            )
                            .lineLimit(2)
                            .truncationMode(.tail)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .foregroundStyle(theme.titleColor)
                    }

                    let subtitleFontLineHeight = 24
                    if let subtitle = model.subtitle {
                        let fontTheme = FontTheme(
                            name: "Inter",
                            size: "16",
                            lineHeight: subtitleFontLineHeight.formatted(),
                            kerning: "0"
                        )
                        ModeledText(
                            model: .fromModel(
                                model: subtitle,
                                font: fontTheme,
                                textColor: theme.subtitleColor
                            )
                        )
                        .opacity(0.60)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .foregroundStyle(theme.subtitleColor)
                        .background(
                            GeometryReader { geometry in
                                Color.clear
                                    .onAppear {
                                        let isSubtitleMultiLine = geometry.size
                                            .height > CGFloat(subtitleFontLineHeight)
                                        if isSubtitleMultiLine {
                                            // If the subtitle is multiline, align the icon to the
                                            // top of the row
                                            iconVerticalAlignment = .top
                                        } else {
                                            // If the subtitle is single line, align the icon to the
                                            // center of the row
                                            iconVerticalAlignment = .center
                                        }
                                    }
                            }
                        )
                    }
                }
            }

            HStack {
                if let trailingIcon = self.model.trailingIcon {
                    let iconBackgroundColor = switch self.model.treatment {
                    case .default_:
                        IconBackgroundTypeSquare.Color.default_
                    case .information:
                        IconBackgroundTypeSquare.Color.information
                    case .success:
                        IconBackgroundTypeSquare.Color.success
                    case .warning:
                        IconBackgroundTypeSquare.Color.warning
                    case .danger:
                        IconBackgroundTypeSquare.Color.danger
                    default:
                        IconBackgroundTypeSquare.Color.default_
                    }
                    if let onClick = model.onClick {
                        Button(action: onClick.invoke) {
                            IconView(
                                model: IconModel(
                                    iconImage: .LocalImage(icon: trailingIcon),
                                    iconSize: .Accessory(),
                                    iconBackgroundType: IconBackgroundTypeSquare(
                                        size: .Large(),
                                        color: iconBackgroundColor,
                                        cornerRadius: 12
                                    ),
                                    iconAlignmentInBackground: .center,
                                    iconTint: nil,
                                    iconOpacity: 1.00,
                                    iconTopSpacing: nil,
                                    text: nil,
                                    badge: nil
                                ),
                                colorOverride: theme.trailingIconColor
                            )
                            .frame(iconSize: IconSize.Large())
                        }
                    } else {
                        IconView(
                            model: IconModel(
                                iconImage: .LocalImage(icon: trailingIcon),
                                iconSize: .Accessory(),
                                iconBackgroundType: IconBackgroundTypeSquare(
                                    size: .Large(),
                                    color: iconBackgroundColor,
                                    cornerRadius: 12
                                ),
                                iconAlignmentInBackground: .center,
                                iconTint: nil,
                                iconOpacity: 1.00,
                                iconTopSpacing: nil,
                                text: nil,
                                badge: nil
                            ),
                            colorOverride: theme.trailingIconColor
                        )
                        .frame(iconSize: IconSize.Large())
                    }
                }
            }
        }
        .padding(EdgeInsets(top: 16, leading: 16, bottom: 16, trailing: 16))
        .background(
            theme.backgroundColor,
            in: RoundedCorners(radius: 16, corners: .allCorners)
        )
    }
}

// Standard theme variables for a callout treatment
struct CalloutTheme {
    let titleColor: Color
    let subtitleColor: Color
    let backgroundColor: Color
    let leadingIconColor: Color
    let trailingIconColor: Color
    let trailingIconBackgroundColor: Color
}

extension CalloutModel {
    var theme: CalloutTheme {
        switch self.treatment {
        case .default_:
            return CalloutTheme(
                titleColor: .calloutDefaultTitle,
                subtitleColor: .calloutDefaultSubtitle,
                backgroundColor: .calloutDefaultBackground,
                leadingIconColor: .calloutDefaultTitle,
                trailingIconColor: .calloutDefaultTrailingIcon,
                trailingIconBackgroundColor: .calloutDefaultTrailingIconBackground
            )
        case .information:
            return CalloutTheme(
                titleColor: .calloutInformationTitle,
                subtitleColor: .calloutInformationSubtitle,
                backgroundColor: .calloutInformationBackground,
                leadingIconColor: .calloutInformationLeadingIcon,
                trailingIconColor: .calloutInformationTrailingIcon,
                trailingIconBackgroundColor: .calloutInformationTrailingIconBackground
            )
        case .success:
            return CalloutTheme(
                titleColor: .calloutSuccessTitle,
                subtitleColor: .calloutSuccessSubtitle,
                backgroundColor: .calloutSuccessBackground,
                leadingIconColor: .calloutSuccessTitle,
                trailingIconColor: .calloutSuccessTrailingIcon,
                trailingIconBackgroundColor: .calloutSuccessTrailingIconBackground
            )
        case .warning:
            return CalloutTheme(
                titleColor: .calloutWarningTitle,
                subtitleColor: .calloutWarningSubtitle,
                backgroundColor: .calloutWarningBackground,
                leadingIconColor: .calloutWarningTitle,
                trailingIconColor: .calloutWarningTrailingIcon,
                trailingIconBackgroundColor: .calloutWarningTrailingIconBackground
            )
        case .danger:
            return CalloutTheme(
                titleColor: .calloutDangerTitle,
                subtitleColor: .calloutDangerSubtitle,
                backgroundColor: .dangerBackground,
                leadingIconColor: .calloutDangerTitle,
                trailingIconColor: .calloutDangerTrailingIcon,
                trailingIconBackgroundColor: .danger
            )
        default:
            fatalError("Unhandled callout treatment")
        }
    }
}

extension CalloutModel.Treatment: Identifiable {}

struct CalloutViewPreview: PreviewProvider {
    static var previews: some View {
        Group {
            // Single-line subtitle
            ForEach(
                [CalloutModel.Treatment.default_, .information, .success, .warning, .danger],
                id: \.self
            ) { treatment in
                CalloutView(
                    model:
                    CalloutModel(
                        title: "Title",
                        subtitle: LabelModelStringModel(string: "Subtitle"),
                        treatment: treatment,
                        leadingIcon: .largeiconcheckstroked,
                        trailingIcon: .smalliconarrowright,
                        onClick: StandardClick(onClick: {})
                    )
                )
                .previewDisplayName("CalloutView - \(treatment)")
            }

            // Multi-line subtitle
            ForEach(
                [CalloutModel.Treatment.default_, .information, .success, .warning, .danger],
                id: \.self
            ) { treatment in
                CalloutView(
                    model:
                    CalloutModel(
                        title: "Title",
                        subtitle: LabelModelStringModel(
                            string: "Subtitle - line one\nSubtitle - line two"
                        ),
                        treatment: treatment,
                        leadingIcon: .largeiconcheckstroked,
                        trailingIcon: .smalliconarrowright,
                        onClick: StandardClick(onClick: {})
                    )
                )
                .previewDisplayName("CalloutView - Multiline - \(treatment)")
            }
        }
    }
}
