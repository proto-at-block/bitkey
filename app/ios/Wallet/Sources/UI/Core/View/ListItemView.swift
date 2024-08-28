import Foundation
import Shared
import SwiftUI

// MARK: -

public struct ListItemView: View {

    // MARK: - Private Properties

    private let viewModel: ListItemModel
    private let verticalPadding: CGFloat
    private let hideContent: Bool

    // MARK: - Life Cycle

    public init(
        viewModel: ListItemModel,
        verticalPadding: CGFloat = 16.f,
        hideContent: Bool = false
    ) {
        self.viewModel = viewModel
        self.verticalPadding = verticalPadding
        self.hideContent = hideContent
    }

    // MARK: - View

    public var body: some View {
        if let pickerModel = viewModel.pickerMenu {
            ListItemPickerView(viewModel: pickerModel) {
                ListItemContentView(
                    viewModel: viewModel,
                    verticalPadding: verticalPadding,
                    hideContent: hideContent
                )
            }.ifNonnull(viewModel.testTag) { view, testTag in
                view.accessibilityIdentifier(testTag)
            }
        } else if let onClick = viewModel.onClick {
            Button(action: onClick) {
                ListItemContentView(
                    viewModel: viewModel,
                    verticalPadding: verticalPadding,
                    hideContent: hideContent
                )
            }.ifNonnull(viewModel.testTag) { view, testTag in
                view.accessibilityIdentifier(testTag)
            }
        } else {
            ListItemContentView(
                viewModel: viewModel,
                verticalPadding: verticalPadding,
                hideContent: hideContent
            )
            .ifNonnull(viewModel.testTag) { view, testTag in
                view.accessibilityIdentifier(testTag)
            }
        }
    }
}

// MARK: -

struct ListItemContentView: View {
    let viewModel: ListItemModel
    let verticalPadding: CGFloat
    let hideContent: Bool

    init(
        viewModel: ListItemModel,
        verticalPadding: CGFloat,
        hideContent: Bool
    ) {
        self.viewModel = viewModel
        self.verticalPadding = verticalPadding
        self.hideContent = hideContent
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack(alignment: viewModel.accessoryAlignment) {
                // Leading accessory
                viewModel.leadingAccessory.map { leadingAccessory in
                    ListItemAccessoryView(viewModel: leadingAccessory)
                }

                HStack {
                    if viewModel.titleLabel == nil {
                        if !viewModel.title.isEmpty {
                            let titleAlignment = switch viewModel.titleAlignment {
                            case .left: TextAlignment.leading
                            case .center: TextAlignment.center
                            default: TextAlignment.leading
                            }
                            TitleSubtitleView(
                                alignment: titleAlignment,
                                title: viewModel.title,
                                titleColor: viewModel.titleColor,
                                titleFont: viewModel.titleFont,
                                subtitle: viewModel.secondaryText,
                                subtitleColor: viewModel.subtitleColor,
                                subtitleFont: viewModel.subtitleFont,
                                enabled: viewModel.enabled,
                                showNewCoachmark: viewModel.showNewCoachmark
                            )
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .if(viewModel.listItemTitleBackgroundTreatment != nil) { title in
                                title
                                    .padding(16)
                                    .frame(maxWidth: .infinity)
                                    .background(Color.foreground10)
                                    .cornerRadius(12)
                            }
                        }
                    } else {
                        switch viewModel.titleLabel {
                        case let model as LabelModelStringModel:
                            if !model.string.isEmpty {
                                let titleAlignment = switch viewModel.titleAlignment {
                                case .left: TextAlignment.leading
                                case .center: TextAlignment.center
                                default: TextAlignment.leading
                                }
                                TitleSubtitleView(
                                    alignment: titleAlignment,
                                    title: model.string,
                                    titleColor: viewModel.titleColor,
                                    titleFont: viewModel.titleFont,
                                    subtitle: viewModel.secondaryText,
                                    subtitleColor: viewModel.subtitleColor,
                                    enabled: viewModel.enabled,
                                    showNewCoachmark: viewModel.showNewCoachmark,
                                    hideContent: hideContent
                                )
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .if(viewModel.listItemTitleBackgroundTreatment != nil) { title in
                                    title
                                        .padding(16)
                                        .frame(maxWidth: .infinity)
                                        .background(Color.foreground10)
                                        .cornerRadius(12)
                                }
                            }

                        case let model as LabelModelStringWithStyledSubstringModel:
                            ModeledText(
                                model: .standard(
                                    .string(from: model, font: .body2Regular),
                                    font: .body2Regular
                                )
                            )

                        case let model as LabelModelLinkSubstringModel:
                            ModeledText(
                                model: .linkedText(
                                    textContent: .linkedText(
                                        string: model.markdownString(),
                                        links: model.linkedSubstrings
                                    ),
                                    font: FontTheme.body2Regular
                                )
                            )

                        default:
                            fatalError("Unexpected Kotlin LabelModel")
                        }
                    }

                    if viewModel.sideText != nil || viewModel.secondarySideText != nil {
                        Spacer()
                        TitleSubtitleView(
                            alignment: .trailing,
                            title: viewModel.sideText,
                            titleColor: viewModel.sideTextTint.color(enabled: viewModel.enabled),
                            subtitle: viewModel.secondarySideText,
                            subtitleColor: viewModel.subtitleColor,
                            enabled: viewModel.enabled,
                            hideContent: hideContent
                        )
                    }

                    // Special Trailing accessory
                    viewModel.specialTrailingAccessory.map { specialTrailingAccessory in
                        ListItemAccessoryView(viewModel: specialTrailingAccessory)
                    }

                    // Trailing accessory
                    viewModel.trailingAccessory.map { trailingAccessory in
                        ListItemAccessoryView(viewModel: trailingAccessory)
                    }
                }
            }
            .padding(.vertical, verticalPadding)
            .padding(.trailing, 4)

            if let coachmark = viewModel.coachmark {
                CoachmarkView(model: coachmark)
            }
        }
    }
}

