import Shared
import SwiftUI

// MARK: -

public class LogsViewController: UIHostingController<LogsView>, ModelRepresentableViewController {

    // MARK: - Life Cycle

    public init(viewModel: LogsBodyModel) {
        super.init(rootView: LogsView(viewModel: viewModel))
    }

    @available(*, unavailable)
    dynamic required init?(coder _: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    // MARK: - ModelRepresentableViewController

    public func apply(model viewModel: LogsBodyModel) {
        rootView = LogsView(viewModel: viewModel)
    }

}

// MARK: -

public struct LogsView: View {

    // MARK: - Public Properties

    public let viewModel: LogsBodyModel

    // MARK: - Life Cycle

    public init(
        viewModel: LogsBodyModel
    ) {
        self.viewModel = viewModel
    }

    // MARK: - View

    public var body: some View {
        NavigationView {
            List {
                Section {
                    ForEach(
                        Array(zip(
                            viewModel.logsModel.logRows.indices,
                            viewModel.logsModel.logRows
                        )),
                        id: \.0
                    ) { _, logRowModel in
                        LogRowView(viewModel: logRowModel)
                    }
                } header: {
                    VStack {
                        Toggle(
                            "Errors Only",
                            isOn: .init(
                                get: { viewModel.errorsOnly },
                                set: { newValue in
                                    viewModel.onErrorsOnlyValueChanged(.init(bool: newValue))
                                }
                            )
                        )
                        .tint(.primary)
                        Toggle(
                            "Analytics Only",
                            isOn: .init(
                                get: { viewModel.analyticsEventsOnly },
                                set: { newValue in
                                    viewModel
                                        .onAnalyticsEventsOnlyValueChanged(.init(bool: newValue))
                                }
                            )
                        )
                        .tint(.primary)
                        ButtonView(model: .tertiaryDestructive(
                            text: "Clear Logs",
                            onClick: viewModel.onClear
                        ))
                    }
                }
            }
            .listStyle(.plain)
            .navigationBarBackButtonHidden(true)
            .navigationBarTitle("Logs")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                Button("Back", action: viewModel.onBack)
            }
        }
    }

}

// MARK: -

private struct LogRowView: View {

    let viewModel: LogRowModel

    public var body: some View {
        VStack(alignment: .leading) {
            Text(viewModel.dateTime)
                .font(.headline.monospaced())

            HStack {
                Text(viewModel.level)
                Spacer()
                Text(viewModel.tag)
            }.font(.body.smallCaps().bold())

            Text(viewModel.message)
                .font(.body.monospaced())
            viewModel.throwableDescription.map {
                Text($0)
                    .font(.body.monospaced())
            }
        }
        .background(viewModel.isError ? Color.red.opacity(0.1) : .white)
    }
}
