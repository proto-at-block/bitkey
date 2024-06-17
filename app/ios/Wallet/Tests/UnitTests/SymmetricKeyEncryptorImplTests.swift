import Shared
import XCTest

@testable import Wallet

class SymmetricKeyEncryptorImplTests: XCTestCase {

    func test_sealAndUnseal() throws {
        let keyGenerator = SymmetricKeyGeneratorImpl()
        let encryptor = SymmetricKeyEncryptorImpl()

        let key = keyGenerator.generate()

        let appKey =
            "[b5435236/84'/1'/0']tprv8gw6bXR6ku6tCPZELTH5U5ioSn4k1rkn7Z4P6mWQf5wviG7zM9G6ZN99FXSqhZS77uBMpXzeBVywuA6Rw47k68cUX7N4ody212Ms2JdwFDU/0/*"
        let appKeyByteString = OkioKt.ByteString(data: appKey.data(using: String.Encoding.utf8)!)

        let sealedData = try encryptor.seal(unsealedData: appKeyByteString, key: key)
        let unsealedData = try encryptor.unseal(sealedData: sealedData, key: key)

        XCTAssertEqual(unsealedData, appKeyByteString)
    }

    func test_unsealAndroidCreatedData() throws {
        let encryptor = SymmetricKeyEncryptorImpl()

        let keyRaw = Data(base64Encoded: "tba6tihkCGK0Ks9fM5qmc6FBUNFDedNDut57GYBS83k=")!
        let ciphertext = Data(base64Encoded: "vwaoULHHAk6zUjWohw==")!
        let nonce = Data(base64Encoded: "d7GeMirUxwPB54H4Og8e5kVomWHaiSyK")!
        let tag = Data(base64Encoded: "eSKF6Q5/VjMA9WNvudqBAg==")!
        let sealedData = SealedData(
            ciphertext: OkioKt.ByteString(data: ciphertext),
            nonce: OkioKt.ByteString(data: nonce),
            tag: OkioKt.ByteString(data: tag)
        )
        let key = SymmetricKeyImpl(raw: OkioKt.ByteString(data: keyRaw))

        let unsealedData = try encryptor.unseal(sealedData: sealedData, key: key)

        let expected = OkioKt.ByteString(data: "13 characters".data(using: .utf8)!)
        XCTAssertEqual(expected, unsealedData)
    }

}
