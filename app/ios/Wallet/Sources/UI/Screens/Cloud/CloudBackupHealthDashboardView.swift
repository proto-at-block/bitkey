import Shared
import SwiftUI

// MARK: -

struct CloudBackupHealthDashboardView: View {

    // MARK: - Public Properties

    public var viewModel: CloudBackupHealthDashboardBodyModel

    // MARK: - View

    public var body: some View {
        VStack {
            ScrollView(showsIndicators: false) {
                ToolbarView(
                    viewModel: .init(
                        leadingAccessory: ToolbarAccessoryModel.IconAccessory.companion
                            .BackAccessory(
                                onClick: viewModel.onBack
                            ),
                        middleAccessory: ToolbarMiddleAccessoryModel(
                            title: "Cloud Backup",
                            subtitle: nil
                        ),
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
            }
            .padding(.horizontal, DesignSystemMetrics.horizontalPadding)
            .padding(.bottom, DesignSystemMetrics.verticalPadding)
            .background(Color.background)
        }
        .navigationBarHidden(true)
    }

}

private struct CloudBackupHealthStatusCardModel: View {

    // MARK: - Public Properties

    public var viewModel: Shared.CloudBackupHealthStatusCardModel

    // MARK: - View

    var body: some View {
        VStack(spacing: 0) {
            if let toolbar = viewModel.toolbarModel {
                ToolbarView(viewModel: toolbar).padding(
                    .horizontal,
                    DesignSystemMetrics.horizontalPadding
                )
            } else {
                Spacer().frame(height: 20)
            }

            FormHeaderView(viewModel: viewModel.headerModel, headlineFont: .title2).padding(
                .horizontal,
                DesignSystemMetrics.horizontalPadding
            )

            Spacer().frame(height: 20)

            let hasAction = viewModel.backupStatusActionButton != nil

            VStack(spacing: 0) {
                if !hasAction {
                    Divider().padding(.horizontal, DesignSystemMetrics.horizontalPadding)
                }

                ListItemView(viewModel: viewModel.backupStatus).padding(
                    .horizontal,
                    DesignSystemMetrics.horizontalPadding
                )

                if let backupStatusActionButton = viewModel.backupStatusActionButton {
                    ButtonView(model: backupStatusActionButton)
                        .padding(.bottom, DesignSystemMetrics.verticalPadding)
                        .padding(.horizontal, DesignSystemMetrics.horizontalPadding)
                }
            }.background(Group {
                if hasAction {
                    switch viewModel.type {
                    case .eakBackup:
                        Color.foreground10
                    case .mobileKeyBackup:
                        Color.warning
                    default:
                        Color.clear
                    }
                } else {
                    Color.clear
                }
            })
            .clipShape(RoundedCorners(radius: 20, corners: [.bottomLeft, .bottomRight]))
        }

        .overlay(
            RoundedRectangle(cornerRadius: 20)
                .stroke(Color.foreground10, lineWidth: 2)
        )
    }
}
