import Shared
import SwiftUI

// MARK: -

public class AnalyticsViewController: UIHostingController<AnalyticsView>, ModelRepresentableViewController {

    // MARK: - Life Cycle

    public init(viewModel: AnalyticsBodyModel) {
        super.init(rootView: AnalyticsView(viewModel: viewModel))
    }

    required dynamic init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    // MARK: - ModelRepresentableViewController

    public func apply(model viewModel: AnalyticsBodyModel) {
        rootView = AnalyticsView(viewModel: viewModel)
    }

}

// MARK: -

public struct AnalyticsView: View {

    // MARK: - Public Properties

    public let viewModel: AnalyticsBodyModel

    // MARK: - Life Cycle

    public init(
        viewModel: AnalyticsBodyModel
    ) {
        self.viewModel = viewModel
    }

    // MARK: - View

    public var body: some View {
        NavigationView {
            List {
                Section {
                    ForEach(Array(zip(viewModel.events.indices, viewModel.events)), id: \.0) { _, eventModel in
                        ListItemView(viewModel: eventModel)
                    }
                } header: {
                    ButtonView(model: .tertiaryDestructive(text: "Clear events", onClick: viewModel.onClear))
                }
            }
            .listStyle(.plain)
            .navigationBarBackButtonHidden(true)
            .navigationBarTitle("Analytics")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                Button("Back", action: viewModel.onBack)
            }
        }
    }
}