// MARK: -

private struct TitleSubtitleView: View {
    var alignment: TextAlignment
    var title: String?
    var titleColor: Color = .foreground
    var titleFont: FontTheme = .body2Medium
    var subtitle: String?
    var subtitleColor: Color = .foreground60
    var subtitleFont: FontTheme = .body3Regular
    var enabled: Bool
    var showNewCoachmark: Bool = false
    var hideContent: Bool = false

    var body: some View {
        CollapsibleLabelContainer(
            collapsed: hideContent,
            topContent: title.map { title in
                HStack {
                    ModeledText(
                        model: .standard(
                            title,
                            font: titleFont,
                            textAlignment: alignment,
                            width: showNewCoachmark ? .hug : nil,
                            textColor: titleColor
                        )
                    )
                    if showNewCoachmark {
                        NewCoachmark(treatment: .light)
                        Spacer()
                    }
                }
            },
            bottomContent: subtitle.map { subtitle in
                ModeledText(
                    model: .standard(
                        subtitle,
                        font: subtitleFont,
                        textAlignment: alignment,
                        textColor: subtitleColor
                    )
                )
            },
            collapsedContent: CollapsedMoneyView(height: 12, centered: false),
            spacing: 4
        )
    }
}

// MARK: -

private extension Shared.ListItemSideTextTint {

    func color(enabled: Bool) -> Color {
        switch self {
        case .primary:
            return enabled ? Color.foreground : Color.foreground30
        case .secondary:
            return .foreground60
        case .green:
            return Color.positiveForeground
        default:
            return Color.foreground
        }
    }
}

private extension Shared.ListItemModel {

    var titleColor: Color {
        if enabled {
            switch treatment {
            case .primary, .tertiary:
                return Color.foreground
            case .secondary:
                return Color.foreground60
            default:
                return Color.foreground
            }
        } else {
            return Color.foreground30
        }
    }

    var titleFont: FontTheme {
        switch treatment {
        case .primary:
            return FontTheme.body2Medium
        case .secondary:
            return FontTheme.body2Regular
        case .tertiary:
            return FontTheme.body3Regular
        case .primaryTitle:
            return FontTheme.title1
        case .secondaryDisplay:
            return FontTheme.display2
        default:
            return FontTheme.body2Medium
        }
    }

    var subtitleFont: FontTheme {
        switch treatment {
        case .secondaryDisplay:
            return .body1Regular
        default:
            return .body3Regular
        }
    }

    var subtitleColor: Color {
        if enabled {
            return Color.foreground60
        } else {
            return Color.foreground30
        }
    }

