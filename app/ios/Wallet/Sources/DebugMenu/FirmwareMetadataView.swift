import Shared
import SwiftUI

// MARK: -

public class FirmwareMetadataViewController: UIHostingController<FirmwareMetadataView>, ModelRepresentableViewController {

    // MARK: - Life Cycle

    public init(viewModel: FirmwareMetadataBodyModel) {
        super.init(rootView: FirmwareMetadataView(viewModel: viewModel))
    }

    required dynamic init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    // MARK: - ModelRepresentableViewController

    public func apply(model viewModel: FirmwareMetadataBodyModel) {
        rootView = FirmwareMetadataView(viewModel: viewModel)
    }

}

// MARK: -

public struct FirmwareMetadataView: View {

    // MARK: - Public Properties

    public let viewModel: FirmwareMetadataBodyModel

    // MARK: - Life Cycle

    public init(
        viewModel: FirmwareMetadataBodyModel
    ) {
        self.viewModel = viewModel
    }

    // MARK: - View

    public var body: some View {
        NavigationView {
            List {
                ButtonView(
                    model: ButtonModel(text: "Refresh", isEnabled: true, isLoading: false, leadingIcon: nil, treatment: .primary, size: .footer, testTag: nil, onClick: ClickCompanion.shared.standardClick(onClick: viewModel.onFirmwareMetadataRefreshClick))
                )
                viewModel.firmwareMetadataModel.map { FirmwareMetadataDataView(vm: $0) }
            }
            .navigationBarBackButtonHidden(true)
            .navigationBarTitle("Firmware Metadata")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                Button("Back", action: viewModel.onBack)
            }
        }
    }
}

// MARK: -

private struct FirmwareMetadataDataView: View {

    let vm: FirmwareMetadataModel

    public var body: some View {
        VStack {
            TitleDetailView(title: "Active Slot", detail: vm.activeSlot)
            TitleDetailView(title: "Git ID", detail: vm.gitId)
            TitleDetailView(title: "Git Branch", detail: vm.gitBranch)
            TitleDetailView(title: "Version", detail: vm.version)
            TitleDetailView(title: "Build", detail: vm.build)
            TitleDetailView(title: "Timestamp", detail: vm.timestamp)
            TitleDetailView(title: "Hash", detail: vm.hash)
            TitleDetailView(title: "HW Revision", detail: vm.hwRevision)

        }
    }
}

// MARK: -

private struct TitleDetailView: View {
    let title: String
    let detail: String

    var body: some View {
        HStack {
            Text(title)
                .font(.headline)
            Spacer()
            Text(detail)
        }
    }
}
