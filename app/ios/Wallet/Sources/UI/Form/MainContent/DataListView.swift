import Foundation
import Shared
import SwiftUI

// MARK: -

struct DataListView: View {

    // MARK: - Private Properties

    private let viewModel: FormMainContentModel.DataList

    // MARK: - Life Cycle

    init(viewModel: FormMainContentModel.DataList) {
        self.viewModel = viewModel
    }

    // MARK: - View

    var body: some View {
        VStack {
            if let hero = viewModel.hero {
                Spacer().frame(height: 20)
                DataHeroView(viewModel: hero)
            }
            VStack {
                ForEach(
                    Array(zip(viewModel.items.indices, viewModel.items)),
                    id: \.0
                ) { idx, viewModel in
                    DataRowView(
                        viewModel: viewModel,
                        titleFont: .body3Regular,
                        titleTextColor: .foreground60,
                        isFirst: idx == 0
                    )
                }
            }

            viewModel.total.map { total in
                VStack {
                    Divider()
                        .frame(height: 1)
                        .overlay(Color.foreground10)
                    DataRowView(
                        viewModel: total,
                        titleFont: .body2Bold,
                        titleTextColor: .foreground,
                        isFirst: false
                    )
                }
            }
            VStack {
                ForEach(
                    Array(zip(viewModel.buttons.indices, viewModel.buttons)),
                    id: \.0
                ) { _, button in
                    ButtonView(model: button)
                }
                if !viewModel.buttons.isEmpty {
                    Spacer().frame(height: 20)
                }
            }
        }
        .padding(.vertical, viewModel.hasSingleItem ? 0 : 4)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color.foreground10, lineWidth: 2)
        )
    }

}

// MARK: -

struct DataRowView: View {
    let viewModel: FormMainContentModel.DataListData

    let titleFont: FontTheme
    let titleTextColor: Color

    let isFirst: Bool

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                HStack(spacing: 0) {
                    ModeledText(model: .standard(
                        viewModel.title,
                        font: titleFont,
                        textColor: titleTextColor
                    ))
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .lineLimit(1)
                    .fixedSize()
                    if let icon = viewModel.titleIcon {
                        IconView(model: icon)
                            .padding(.leading, 4)
                        Spacer()
                    }
                }
                .ifNonnull(viewModel.onTitle) { view, onTitle in
                    view.onTapGesture {
                        onTitle()
                    }
                }

                Spacer()
                VStack {
                    ModeledText(model: .standard(
                        viewModel.sideText,
                        font: viewModel.sideTextType.font,
                        textAlignment: .trailing,
                        textColor: viewModel.sideTextTreatment.textColor,
                        treatment: viewModel.sideTextTreatment.treatment
                    ))
                    viewModel.secondarySideText.map {
                        ModeledText(model: .standard(
                            $0,
                            font: viewModel.secondarySideTextType.font,
                            textAlignment: .trailing,
                            textColor: viewModel.secondarySideTextTreatment.textColor,
                            treatment: viewModel.secondarySideTextTreatment.treatment
                        ))
                    }
                }
                if viewModel.onClick != nil {
                    Image(uiImage: .smallIconCaretRight)
                        .foregroundColor(.foreground30)
                }
            }
            .padding(.top, isFirst ? 16 : 8)
            .padding(.bottom, 16)
            .padding(.horizontal, DesignSystemMetrics.horizontalPadding)
            .background(Color.white)
            .ifNonnull(viewModel.onClick) { view, onClick in
                view.onTapGesture {
                    onClick()
                }
            }

            if let explainer = viewModel.explainer {
                HStack {
                    VStack {
                        ModeledText(model: .standard(
                            explainer.title,
                            font: .body3Bold,
                            textColor: .foreground
                        ))
                        .padding(.bottom, 1)

                        ModeledText(model: .standard(
                            explainer.subtitle,
                            font: .body3Regular,
                            textColor: .foreground60
                        ))
                    }

                    explainer.iconButton.map { iconButtonModel in
                        VStack {
                            IconButtonView(model: iconButtonModel)
                            Spacer()
                        }
                    }
                }
                .padding(.top, 16)
                .padding(.bottom, 16)
                .padding(.horizontal, 16)
                .background(Color.foreground10)
            }

            if viewModel.showBottomDivider {
                Divider()
                    .padding(.horizontal, DesignSystemMetrics.horizontalPadding)
            }
        }
        .background(.white)
    }
}

// MARK: -

struct DataHeroView: View {
    let viewModel: FormMainContentModel.DataListDataHero

    var body: some View {
        VStack(alignment: .center) {
            if let iconModel = viewModel.image {
                IconView(model: iconModel)
                    .updateForBitkeyDevice3dView(iconModel: iconModel)
                Spacer().frame(height: 16)
            }

            if let title = viewModel.title {
                ModeledText(model: .standard(title, font: .title2, textAlignment: .center))
            }
            if let subtitle = viewModel.subtitle {
                Spacer().frame(height: 4)
                ModeledText(model: .standard(
                    subtitle,
                    font: .body3Bold,
                    textAlignment: .center,
                    textColor: .foreground60
                ))
            }

            if let button = viewModel.button {
                Spacer().frame(height: 16)
                ButtonView(model: button)
            }
        }
        .padding(.horizontal, DesignSystemMetrics.horizontalPadding)
        .padding(.vertical, 24)
    }
}

