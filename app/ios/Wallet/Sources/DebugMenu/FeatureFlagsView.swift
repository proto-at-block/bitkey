import Shared
import SwiftUI

// MARK: -

public class FeatureFlagsViewController: UIHostingController<FeatureFlagsView>, ModelRepresentableViewController {

    // MARK: - Life Cycle

    public init(viewModel: FeatureFlagsBodyModel) {
        super.init(rootView: FeatureFlagsView(viewModel: viewModel))
    }

    required dynamic init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    // MARK: - ModelRepresentableViewController

    public func apply(model viewModel: FeatureFlagsBodyModel) {
        rootView = FeatureFlagsView(viewModel: viewModel)
    }

}

// MARK: -

public struct FeatureFlagsView: View {

    // MARK: - Public Properties

    public let viewModel: FeatureFlagsBodyModel

    // MARK: - Life Cycle

    public init(
        viewModel: FeatureFlagsBodyModel
    ) {
        self.viewModel = viewModel
    }

    // MARK: - View

    public var body: some View {
        NavigationView {
            List {
                DebugListGroupView(viewModel: viewModel.flagsModel)
            }
            .listStyle(.plain)
            .navigationBarBackButtonHidden(true)
            .navigationBarTitle("Feature Flags")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                Button("Back", action: viewModel.onBack)
            }
        }
    }

}
