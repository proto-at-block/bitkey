import core
import Foundation
import Shared

public final class P256BoxImpl: Shared.P256Box {

    public init() {}

    public func generateKeyPair() -> Shared.P256BoxKeyPair {
        let keyPair = core.P256BoxKeyPair()
        let publicKey = P256BoxPublicKey(bytes: OkioKt.ByteString(data: keyPair.publicKey()))
        let privateKey = P256BoxPrivateKey(bytes: OkioKt.ByteString(data: keyPair.secretKey()))

        return P256BoxKeyPair(privateKey: privateKey, publicKey: publicKey)
    }

    public func keypairFromSecretBytes(secretBytes: OkioByteString) throws -> Shared
        .P256BoxKeyPair
    {
        let keyPair = try core.P256BoxKeyPair.fromSecretBytes(secretBytes: secretBytes.toData())
        let publicKey = P256BoxPublicKey(bytes: OkioKt.ByteString(data: keyPair.publicKey()))
        let privateKey = P256BoxPrivateKey(bytes: OkioKt.ByteString(data: keyPair.secretKey()))

        return P256BoxKeyPair(privateKey: privateKey, publicKey: publicKey)
    }

    public func encrypt(
        theirPublicKey: P256BoxPublicKey,
        myKeyPair: Shared.P256BoxKeyPair,
        nonce: XNonce,
        plaintext: OkioByteString
    ) throws -> XCiphertext {
        let p256Box = try core.P256Box(
            publicKey: theirPublicKey.bytes.toData(),
            secretKey: myKeyPair.privateKey.bytes.toData()
        )
        let ciphertext = try p256Box.encrypt(
            nonce: nonce.bytes.toData(),
            plaintext: plaintext.toData()
        )

        return XSealedData(
            header: XSealedData.Header(
                format: .withpubkey,
                algorithm: P256BoxCompanion().ALGORITHM
            ),
            ciphertext: OkioKt.ByteString(data: ciphertext),
            nonce: nonce,
            publicKey: myKeyPair.publicKey.bytes.hex()
        ).toOpaqueCiphertext()
    }

    public func decrypt(
        myPrivateKey: P256BoxPrivateKey,
        sealedData: XCiphertext
    ) throws -> OkioByteString {
        let xSealedData = try sealedData.toXSealedData()

        if xSealedData.header.algorithm != P256BoxCompanion().ALGORITHM {
            throw P256BoxError.CipherInstantiationError(message: "Invalid algorithm")
        }

        // Get the hex string from the PublicKey value property
        guard let publicKey = xSealedData.publicKey as? String else {
            throw P256BoxError.PublicKeyError(message: "XSealedData does not contain a public key")
        }

        let p256Box = try core.P256Box(
            publicKey: OkioByteString.Companion.shared.decodeHex(publicKey).toData(),
            secretKey: myPrivateKey.bytes.toData()
        )
        let plaintext = try p256Box.decrypt(
            nonce: xSealedData.nonce.bytes.toData(),
            ciphertext: xSealedData.ciphertext.toData()
        )

        return OkioKt.ByteString(data: plaintext)
    }
}
