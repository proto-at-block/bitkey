import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class RotateAuthKeysSnapshotTests: XCTestCase {

    func test_key_rotation_choice__proposed_rotation() {
        let view = FormView(
            viewModel: RotateAuthKeyScreens().DeactivateDevicesAfterRestoreChoice(
                onNotRightNow: {},
                removeAllOtherDevicesEnabled: false,
                onRemoveAllOtherDevices: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_key_rotation_choice__settings() {
        let view = FormView(
            viewModel: RotateAuthKeyScreens().DeactivateDevicesFromSettingsChoice(
                onBack: {},
                removeAllOtherDevicesEnabled: false,
                onRemoveAllOtherDevices: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_key_rotation_loading__proposed_rotation() {
        let view = LoadingSuccessView(
            viewModel: RotateAuthKeyScreens().RotatingKeys(
                context: .proposedRotation
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_key_rotation_loading__settings() {
        let view = LoadingSuccessView(
            viewModel: RotateAuthKeyScreens().RotatingKeys(
                context: .settings
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_key_rotation_loading__failed_attempt() {
        let view = LoadingSuccessView(
            viewModel: RotateAuthKeyScreens().RotatingKeys(
                context: .failedAttempt
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_key_rotation_success__proposed_rotation() {
        let view = FormView(
            viewModel: RotateAuthKeyScreens().Confirmation(
                context: .proposedRotation,
                onSelected: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_key_rotation_success__settings() {
        let view = FormView(
            viewModel: RotateAuthKeyScreens().Confirmation(
                context: .settings,
                onSelected: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_key_rotation_success__failed_attempt() {
        let view = FormView(
            viewModel: RotateAuthKeyScreens().Confirmation(
                context: .failedAttempt,
                onSelected: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_key_rotation_acceptable_failure__proposed_rotation() {
        let view = FormView(
            viewModel: RotateAuthKeyScreens().AcceptableFailure(
                context: .proposedRotation,
                onRetry: {},
                onAcknowledge: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_key_rotation_acceptable_failure__settings() {
        let view = FormView(
            viewModel: RotateAuthKeyScreens().AcceptableFailure(
                context: .settings,
                onRetry: {},
                onAcknowledge: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_key_rotation_acceptable_failure__failed_attempt() {
        let view = FormView(
            viewModel: RotateAuthKeyScreens().AcceptableFailure(
                context: .failedAttempt,
                onRetry: {},
                onAcknowledge: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_key_rotation_unexpected_failure__proposed_rotation() {
        let view = FormView(
            viewModel: RotateAuthKeyScreens().UnexpectedFailure(
                context: .proposedRotation,
                onRetry: {},
                onContactSupport: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_key_rotation_unexpected_failure__settings() {
        let view = FormView(
            viewModel: RotateAuthKeyScreens().UnexpectedFailure(
                context: .settings,
                onRetry: {},
                onContactSupport: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_key_rotation_unexpected_failure__failed_attempt() {
        let view = FormView(
            viewModel: RotateAuthKeyScreens().UnexpectedFailure(
                context: .failedAttempt,
                onRetry: {},
                onContactSupport: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_key_rotation_account_locked_failure__proposed_rotation() {
        let view = FormView(
            viewModel: RotateAuthKeyScreens().AccountLockedFailure(
                context: .proposedRotation,
                onRetry: {},
                onContactSupport: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_key_rotation_account_locked_failure__settings() {
        let view = FormView(
            viewModel: RotateAuthKeyScreens().AccountLockedFailure(
                context: .settings,
                onRetry: {},
                onContactSupport: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_key_rotation_account_locked_failure__failed_attempt() {
        let view = FormView(
            viewModel: RotateAuthKeyScreens().AccountLockedFailure(
                context: .failedAttempt,
                onRetry: {},
                onContactSupport: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_key_rotation_dismissing_proposal() {
        let view = LoadingSuccessView(
            viewModel: RotateAuthKeyScreens().DismissingProposal(
                context: .proposedRotation
            )
        )

        assertBitkeySnapshots(view: view)
    }

}
