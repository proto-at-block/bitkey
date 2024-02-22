import Shared
import SwiftUI

// MARK: -

struct DebugListGroupView: View {

    // MARK: - Public Properties

    public let viewModel: ListGroupModel

    // MARK: - View

    var body: some View {
        Section {
            ForEach(viewModel.items, id:\.title) { listItem in
                if let onClick = listItem.onClick {
                    DebugListItemView(viewModel: listItem)
                        .onTapGesture {
                            onClick()
                        }
                } else {
                    DebugListItemView(viewModel: listItem)
                }
             }
        } header: {
            if let header = viewModel.header {
                Text(header)
            } else {
                EmptyView()
            }
        }
    }

}

// MARK: -

struct DebugListItemView: View {

    // MARK: - Public Properties

    public let viewModel: ListItemModel

    // MARK: - Life Cycle

    public init(viewModel: ListItemModel) {
        self.viewModel = viewModel
    }

    // MARK: - View

    var body: some View {
        HStack {
            VStack(spacing: 4) {
                Text(viewModel.title)
                    .font(.headline)
                    .frame(maxWidth: .infinity, alignment: .leading)
                viewModel.secondaryText.map { secondaryText in
                    Text(secondaryText).font(.caption)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            Spacer()
            viewModel.sideText.map { sideText in
                VStack {
                    Text(sideText)
                        .font(.body)
                    viewModel.secondarySideText.map { secondarySideText in
                        Text(secondarySideText).font(.footnote)
                    }
                }.contextMenu {
                    Button {
                        UIPasteboard.general.string = sideText
                    } label: {
                        Text("Copy \(viewModel.title)")
                        Image(systemName: "doc.on.doc")
                    }
                 }
            }
            Spacer()
            viewModel.trailingAccessory.map { trailingAccessory in
                ListItemAccessoryView(viewModel: trailingAccessory)
            }
        }
    }

}
