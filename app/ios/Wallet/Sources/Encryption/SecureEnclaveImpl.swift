import CryptoKit
import Shared

public protocol SecureEnclaveError: Error {
    var message: String { get }
}

struct SecureEnclaveErrorImpl: SecureEnclaveError {
    let message: String
}

public final class SecureEnclaveImpl: Shared.SecureEnclave {
    // Used ONLY for unit tests. We can't set `kSecAttrIsPermanent = true` in unit tests, and so
    // we can't get keys back out of the Keychain; it's not persisted there. So, we have to keep
    // references to the SecKeys, hence we smuggle them out from this class, when they shouldn't
    // be.
    var smuggledKeys: [String: SecKey] = [:]

    public init() {}

    private func isRunningInUnitTest() -> Bool {
        return ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil
    }

    private func buildAccessControl(_ spec: SeKeySpec) throws -> SecAccessControlCreateFlags {
        switch spec.usageConstraints {
        case .none:
            return [.privateKeyUsage]
        case .biometricsOrPinRequired:
            return [.privateKeyUsage, .userPresence]
        case .pinRequired:
            return [.privateKeyUsage, .devicePasscode]
        default:
            throw SecureEnclaveErrorImpl(
                message: "Unhandled usageConstraints: $\(spec.usageConstraints)"
            )
        }
    }

    func getPublicKeyBytes(from privateKey: SecKey) throws -> Data {
        guard let publicKey = SecKeyCopyPublicKey(privateKey) else {
            throw SecureEnclaveErrorImpl(message: "Couldn't get public key")
        }

        var error: Unmanaged<CFError>?
        guard let publicKeyBytes = SecKeyCopyExternalRepresentation(publicKey, &error) else {
            if let error = error?.takeRetainedValue() {
                throw error as Error
            }
            throw SecureEnclaveErrorImpl(message: "Couldn't export public key bytes")
        }

        return publicKeyBytes as Data
    }

    func loadPrivateKey(from name: String) throws -> SecKey {
        if isRunningInUnitTest() {
            return smuggledKeys[name]!
        }

        let query: [String: Any] = [
            kSecClass as String: kSecClassKey,
            kSecAttrApplicationTag as String: name,
            kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
            kSecAttrKeyClass as String: kSecAttrKeyClassPrivate,
            kSecReturnRef as String: true,
        ]

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)

        guard status == errSecSuccess else {
            throw NSError(domain: NSOSStatusErrorDomain, code: Int(status), userInfo: nil)
        }