// MARK: -

private extension FormMainContentModel.DataList {

    var hasSingleItem: Bool {
        return items.count == 1 && total == nil
    }

}

// MARK: -

// Extensions that mirror mapping behavior of LabelStyle.kt
private extension FormMainContentModel.DataListDataSideTextType {
    var font: FontTheme {
        switch self {
        case .regular: .body3Regular
        case .medium: .body3Medium
        case .bold: .body3Bold
        case .body2bold: .body2Bold
        default: .body3Regular
        }
    }
}

private extension FormMainContentModel.DataListDataSideTextTreatment {
    var textColor: Color {
        switch self {
        case .primary: .foreground
        case .secondary, .strikethrough: .foreground60
        case .warning: .warningForeground
        default: .foreground
        }
    }

    var treatment: LabelTreatment {
        switch self {
        case .strikethrough: .strikethrough
        default: .unspecified
        }
    }
}

// MARK: -

struct DataListView_Previews: PreviewProvider {
    static var previews: some View {
        VStack(spacing: 16) {
            DataListView(
                viewModel: .init(
                    hero: nil,
                    items: [
                        .init(
                            withTitle: "Miner Fee",
                            titleIcon: nil,
                            onTitle: nil,
                            sideText: "bc1q...xyB1",
                            secondarySideText: nil,
                            showBottomDivider: false
                        ),
                    ],
                    total: nil,
                    buttons: []
                )
            )

            DataListView(
                viewModel: .init(
                    hero: nil,
                    items: [
                        .init(
                            withTitle: "Foo",
                            titleIcon: nil,
                            onTitle: nil,
                            sideText: "Bar",
                            secondarySideText: nil,
                            showBottomDivider: true
                        ),
                        .init(
                            withTitle: "Foo",
                            titleIcon: nil,
                            onTitle: nil,
                            sideText: "Bar",
                            secondarySideText: nil,
                            showBottomDivider: false
                        ),
                    ],
                    total: nil,
                    buttons: []
                )
            )

            DataListView(
                viewModel: .init(
                    hero: nil,
                    items: [
                        .init(
                            withTitle: "Miner Fee",
                            titleIcon: nil,
                            onTitle: nil,
                            sideText: "bc1q...xyB1",
                            secondarySideText: nil,
                            showBottomDivider: false
                        ),
                        .init(
                            withTitle: "Miner Fee",
                            titleIcon: nil,
                            onTitle: nil,
                            sideText: "bc1q...xyB1",
                            secondarySideText: nil,
                            showBottomDivider: false
                        ),
                    ],
                    total: .init(
                        withTitle: "Total Cost",
                        titleIcon: nil,
                        onTitle: nil,
                        sideText: "$20.36",
                        sideTextType: .body2bold,
                        sideTextTreatment: .primary,
                        secondarySideText: "(0.00010 BTC)",
                        showBottomDivider: false
                    ),
                    buttons: []
                )
            )

            DataListView(
                viewModel: .init(
                    hero: nil,
                    items: [
                        .init(
                            withTitle: "Should have arrived by",
                            titleIcon: nil,
                            onTitle: nil,
                            sideText: "Aug 7, 12:14pm",
                            sideTextType: .regular,
                            sideTextTreatment: .strikethrough,
                            secondarySideText: "7m late",
                            secondarySideTextType: .bold,
                            secondarySideTextTreatment: .warning,
                            explainer: FormMainContentModel.DataListDataExplainer(
                                title: "Taking longer than usual",
                                subtitle: "You can either wait for this transaction to be confirmed or speed it up – you'll need to pay a higher network fee.",
                                iconButton: IconButtonModel(
                                    iconModel: IconModel(
                                        iconImage: .LocalImage(icon: .smalliconinformationfilled),
                                        iconSize: .xsmall,
                                        iconBackgroundType: IconBackgroundTypeCircle(
                                            circleSize: .xsmall,
                                            color: .translucentblack
                                        ),
                                        iconTint: nil,
                                        iconOpacity: 0.20,
                                        iconTopSpacing: nil,
                                        text: nil
                                    ),
                                    onClick: StandardClick {},
                                    enabled: true
                                )
                            )
                        ),
                    ],
                    total: nil,
                    buttons: []
                )
            )
        }
    }
}

// MARK: -

private extension View {

    /// If the icon is `bitkeydevice3d`, updates the view to be wrapped in
    /// `BitkeyDevice3dClickableIconView`
    @ViewBuilder
    func updateForBitkeyDevice3dView(iconModel: IconModel) -> some View {
        switch iconModel.iconImage {
        case let localIconModel as IconImage.LocalImage:
            if localIconModel.icon == .bitkeydevice3d {
                BitkeyDevice3dClickableIconView(iconView: { AnyView(self) })
            } else {
                self
            }

        default:
            self
        }
    }

}
