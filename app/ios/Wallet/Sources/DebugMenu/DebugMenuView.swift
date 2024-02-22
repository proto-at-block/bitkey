import Shared
import SwiftUI

// MARK: -

public struct DebugMenuView: View {

    // MARK: - Public Properties

    public let viewModel: DebugMenuBodyModel

    // MARK: - Life Cycle

    public init(
        viewModel: DebugMenuBodyModel
    ) {
        self.viewModel = viewModel
    }

    // MARK: - View

    public var body: some View {
        NavigationView {
            List(Array(zip(viewModel.groups.indices, viewModel.groups)), id: \.0) { _, listGroup in
                DebugMenuListGroupView(viewModel: listGroup)
            }
            .navigationBarTitle("Debug Menu")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                Button("Close", action: viewModel.onBack)
            }
            .alert(isPresented: .constant(viewModel.alertModel != nil)) {
                let alertModel = viewModel.alertModel!
                return Alert(
                    title: Text(alertModel.title),
                    message: alertModel.subline != nil
                        ? Text(alertModel.subline!)
                        : nil,
                    primaryButton: .destructive(Text(alertModel.primaryButtonText), action: alertModel.onPrimaryButtonClick),
                    secondaryButton:  {
                        if let onSecondaryButtonClick = alertModel.onSecondaryButtonClick {
                            return Alert.Button.default(Text(alertModel.secondaryButtonText!), action: onSecondaryButtonClick)
                        } else {
                            return Alert.Button.cancel()
                        }
                    }()
                )
            }
        }
    }

}

// MARK: -

private struct DebugMenuListGroupView: View {

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
