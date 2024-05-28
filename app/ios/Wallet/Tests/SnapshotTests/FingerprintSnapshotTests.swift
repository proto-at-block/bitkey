import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class FingerprintSnapshotTests: XCTestCase {
    func test_prompting_for_fingerprint_fwup() {
        let view = FormView(
            viewModel: PromptingForFingerprintFwUpSheetModelKt.PromptingForFingerprintFwUpSheetModel(
                onCancel: {},
                onUpdate: {}
            ).body as! FormBodyModel
        )

        assertBitkeySnapshots(view: view)
    }
    
    func test_deleting_fingerprint_confirmation() {
        let view = FormView(
            viewModel: ConfirmDeleteFingerprintBodyModelKt.ConfirmDeleteFingerprintBodyModel(
                onDelete: {},
                onCancel: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }
    
    func test_listing_fingerprints() {
        let enrolledFingerprints = EnrolledFingerprints(
            maxCount: 3,
            fingerprintHandles: [
                FingerprintHandle(index: 0, label: "Left Thumb"),
                FingerprintHandle(index: 1, label: "Right Thumb")
            ]
        )
        
        let view = FormView(
            viewModel: ListingFingerprintsBodyModelKt.ListingFingerprintsBodyModel(
                enrolledFingerprints: enrolledFingerprints,
                onBack: {},
                onAddFingerprint: {_ in},
                onEditFingerprint: {_ in}
            )
        )

        assertBitkeySnapshots(view: view)
    }
    
    func test_adding_new_fingerprint() {
        let view = FormView(
            viewModel: EditingFingerprintBodyModelKt.EditingFingerprintBodyModel(
                index: 1,
                label: "",
                textFieldValue: "",
                onDelete: {},
                onSave: {},
                onValueChange: {_ in},
                onBackPressed: {},
                isExistingFingerprint: false,
                attemptToDeleteLastFingerprint: false
            )
        )

        assertBitkeySnapshots(view: view)
    }
    
    func test_editing_existing_fingerprint() {
        let view = FormView(
            viewModel: EditingFingerprintBodyModelKt.EditingFingerprintBodyModel(
                index: 0,
                label: "Left thumb",
                textFieldValue: "Right thumb",
                onDelete: {},
                onSave: {},
                onValueChange: {_ in},
                onBackPressed: {},
                isExistingFingerprint: true,
                attemptToDeleteLastFingerprint: false
            )
        )

        assertBitkeySnapshots(view: view)
    }
    
    func test_editing_existing_fingerprint_save_disabled() {
        let view = FormView(
            viewModel: EditingFingerprintBodyModelKt.EditingFingerprintBodyModel(
                index: 0,
                label: "Left thumb",
                textFieldValue: "Left thumb",
                onDelete: {},
                onSave: {},
                onValueChange: {_ in},
                onBackPressed: {},
                isExistingFingerprint: true,
                attemptToDeleteLastFingerprint: false
            )
        )

        assertBitkeySnapshots(view: view)
    }
    
    func test_editing_attempt_to_remove_last_fingerprint() {
        let view = FormView(
            viewModel: EditingFingerprintBodyModelKt.EditingFingerprintBodyModel(
                index: 0,
                label: "Left thumb",
                textFieldValue: "Left thumb",
                onDelete: {},
                onSave: {},
                onValueChange: {_ in},
                onBackPressed: {},
                isExistingFingerprint: true,
                attemptToDeleteLastFingerprint: true
            )
        )

        assertBitkeySnapshots(view: view)
    }
    
    
    
}