    var accessoryAlignment: SwiftUI.VerticalAlignment {
        return switch self.leadingAccessoryAlignment {
        case .top:
            .top
        case .center:
            .center
        default:
            .center
        }
    }
}

// MARK: - Preview

struct ListItemView_Preview: PreviewProvider {
    static var previews: some View {
        ListItemView(
            viewModel:
            .init(
                title: "Primary",
                titleAlignment: .left,
                listItemTitleBackgroundTreatment: nil,
                secondaryText: "Seconday Text",
                sideText: "Side Text",
                secondarySideText: "Secondary Side Text",
                leadingAccessoryAlignment: .center,
                leadingAccessory: ListItemAccessoryIconAccessory(icon: .largeiconadd),
                trailingAccessory: nil,
                specialTrailingAccessory:
                ListItemAccessoryIconAccessory(
                    iconPadding: nil,
                    model: .init(
                        iconImage: .LocalImage(icon: .smalliconwarningfilled),
                        iconSize: .accessory,
                        iconBackgroundType: IconBackgroundTypeTransient(),
                        iconTint: .warning,
                        iconOpacity: nil,
                        iconTopSpacing: nil,
                        text: nil
                    )
                ),
                treatment: .primary,
                sideTextTint: .primary,
                enabled: true,
                selected: false,
                showNewCoachmark: false,
                onClick: {},
                pickerMenu: nil,
                testTag: nil,
                titleLabel: nil,
                coachmark: nil
            )
        )
        ListItemView(
            viewModel:
            .init(
                title: "Disabled",
                titleAlignment: .left,
                listItemTitleBackgroundTreatment: nil,
                secondaryText: "Seconday Text",
                sideText: "Side Text",
                secondarySideText: "Secondary Side Text",
                leadingAccessoryAlignment: .center,
                leadingAccessory: ListItemAccessoryIconAccessory(icon: .largeiconadd),
                trailingAccessory: nil,
                specialTrailingAccessory: nil,
                treatment: .primary,
                sideTextTint: .primary,
                enabled: false,
                selected: false,
                showNewCoachmark: false,
                onClick: {},
                pickerMenu: nil,
                testTag: nil,
                titleLabel: nil,
                coachmark: nil
            )
        )
        ListItemView(
            viewModel:
            .init(
                title: "Secondary",
                titleAlignment: .left,
                listItemTitleBackgroundTreatment: nil,
                secondaryText: "Seconday Text",
                sideText: "Side Text",
                secondarySideText: "Secondary Side Text",
                leadingAccessoryAlignment: .center,
                leadingAccessory: ListItemAccessoryIconAccessory(icon: .largeiconadd),
                trailingAccessory: nil,
                specialTrailingAccessory: nil,
                treatment: .secondary,
                sideTextTint: .primary,
                enabled: true,
                selected: false,
                showNewCoachmark: false,
                onClick: {},
                pickerMenu: nil,
                testTag: nil,
                titleLabel: nil,
                coachmark: nil
            )
        )
        ListItemView(
            viewModel:
            .init(
                title: "Seconday Disabled",
                titleAlignment: .left,
                listItemTitleBackgroundTreatment: nil,
                secondaryText: "Seconday Text",
                sideText: "Side Text",
                secondarySideText: "Secondary Side Text",
                leadingAccessoryAlignment: .center,
                leadingAccessory: ListItemAccessoryIconAccessory(icon: .largeiconadd),
                trailingAccessory: nil,
                specialTrailingAccessory: nil,
                treatment: .secondary,
                sideTextTint: .primary,
                enabled: false,
                selected: false,
                showNewCoachmark: false,
                onClick: {},
                pickerMenu: nil,
                testTag: nil,
                titleLabel: nil,
                coachmark: nil
            )
        )

        ListItemView(
            viewModel:
            .init(
                title: "1234-ABCD-EF",
                titleAlignment: .center,
                listItemTitleBackgroundTreatment: .recovery,
                secondaryText: nil,
                sideText: nil,
                secondarySideText: nil,
                leadingAccessoryAlignment: .center,
                leadingAccessory: nil,
                trailingAccessory: nil,
                specialTrailingAccessory: nil,
                treatment: .primaryTitle,
                sideTextTint: .primary,
                enabled: true,
                selected: false,
                showNewCoachmark: false,
                onClick: {},
                pickerMenu: nil,
                testTag: nil,
                titleLabel: nil,
                coachmark: nil
            )
        )
    }
}
