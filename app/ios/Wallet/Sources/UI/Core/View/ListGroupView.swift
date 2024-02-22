import Foundation
import Shared
import SwiftUI

// MARK: -

public struct ListGroupView: View {

    // MARK: - Private Properties

    private let viewModel: ListGroupModel
    private let itemHeight: CGFloat?

    // MARK: - Life Cycle

    public init(viewModel: ListGroupModel, itemHeight: CGFloat? = nil) {
        self.viewModel = viewModel
        self.itemHeight = itemHeight
    }

    // MARK: - View

    public var body: some View {
        switch viewModel.style {
        case .none:
            regularList(showsDivider: false, addsVerticalPadding: false)
        case .divider:
            regularList(showsDivider: true, addsVerticalPadding: false)
        case .cardItem:
            cardItemList
        case .cardGroup, .cardGroupDivider:
            regularList(showsDivider: viewModel.style == .cardGroupDivider, minItemHeight: 60, addsVerticalPadding: true)
                .wrapInCard()
        case .threeColumnCardItem:
            fixedColumnCardItemList(columnCount: 3)
        default:
            regularList(showsDivider: true, addsVerticalPadding: false)
        }
    }
    
    public func regularList(showsDivider: Bool, minItemHeight: CGFloat? = nil, addsVerticalPadding: Bool) -> some View {
        VStack(spacing: 0) {
            viewModel.header.map { sectionHeaderText in
                ModeledText(model: .standard(sectionHeaderText, font: .title3, textColor: .foreground60))
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.top, addsVerticalPadding ? 20 : 0)
                    .padding(.bottom, viewModel.items.isEmpty ? 16 : 0)
            }
            ForEach(Array(zip(viewModel.items.indices, viewModel.items)), id:\.0) { index, listItem in
                ListItemView(viewModel: listItem)
                    .frame(minHeight: minItemHeight)

                if showsDivider, index != viewModel.items.endIndex - 1 {
                    Divider()
                        .frame(height: 1)
                        .overlay(Color.foreground10)
                }
            }
            viewModel.footerButton.map { footerButtonModel in
                ButtonView(model: footerButtonModel, cornerRadius: 12)
                    .padding(.top, 8)
                    .padding(.bottom, addsVerticalPadding ? 20 : 0)
            }
        }
    }

    public var cardItemList: some View {
        VStack {
            ForEach(Array(zip(viewModel.items.indices, viewModel.items)), id:\.0) { index, listItem in
                ListItemView(viewModel: listItem)
                    .padding(.horizontal, 16)
                    .background {
                        RoundedRectangle(cornerRadius: 12)
                            .fill(.black.opacity(0.03))
                    }

                if (index != viewModel.items.endIndex - 1) {
                    Spacer()
                        .frame(minHeight: 8, idealHeight: 16, maxHeight: 16)
                        .fixedSize()
                }
            }
        }
    }
    public func fixedColumnCardItemList(columnCount: Int) -> some View {
        let fixedColumnGrid = Array(repeating: GridItem(.fixed(80), spacing: 32), count: columnCount)
        return LazyVGrid(columns: fixedColumnGrid, alignment: .center, spacing: 16) {
            ForEach(Array(zip(viewModel.items.indices, viewModel.items)), id:\.0) { index, listItem in
                let view = ListItemView(viewModel: listItem)
                    .padding(8)
                    .frame(width: 80, height: 64)
                    .background(RoundedRectangle(cornerRadius: 16).fill(Color.foreground.opacity(0.03)))
                    .if(listItem.selected) { view in
                        view.overlay(
                            RoundedRectangle(cornerRadius: 16).inset(by: 2).stroke(Color.foreground, lineWidth: 2)
                        )
                    }
                view
            }
        }
    }
}

struct ListGroupView_Preview: PreviewProvider {
    private static func viewModel(style: ListGroupStyle) -> ListGroupModel {
        return ListGroupModel(
            header: "List Header",
            items: [
                .init(
                    title: "Title",
                    titleAlignment: .left,
                    secondaryText: "Secondary Text",
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
                ),
                .init(
                    title: "Title",
                    titleAlignment: .left,
                    secondaryText: "Secondary Text",
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
                ),
                .init(
                    title: "Title",
                    titleAlignment: .left,
                    secondaryText: "Secondary Text",
                    sideText: "Side Text",
                    secondarySideText: "Secondary Side Text",
                    leadingAccessoryAlignment: .center,
                    leadingAccessory: nil,
                    trailingAccessory: ListItemAccessoryCompanion().drillIcon(tint: .on30),
                    treatment: .secondary,
                    sideTextTint: .green,
                    enabled: false,
                    selected: false,
                    onClick: {},
                    pickerMenu: nil,
                    testTag: nil
                )
            ],
            style: style,
            footerButton: nil
        )
    }
    
    static var previews: some View {
        ListGroupView(
            viewModel: viewModel(style: .none)
        )
        ListGroupView(
            viewModel: viewModel(style: .cardGroup)
        )
        ListGroupView(
            viewModel: viewModel(style: .cardItem)
        )
    }
}

// MARK: -

extension View {

    func wrapInCard() -> some View {
        return self
            .padding(.horizontal, 16)
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(Color.foreground10, lineWidth: 2)
            )

    }

}
