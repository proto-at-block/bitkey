import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class CloudSnapshotTests: XCTestCase {

    func test_cloud_sign_in_fail() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateCloudSignInFailedScreenModel(
                onContactSupport: {},
                onTryAgain: {},
                onBack: {},
                devicePlatform: .ios
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_cloud_save_instructions() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateSaveBackupInstructionsBodyModel(
                requiresHardware: false,
                isLoading: false,
                onBackupClick: {},
                onLearnMoreClick: {},
                devicePlatform: .ios
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_cloud_backup_missing() {
        let view = FormView(
            viewModel: FormScreenModel_CloudRecoveryiOSKt.CloudBackupNotFoundBodyModel(
                onBack: {},
                onCheckCloudAgain: {},
                onCannotAccessCloud: {},
                onImportEmergencyAccessKit: {},
                onShowTroubleshootingSteps: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_cloud_backup_found() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateCloudBackupFoundModel(
                devicePlatform: .ios,
                onBack: {},
                onRestore: {},
                showSocRecButton: true,
                onLostBitkeyClick: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_cloud_not_signed_in() {
        let view = FormView(
            viewModel: FormScreenModel_CloudRecoveryiOSKt.CloudNotSignedInBodyModel(
                onBack: {},
                onCheckCloudAgain: {},
                onCannotAccessCloud: {},
                onImportEmergencyAccessKit: {},
                onShowTroubleshootingSteps: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_cloud_social_recovery_explaination() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateSocialRecoveryExplanationModel(
                onBack: {},
                onContinue: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

}
