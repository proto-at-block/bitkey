import Shared
import XCTest

@testable import Wallet

/// Verifies the iOS Datadog log writer redacts sensitive data via the shared KMP
/// `SensitiveDataValidator`, mirroring the Android `DatadogLogWriter` behavior.
/// The indicator patterns themselves are covered by `SensitiveDataValidatorTests`
/// in the KMP `:libs:logging-public` module; these tests pin the Swift wiring.
class DatadogLogWriterRedactionTests: XCTestCase {

    func testBitcoinAddressIsRedacted() {
        let result = DatadogLogWriter.redact(
            tag: "payments",
            message: "sending to bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq",
            throwable: nil
        )
        XCTAssertEqual(result.tag, "REDACTED")
        XCTAssertTrue(result.message.hasPrefix("REDACTED Log"))
        XCTAssertFalse(result.message.contains("bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq"))
        XCTAssertTrue(result.dropThrowable)
    }

    func testBitcoinPrivateKeyIsRedacted() {
        let xprv =
            "xprv9s21ZrQH143K3QTDL4LXw2F7HEK3wJUD2nW2nRk4stbPy6cq3jPPqjiChkVvvNKmPGJxWUtg6LnF5kejMRNNU3TGtRBeJgk33yuGBxrMPHi"
        let result = DatadogLogWriter.redact(
            tag: "keys",
            message: "loaded key \(xprv)",
            throwable: nil
        )
        XCTAssertEqual(result.tag, "REDACTED")
        XCTAssertFalse(result.message.contains(xprv))
        XCTAssertTrue(result.dropThrowable)
    }

    func testBitcoinTransactionIdIsRedacted() {
        let txid = "6f7cf9580f1c2dfb3c4d5726d155e3a025a34c2ba76c8c3c7a5be2b4a4b7e9ad"
        let result = DatadogLogWriter.redact(
            tag: "tx",
            message: "broadcast \(txid)",
            throwable: nil
        )
        XCTAssertEqual(result.tag, "REDACTED")
        XCTAssertFalse(result.message.contains(txid))
        XCTAssertTrue(result.dropThrowable)
    }

    func testSensitiveThrowableMessageTriggersRedactionOfCleanEntry() {
        let xprv =
            "xprv9s21ZrQH143K3QTDL4LXw2F7HEK3wJUD2nW2nRk4stbPy6cq3jPPqjiChkVvvNKmPGJxWUtg6LnF5kejMRNNU3TGtRBeJgk33yuGBxrMPHi"
        let result = DatadogLogWriter.redact(
            tag: "signing",
            message: "Failed to sign transaction",
            throwable: KotlinThrowable(message: "Signing failed for key \(xprv)")
        )
        XCTAssertEqual(result.tag, "REDACTED")
        XCTAssertTrue(result.message.hasPrefix("REDACTED Log"))
        XCTAssertTrue(result.dropThrowable)
    }

    func testSensitiveNestedThrowableMessageTriggersRedaction() {
        let xprv =
            "xprv9s21ZrQH143K3QTDL4LXw2F7HEK3wJUD2nW2nRk4stbPy6cq3jPPqjiChkVvvNKmPGJxWUtg6LnF5kejMRNNU3TGtRBeJgk33yuGBxrMPHi"
        let cause = KotlinThrowable(message: "Signing failed for key \(xprv)")
        let result = DatadogLogWriter.redact(
            tag: "signing",
            message: "Failed to sign transaction",
            throwable: KotlinThrowable(message: "Unexpected signing error", cause: cause)
        )
        XCTAssertEqual(result.tag, "REDACTED")
        XCTAssertTrue(result.message.hasPrefix("REDACTED Log"))
        XCTAssertTrue(result.dropThrowable)
    }

    func testCleanThrowableMessageDoesNotTriggerRedaction() {
        let result = DatadogLogWriter.redact(
            tag: "nfc",
            message: "NFC session failed",
            throwable: KotlinThrowable(message: "Tag was lost")
        )
        XCTAssertEqual(result.tag, "nfc")
        XCTAssertEqual(result.message, "NFC session failed")
        XCTAssertFalse(result.dropThrowable)
    }

    func testCleanMessagePassesThroughUnchanged() {
        let result = DatadogLogWriter.redact(
            tag: "nfc",
            message: "NFC session completed successfully",
            throwable: nil
        )
        XCTAssertEqual(result.tag, "nfc")
        XCTAssertEqual(result.message, "NFC session completed successfully")
        XCTAssertFalse(result.dropThrowable)
    }
}