        return item as! SecKey
    }

    func loadSePublicKey(_ publicKey: SePublicKey) throws -> SecKey {
        let keyAttributes: [String: Any] = [
            kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
            kSecAttrKeyClass as String: kSecAttrKeyClassPublic,
            kSecAttrKeySizeInBits as String: 256,
        ]

        guard let pub = SecKeyCreateWithData(
            publicKey.bytes.asData() as CFData,
            keyAttributes as CFDictionary,
            nil
        ) else {
            throw SecureEnclaveErrorImpl(message: "Couldn't create public key from data")
        }

        return pub
    }

    public func diffieHellman(
        ourPrivateKey: SeKeyHandle,
        peerPublicKey: SePublicKey
    ) throws -> KotlinByteArray {
        let privateKey = try loadPrivateKey(from: ourPrivateKey.name)
        let publicKey = try loadSePublicKey(peerPublicKey)

        let algorithm: SecKeyAlgorithm = .ecdhKeyExchangeStandard

        guard SecKeyIsAlgorithmSupported(privateKey, .keyExchange, algorithm) else {
            throw SecureEnclaveErrorImpl(
                message: "Key exchange algorithm not supported for this key"
            )
        }

        var error: Unmanaged<CFError>?
        let keyExchangeParams =
            [SecKeyKeyExchangeParameter.requestedSize.rawValue: 32] as CFDictionary
        guard let sharedSecret = SecKeyCopyKeyExchangeResult(
            privateKey,
            algorithm,
            publicKey,
            keyExchangeParams,
            &error
        ) else {
            if let error = error?.takeRetainedValue() {
                throw error as Error
            }
            throw SecureEnclaveErrorImpl(message: "Diffie-Hellman key exchange failed")
        }

        return (sharedSecret as Data).asKotlinByteArray
    }

    func deleteKeyFromSecureEnclave(tag: String) throws {
        let query: [String: Any] = [
            kSecClass as String: kSecClassKey,
            kSecAttrApplicationTag as String: tag,
            kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
        ]

        let status = SecItemDelete(query as CFDictionary)

        if status == errSecSuccess {
            print("Key successfully deleted.")
        } else if status == errSecItemNotFound {
            print("Key not found.")
        } else {
            if let error = SecCopyErrorMessageString(status, nil) {
                throw NSError(
                    domain: NSOSStatusErrorDomain,
                    code: Int(status),
                    userInfo: [NSLocalizedDescriptionKey: error]
                )
            }
        }
    }

    public func generateP256KeyPair(spec: SeKeySpec) throws -> SeKeyPair {
        if spec.validity != nil {
            throw SecureEnclaveErrorImpl(message: "Can't set validity on iOS")
        }

        // Android allows you to overwrite keys. iOS has some strange behavior, where the
        // access control policy from the OLD key remains. To ensure consistency, we outright
        // delete the key before generating a new one.
        try deleteKeyFromSecureEnclave(tag: spec.name)

        guard let accessControl = try SecAccessControlCreateWithFlags(
            kCFAllocatorDefault,
            // Only accessible when device is unlocked, and when a passcode is set;
            // if the passcode is removed, then all keys protected by this protection class will
            // be deleted.
            kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly,
            buildAccessControl(spec),
            nil
        ) else {
            throw SecureEnclaveErrorImpl(message: "Failed to create access control")
        }

        var privateKeyAttrs: [String: Any] = [
            kSecAttrApplicationTag as String: spec.name,
        ]
        var attributes: [String: Any] = [
            kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
            kSecAttrKeySizeInBits as String: 256,
            kSecAttrAccessControl as String: accessControl,
        ]

        // Unit tests can't use the SE.
        if !isRunningInUnitTest() {
            attributes[kSecAttrTokenID as String] = kSecAttrTokenIDSecureEnclave
            privateKeyAttrs[kSecAttrIsPermanent as String] = true
        } else {
            print("In unit test; NOT setting kSecAttrTokenIDSecureEnclave")
        }

        attributes[kSecPrivateKeyAttrs as String] = privateKeyAttrs

        var error: Unmanaged<CFError>?
        guard let privateKey = SecKeyCreateRandomKey(attributes as CFDictionary, &error) else {
            if let err = error?.takeRetainedValue() {
                // Rethrow the CFError as a Swift Error
                throw err as Error
            } else {
                throw SecureEnclaveErrorImpl(message: "Unknown error")
            }
        }

        if isRunningInUnitTest() {
            smuggledKeys[spec.name] = privateKey
        }

        return try SeKeyPair(
            privateKey: SeKeyHandle(name: spec.name),
            publicKey: SePublicKey(
                bytes: getPublicKeyBytes(from: privateKey)
                    .asKotlinByteArray
            )
        )
    }

    public func publicKeyForPrivateKey(sePrivateKey: SeKeyHandle) throws -> SePublicKey {
        let privateKey = try loadPrivateKey(from: sePrivateKey.name)
        let publicKey = try getPublicKeyBytes(from: privateKey)
        return SePublicKey(bytes: publicKey.asKotlinByteArray)
    }

    public func requirePurposeSupportedByHardware(purpose _: SeKeyPurpose) throws {
        // If the SE is present on the phone, then it will support signing and agreement.
        if !CryptoKit.SecureEnclave.isAvailable {
            throw SecureEnclaveErrorImpl(message: "Secure Enclave not available")
        }
    }
}
