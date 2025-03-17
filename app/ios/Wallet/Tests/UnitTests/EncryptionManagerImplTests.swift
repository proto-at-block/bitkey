import CryptoKit
import Testing

@testable import Wallet

struct EncryptionManagerImplTests {

    @Test
    func sealAndUnseal() throws {
        let manager = EncryptionManagerImpl()

        let dataKey = "ABCDEFGHIJKLMNOPQRSTUVWXYZ012345"

        let appKey =
            "[b5435236/84'/1'/0']tprv8gw6bXR6ku6tCPZELTH5U5ioSn4k1rkn7Z4P6mWQf5wviG7zM9G6ZN99FXSqhZS77uBMpXzeBVywuA6Rw47k68cUX7N4ody212Ms2JdwFDU/0/*"
        let appKeyData = try #require(appKey.data(using: .utf8))
        let dataKeyData = try #require(dataKey.data(using: .utf8))

        let sealedData = try manager.seal(appKeyData, with: dataKeyData)
        let unsealedData = try manager.unseal(sealedData, with: dataKeyData)

        let unsealedAppKey = String(data: unsealedData, encoding: .utf8)

        #expect(unsealedAppKey == appKey)
    }
}
