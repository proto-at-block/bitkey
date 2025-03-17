import Foundation
import Shared
import Testing

@testable import Wallet

struct Spake2ImplTests {
    let spake2 = Spake2Impl()

    @Test
    func goodRoundTripNoConfirmation() throws {
        let (aliceKeys, bobKeys) = try performKeyExchange(
            alicePassword: OkioByteString.companion.encodeUtf8("password"),
            bobPassword: OkioByteString.companion.encodeUtf8("password")
        )

        assertKeysMatch(aliceKeys: aliceKeys, bobKeys: bobKeys, shouldMatch: true)
    }

    @Test
    func bobWrongPasswordNoConfirmation() throws {
        let (aliceKeys, bobKeys) = try performKeyExchange(
            alicePassword: OkioByteString.companion.encodeUtf8("password"),
            bobPassword: OkioByteString.companion.encodeUtf8("passworf")
        )

        assertKeysMatch(aliceKeys: aliceKeys, bobKeys: bobKeys, shouldMatch: false)
    }

    @Test
    func aliceWrongPasswordNoConfirmation() throws {
        let (aliceKeys, bobKeys) = try performKeyExchange(
            alicePassword: OkioByteString.companion.encodeUtf8("passworf"),
            bobPassword: OkioByteString.companion.encodeUtf8("password")
        )

        assertKeysMatch(aliceKeys: aliceKeys, bobKeys: bobKeys, shouldMatch: false)
    }

    @Test
    func goodRoundTripWithConfirmation() throws {
        let (aliceKeys, bobKeys) = try performKeyExchange(
            alicePassword: OkioByteString.companion.encodeUtf8("password"),
            bobPassword: OkioByteString.companion.encodeUtf8("password")
        )

        assertKeysMatch(aliceKeys: aliceKeys, bobKeys: bobKeys, shouldMatch: true)

        let aliceKeyConfMsg = try spake2.generateKeyConfMsg(role: Spake2Role.alice, keys: aliceKeys)
        let bobKeyConfMsg = try spake2.generateKeyConfMsg(role: Spake2Role.bob, keys: bobKeys)

        try spake2.processKeyConfMsg(
            role: Spake2Role.alice,
            receivedKeyConfMsg: bobKeyConfMsg,
            keys: aliceKeys
        )
        try spake2.processKeyConfMsg(
            role: Spake2Role.bob,
            receivedKeyConfMsg: aliceKeyConfMsg,
            keys: bobKeys
        )
    }

    @Test
    func wrongPasswordWithConfirmation() throws {
        let (aliceKeys, bobKeys) = try performKeyExchange(
            alicePassword: OkioByteString.companion.encodeUtf8("password"),
            bobPassword: OkioByteString.companion.encodeUtf8("passworf")
        )

        assertKeysMatch(aliceKeys: aliceKeys, bobKeys: bobKeys, shouldMatch: false)

        let aliceKeyConfMsg = try spake2.generateKeyConfMsg(role: Spake2Role.alice, keys: aliceKeys)
        let bobKeyConfMsg = try spake2.generateKeyConfMsg(role: Spake2Role.bob, keys: bobKeys)

        #expect(throws: (any Error).self) {
            try spake2.processKeyConfMsg(
                role: Spake2Role.alice,
                receivedKeyConfMsg: bobKeyConfMsg,
                keys: aliceKeys
            )
        }
        #expect(throws: (any Error).self) {
            try spake2.processKeyConfMsg(
                role: Spake2Role.bob,
                receivedKeyConfMsg: aliceKeyConfMsg,
                keys: bobKeys
            )
        }
    }

    @Test
    func samePasswordDifferentKeys() throws {
        let (aliceKeysFirst, bobKeysFirst) = try performKeyExchange(
            alicePassword: OkioByteString.companion.encodeUtf8("password"),
            bobPassword: OkioByteString.companion.encodeUtf8("password")
        )
        assertKeysMatch(aliceKeys: aliceKeysFirst, bobKeys: bobKeysFirst, shouldMatch: true)

        let (aliceKeysSecond, bobKeysSecond) = try performKeyExchange(
            alicePassword: OkioByteString.companion.encodeUtf8("password"),
            bobPassword: OkioByteString.companion.encodeUtf8("password")
        )
        assertKeysMatch(aliceKeys: aliceKeysSecond, bobKeys: bobKeysSecond, shouldMatch: true)

        // Ensure keys from the first and second runs are different
        var result: Bool

        result = aliceKeysFirst.aliceEncryptionKey != aliceKeysSecond.aliceEncryptionKey
        #expect(result)
        result = aliceKeysFirst.bobEncryptionKey != aliceKeysSecond.bobEncryptionKey
        #expect(result)
        result = aliceKeysFirst.aliceConfKey != aliceKeysSecond.aliceConfKey
        #expect(result)
        result = aliceKeysFirst.bobConfKey != aliceKeysSecond.bobConfKey
        #expect(result)

        result = bobKeysFirst.aliceEncryptionKey != bobKeysSecond.aliceEncryptionKey
        #expect(result)
        result = bobKeysFirst.bobEncryptionKey != bobKeysSecond.bobEncryptionKey
        #expect(result)
        result = bobKeysFirst.aliceConfKey != bobKeysSecond.aliceConfKey
        #expect(result)
        result = bobKeysFirst.bobConfKey != bobKeysSecond.bobConfKey
        #expect(result)
    }

    func performKeyExchange(
        alicePassword: OkioByteString,
        bobPassword: OkioByteString
    ) throws -> (Spake2SymmetricKeys, Spake2SymmetricKeys) {
        let aliceParams = Spake2Params(
            role: Spake2Role.alice,
            myName: "Alice",
            theirName: "Bob",
            password: alicePassword
        )
        let bobParams = Spake2Params(
            role: Spake2Role.bob,
            myName: "Bob",
            theirName: "Alice",
            password: bobPassword
        )

        let aliceKeyPair = try spake2.generateKeyPair(spake2Params: aliceParams)
        let bobKeyPair = try spake2.generateKeyPair(spake2Params: bobParams)

        let aliceSymmetricKeys = try spake2.processTheirPublicKey(
            spake2Params: aliceParams,
            myKeyPair: aliceKeyPair,
            theirPublicKey: bobKeyPair.publicKey,
            aad: nil
        )
        let bobSymmetricKeys = try spake2.processTheirPublicKey(
            spake2Params: bobParams,
            myKeyPair: bobKeyPair,
            theirPublicKey: aliceKeyPair.publicKey,
            aad: nil
        )

        return (aliceSymmetricKeys, bobSymmetricKeys)
    }

    func assertKeysMatch(
        aliceKeys: Spake2SymmetricKeys,
        bobKeys: Spake2SymmetricKeys,
        shouldMatch: Bool
    ) {
        var result: Bool

        result = aliceKeys.aliceEncryptionKey == bobKeys.aliceEncryptionKey
        #expect(result == shouldMatch)
        result = aliceKeys.bobEncryptionKey == bobKeys.bobEncryptionKey
        #expect(result == shouldMatch)
        result = aliceKeys.aliceConfKey == bobKeys.aliceConfKey
        #expect(result == shouldMatch)
        result = aliceKeys.bobConfKey == bobKeys.bobConfKey
        #expect(result == shouldMatch)
    }
}
