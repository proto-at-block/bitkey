
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

    public var body: some View {
        let theme = model.theme
        HStack {
            HStack {
                if let leadingIcon = self.model.leadingIcon {
                    IconView(
                        model: IconModel(
                            iconImage: .LocalImage(icon: leadingIcon),
                            iconSize: .accessory,
                            iconBackgroundType: IconBackgroundTypeTransient(),
                            iconTint: nil,
                            iconOpacity: 1.00,
                            iconTopSpacing: nil,
                            text: nil
                        ),
                        colorOverride: theme.titleColor
                    )
                }

                VStack {
                    Text(model.title)
                        .font(
                            Font.custom("Inter", size: 16)
                                .weight(.medium)
                        )
                        .lineLimit(2)
                        .truncationMode(.tail)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .foregroundStyle(theme.titleColor)

                    if let subtitle = model.subtitle {
                        let fontTheme = FontTheme(
                            name: "Inter",
                            size: "16",
                            lineHeight: "24",
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
                    }
                }

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
                                    iconSize: .accessory,
                                    iconBackgroundType: IconBackgroundTypeSquare(
                                        size: .large,
                                        color: iconBackgroundColor,
                                        cornerRadius: 12
                                    ),
                                    iconTint: nil,
                                    iconOpacity: 1.00,
                                    iconTopSpacing: nil,
                                    text: nil
                                ),
                                colorOverride: theme.trailingIconColor
                            )
                            .frame(iconSize: IconSize.large)
                        }
                    } else {
                        IconView(
                            model: IconModel(
                                iconImage: .LocalImage(icon: trailingIcon),
                                iconSize: .accessory,
                                iconBackgroundType: IconBackgroundTypeSquare(
                                    size: .large,
                                    color: iconBackgroundColor,
                                    cornerRadius: 12
                                ),
                                iconTint: nil,
                                iconOpacity: 1.00,
                                iconTopSpacing: nil,
                                text: nil
                            ),
                            colorOverride: theme.trailingIconColor
                        )
                        .frame(iconSize: IconSize.large)
                    }
                }
            }
            .padding(EdgeInsets(top: 16, leading: 16, bottom: 16, trailing: 16))
        }
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
                trailingIconColor: .calloutDefaultTrailingIcon,
                trailingIconBackgroundColor: .calloutDefaultTrailingIconBackground
            )
        case .information:
            return CalloutTheme(
                titleColor: .calloutInformationTitle,
                subtitleColor: .calloutInformationSubtitle,
                backgroundColor: .calloutInformationBackground,
                trailingIconColor: .calloutInformationTrailingIcon,
                trailingIconBackgroundColor: .calloutInformationTrailingIconBackground
            )
        case .success:
            return CalloutTheme(
                titleColor: .calloutSuccessTitle,
                subtitleColor: .calloutSuccessSubtitle,
                backgroundColor: .calloutSuccessBackground,
                trailingIconColor: .calloutSuccessTrailingIcon,
                trailingIconBackgroundColor: .calloutSuccessTrailingIconBackground
            )
        case .warning:
            return CalloutTheme(
                titleColor: .calloutWarningTitle,
                subtitleColor: .calloutWarningSubtitle,
                backgroundColor: .calloutWarningBackground,
                trailingIconColor: .calloutWarningTrailingIcon,
                trailingIconBackgroundColor: .calloutWarningTrailingIconBackground
            )
        case .danger:
            return CalloutTheme(
                titleColor: .calloutDangerTitle,
                subtitleColor: .calloutDangerSubtitle,
                backgroundColor: .dangerBackground,
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
            ForEach([CalloutModel.Treatment.default_, .information, .success, .warning, .danger]) {
                CalloutView(
                    model:
                    CalloutModel(
                        title: "Title",
                        subtitle: LabelModelStringModel(string: "Subtitle"),
                        treatment: $0,
                        leadingIcon: .largeiconcheckstroked,
                        trailingIcon: .smalliconarrowright,
                        onClick: StandardClick(onClick: {})
                    )
                )
                .previewDisplayName("CalloutView - \($0)")
            }
        }
    }
}
