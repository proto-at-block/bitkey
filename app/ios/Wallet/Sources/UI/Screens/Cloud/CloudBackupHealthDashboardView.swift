import Shared
import SwiftUI

// MARK: -

struct CloudBackupHealthDashboardView: View {

    // MARK: - Public Properties

    public var viewModel: CloudBackupHealthDashboardBodyModel

    // MARK: - View

    public var body: some View {
        VStack {
            ToolbarView(
                viewModel: .init(
                    leadingAccessory: ToolbarAccessoryModel.IconAccessory.companion.BackAccessory(
                        onClick: viewModel.onBack
                    ),
                    middleAccessory: ToolbarMiddleAccessoryModel.init(title: "Cloud Backup", subtitle: nil),
                    trailingAccessory: nil
                )
            )

            Spacer()
                .frame(height: 20)
            
            CloudBackupHealthStatusCardModel(viewModel: viewModel.mobileKeyBackupStatusCard)

            Spacer()
                .frame(height: 20)
            
            if let eakBackupStatusCard = viewModel.eakBackupStatusCard {
                CloudBackupHealthStatusCardModel(viewModel: eakBackupStatusCard)
            }

            Spacer()
        }
        .padding(.horizontal, DesignSystemMetrics.horizontalPadding)
    }

}

private struct CloudBackupHealthStatusCardModel: View {
    
    // MARK: - Public Properties

    public var viewModel: Shared.CloudBackupHealthStatusCardModel
    
    // MARK: - View

    var body: some View {
        VStack(spacing: 0) {
            ToolbarView(viewModel: viewModel.toolbarModel)

            FormHeaderView(viewModel: viewModel.headerModel, headlineFont: .title1)


            Spacer()
                .frame(height: 17)


            Divider()
            ListItemView(viewModel: viewModel.backupStatus)

            if let backupStatusActionButton = viewModel.backupStatusActionButton {
                ButtonView(model: backupStatusActionButton)
            }
        }
        .padding(.horizontal, DesignSystemMetrics.horizontalPadding)
        .overlay(
            RoundedRectangle(cornerRadius: 24)
                .stroke(Color.foreground10, lineWidth: 2)
        )
    }
}
