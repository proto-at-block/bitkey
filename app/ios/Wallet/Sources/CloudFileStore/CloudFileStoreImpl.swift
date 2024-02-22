import Foundation
import Shared

/**
 * iOS implementation of a [CloudFileStore].
 *
 * Currently only iCloud Drive is supported.
 */
public final class CloudFileStoreImpl: Shared.CloudFileStore {
    
    // MARK: - Private Properties
    
    private let iCloudDriveFileStore: iCloudDriveFileStore
    
    public init(iCloudDriveFileStore: iCloudDriveFileStore) {
        self.iCloudDriveFileStore = iCloudDriveFileStore
    }
    
    // MARK: - CloudFileStore
    
    public func exists(
        account: CloudStoreAccount,
        fileName: String
    ) async throws -> CloudFileStoreResult<KotlinBoolean> {
        do {
            switch account {
            case is iCloudAccount:
                return try await iCloudDriveFileStore.exists(fileName: fileName)
            default:
                throw CloudFileStoreError.unsupportedAccount(account)
            }
        } catch {
            return error.toCloudFileStoreResultErr()
        }
    }
    
    public func read(
        account: CloudStoreAccount,
        fileName: String
    ) async throws -> CloudFileStoreResult<OkioByteString> {
        do {
            switch account {
            case is iCloudAccount:
                return try await iCloudDriveFileStore.read(fileName: fileName)
            default:
                throw CloudFileStoreError.unsupportedAccount(account)
            }
        } catch {
            return error.toCloudFileStoreResultErr()
        }
    }
    
    public func remove(
        account: CloudStoreAccount,
        fileName: String
    ) async throws -> CloudFileStoreResult<KotlinUnit> {
        do {
            switch account {
            case is iCloudAccount:
                return try await iCloudDriveFileStore.remove(fileName: fileName)
            default:
                throw CloudFileStoreError.unsupportedAccount(account)
            }
        } catch {
            return error.toCloudFileStoreResultErr()
        }
    }
    
    public func write(
        account: CloudStoreAccount,
        bytes: OkioByteString,
        fileName: String,
        mimeType: MimeType
    ) async throws -> CloudFileStoreResult<KotlinUnit> {
        do {
            switch account {
            case is iCloudAccount:
                return try await iCloudDriveFileStore.write(bytes: bytes, fileName: fileName)
            default:
                throw CloudFileStoreError.unsupportedAccount(account)
            }
        } catch {
            return error.toCloudFileStoreResultErr()
        }
    }
    
}

// MARK: -

public enum CloudFileStoreError : Error {
    case unsupportedAccount(CloudStoreAccount)
}

// MARK: -

extension Swift.Error {
    func toCloudFileStoreResultErr<V: AnyObject>() -> CloudFileStoreResultErr<V> {
        return CloudFileStoreResultErr(error: .init(rectificationData: localizedDescription))
    }
}
