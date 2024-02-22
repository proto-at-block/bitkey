import Shared
import XCTest

@testable import Wallet

class SymmetricKeyEncryptorImplTests: XCTestCase {

    func test_sealAndUnseal() throws {
        let keyGenerator = SymmetricKeyGeneratorImpl()
        let encryptor = SymmetricKeyEncryptorImpl()
        
        let key = keyGenerator.generate()
        
        let appKey = "[b5435236/84'/1'/0']tprv8gw6bXR6ku6tCPZELTH5U5ioSn4k1rkn7Z4P6mWQf5wviG7zM9G6ZN99FXSqhZS77uBMpXzeBVywuA6Rw47k68cUX7N4ody212Ms2JdwFDU/0/*"
        let appKeyByteString = OkioKt.ByteString(data: appKey.data(using: String.Encoding.utf8)!)
        
        let sealedData = encryptor.seal(unsealedData: appKeyByteString, key: key)
        let unsealedData = encryptor.unseal(sealedData: sealedData, key: key)
        
        XCTAssertEqual(unsealedData, appKeyByteString)
    }

}
