import Foundation
import Shared
import SwiftUI

// MARK: -

public struct ListItemView: View {

    // MARK: - Private Properties

    private let viewModel: ListItemModel
    private let verticalPadding: CGFloat

    // MARK: - Life Cycle

    public init(viewModel: ListItemModel, verticalPadding: CGFloat = 16.f) {
        self.viewModel = viewModel
        self.verticalPadding = verticalPadding
    }

    // MARK: - View

    public var body: some View {
        if let pickerModel = viewModel.pickerMenu {
            ListItemPickerView(viewModel: pickerModel) {
                ListItemContentView(viewModel: viewModel, verticalPadding: verticalPadding)
            }.ifNonnull(viewModel.testTag) { view, testTag in
                view.accessibilityIdentifier(testTag)
            }
        } else if let onClick = viewModel.onClick {
            Button(action: onClick) {
                ListItemContentView(viewModel: viewModel, verticalPadding: verticalPadding)
            }.ifNonnull(viewModel.testTag) { view, testTag in
                view.accessibilityIdentifier(testTag)
            }
        } else {
            ListItemContentView(viewModel: viewModel, verticalPadding: verticalPadding)
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
    var body: some View {
        HStack(spacing: 8) {
            // Leading accessory
            viewModel.leadingAccessory.map { leadingAccessory in
                ListItemAccessoryView(viewModel: leadingAccessory)
            }

            if (!viewModel.title.isEmpty) {
                let titleAlignment = switch viewModel.titleAlignment{
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
                    enabled: viewModel.enabled
                ).frame(maxWidth: .infinity, alignment: .leading)
            }

            if (viewModel.sideText != nil || viewModel.secondarySideText != nil) {
                Spacer()
                TitleSubtitleView(
                    alignment: .trailing,
                    title: viewModel.sideText,
                    titleColor: viewModel.sideTextTint.color(enabled: viewModel.enabled),
                    subtitle: viewModel.secondarySideText,
                    subtitleColor: viewModel.subtitleColor,
                    enabled: viewModel.enabled
                )
            }

            // Trailing accessory
            viewModel.trailingAccessory.map { trailingAccessory in
                ListItemAccessoryView(viewModel: trailingAccessory)
            }
        }
        .padding(.vertical, verticalPadding)
        .padding(.trailing, 4)
    }
}

// MARK: -

private struct TitleSubtitleView: View {
    var alignment: TextAlignment
    var title: String?
    var titleColor: Color = .foreground
    var titleFont: FontTheme = FontTheme.body2Medium
    var subtitle: String?
    var subtitleColor: Color = .foreground60
    var enabled: Bool

    var body: some View {
        VStack(spacing: 4) {
            title.map {
                ModeledText(
                    model: .standard(
                        $0,
                        font: titleFont,
                        textAlignment: alignment,
                        textColor: titleColor
                    )
                )
            }
            subtitle.map {
                ModeledText(
                    model: .standard(
                        $0,
                        font: .body3Regular,
                        textAlignment: alignment,
                        textColor: subtitleColor
                    )
                )
            }
        }
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
        default:
            return FontTheme.body2Medium

        }
    }

    var subtitleColor: Color {
        if enabled {
            return Color.foreground60
        } else {
            return Color.foreground30
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
                        secondaryText: "Seconday Text",
                        sideText: "Side Text",
                        secondarySideText: "Secondary Side Text",
                        leadingAccessoryAlignment: .center,
                        leadingAccessory: ListItemAccessoryIconAccessory(icon: .largeiconadd),
                        trailingAccessory: nil,
                        treatment: .primary,
                        sideTextTint: .primary,
                        enabled: true,
                        selected: false,
                        onClick: {},
                        pickerMenu: nil, 
                        testTag: nil
                    )
        )
        ListItemView(
            viewModel:
                    .init(
                        title: "Disabled",
                        titleAlignment: .left,
                        secondaryText: "Seconday Text",
                        sideText: "Side Text",
                        secondarySideText: "Secondary Side Text",
                        leadingAccessoryAlignment: .center,
                        leadingAccessory: ListItemAccessoryIconAccessory(icon: .largeiconadd),
                        trailingAccessory: nil,
                        treatment: .primary,
                        sideTextTint: .primary,
                        enabled: false,
                        selected: false,
                        onClick: {},
                        pickerMenu: nil,
                        testTag: nil
                    )
        )
        ListItemView(
            viewModel:
                    .init(
                        title: "Secondary",
                        titleAlignment: .left,
                        secondaryText: "Seconday Text",
                        sideText: "Side Text",
                        secondarySideText: "Secondary Side Text",
                        leadingAccessoryAlignment: .center,
                        leadingAccessory: ListItemAccessoryIconAccessory(icon: .largeiconadd),
                        trailingAccessory: nil,
                        treatment: .secondary,
                        sideTextTint: .primary,
                        enabled: true,
                        selected: false,
                        onClick: {},
                        pickerMenu: nil,
                        testTag: nil
                    )
        )
        ListItemView(
            viewModel:
                    .init(
                        title: "Seconday Disabled",
                        titleAlignment: .left,
                        secondaryText: "Seconday Text",
                        sideText: "Side Text",
                        secondarySideText: "Secondary Side Text",
                        leadingAccessoryAlignment: .center,
                        leadingAccessory: ListItemAccessoryIconAccessory(icon: .largeiconadd),
                        trailingAccessory: nil,
                        treatment: .secondary,
                        sideTextTint: .primary,
                        enabled: false,
                        selected: false,
                        onClick: {},
                        pickerMenu: nil,
                        testTag: nil
                    )
        )
    }
}
