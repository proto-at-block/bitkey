import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class AddressSnapshotTests: XCTestCase {

    func test_address() {
        let view = AddressQrCodeView(viewModel: .snapshotTest)
        assertBitkeySnapshots(view: view, usesVisualEffect: true)
    }

    func test_address_error() {
        let view = AddressQrCodeView(viewModel: .errorSnapshotTest)
        assertBitkeySnapshots(view: view)
    }

    func test_address_loading() {
        let view = AddressQrCodeView(viewModel: .loadingSnapshotTest)
        assertBitkeySnapshots(view: view)
    }

}

// MARK: -

private extension AddressQrCodeBodyModel {

    static var snapshotTest: AddressQrCodeBodyModel {
        let address = "bc1q42lja79elem0anu8q8s3h2n687re9jax556pcc"
        return .init(
            onBack: {},
            onRefreshClick: {},
            content: AddressQrCodeBodyModelContentQrCode(
                addressQrImageUrl: nil,
                address: address,
                fallbackAddressQrCodeModel: .init(data: "bitcoin:\(address)"),
                copyButtonIcon: .smalliconcopy,
                copyButtonLabelText: "Copy",
                onCopyClick: {},
                onShareClick: {}
            )
        )
    }

    static var errorSnapshotTest: AddressQrCodeBodyModel {
        return .init(
            onBack: {},
            onRefreshClick: {},
            content: AddressQrCodeBodyModelContentError(
                title: "We couldn't create an address",
                subline: "We are looking into this. Please try again later."
            )
        )
    }

    static var loadingSnapshotTest: AddressQrCodeBodyModel {
        return .init(
            onBack: {},
            onRefreshClick: {},
            content: AddressQrCodeBodyModelContentQrCode(
                addressQrImageUrl: nil,
                address: nil,
                fallbackAddressQrCodeModel: nil,
                copyButtonIcon: .smalliconcopy,
                copyButtonLabelText: "Copy",
                onCopyClick: {},
                onShareClick: {}
            )
        )
    }

}
