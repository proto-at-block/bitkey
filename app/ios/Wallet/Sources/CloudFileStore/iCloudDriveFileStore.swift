import Foundation
import Shared

public final class iCloudDriveFileStore {

    // MARK: - Private Properties

    private let fileCoordinator = NSFileCoordinator()

    private let fileManager = FileManager.default

    // MARK: - Internal Methods

    func exists(
        fileName: String
    ) async throws -> CloudFileStoreResult<KotlinBoolean> {
        do {
            let fileURL = try fileURLInDocumentsFolder(fileName)

            let fileExists = fileManager.fileExists(atPath: fileURL.path)

            return CloudFileStoreResultOk(value: KotlinBoolean(bool: fileExists))
        } catch {
            log(.warn, error: error) {
                "iCloud Drive: failed to check existence of file=\(fileName)"
            }
            return error.toCloudFileStoreResultErr()
        }
    }

    func read(
        fileName: String
    ) async throws -> CloudFileStoreResult<OkioByteString> {
        do {
            let fileURL = try fileURLInDocumentsFolder(fileName)

            var error: NSError?
            var readingFailed = true

            return try await withCheckedThrowingContinuation { continuation in
                fileCoordinator.coordinate(
                    readingItemAt: fileURL,
                    error: &error
                ) { readingURL in
                    readingFailed = false

                    do {
                        let fileData = try Data(contentsOf: readingURL)
                        let byteString = OkioKt.ByteString(data: fileData)
                        let result = CloudFileStoreResultOk(value: byteString)

                        continuation.resume(returning: result)
                    } catch {
                        log(.error, error: error) { "iCloud Drive: failed to read file=\(fileName)"
                        }
                        continuation.resume(throwing: iCloudDriveFileStoreError.readFailed)
                    }
                }

                // This NSFileCoordinator API is strange in that some error paths do not call the
                // completion handler above and may not populate the error; so we must check a
                // separate 'sentinel value' here per the documentation (see <https://developer.apple.com/documentation/foundation/nsfilecoordinator/1407416-coordinate>).
                if readingFailed {
                    log(.error, error: error) { "iCloud Drive: failed to read file=\(fileName)" }
                    continuation.resume(throwing: iCloudDriveFileStoreError.readFailed)
                }
            }
        } catch {
            log(.error, error: error) { "iCloud Drive: failed to read file=\(fileName)" }
            return error.toCloudFileStoreResultErr()
        }
    }

    func remove(
        fileName: String
    ) async throws -> CloudFileStoreResult<KotlinUnit> {
        do {
            let fileURL = try fileURLInDocumentsFolder(fileName)

            try fileManager.removeItem(at: fileURL)

            return CloudFileStoreResultOk(value: KotlinUnit())
        } catch {
            log(.warn, error: error) { "iCloud Drive: failed to remove file=\(fileName)" }
            return error.toCloudFileStoreResultErr()
        }
    }

    func write(
        bytes: OkioByteString,
        fileName: String
    ) async throws -> CloudFileStoreResult<KotlinUnit> {
        do {
            let fileURL = try fileURLInDocumentsFolder(fileName)

            var error: NSError?
            var writingFailed = true

            return try await withCheckedThrowingContinuation { continuation in
                fileCoordinator.coordinate(
                    writingItemAt: fileURL,
                    options: .forReplacing,
                    error: &error
                ) { writingURL in
                    writingFailed = false

                    do {
                        let data = bytes.toData()
                        try data.write(to: writingURL, options: .atomic)

                        continuation.resume(returning: CloudFileStoreResultOk(value: KotlinUnit()))
                    } catch {
                        log(.error, error: error) {
                            "iCloud Drive [accessor block]: failed to write file=\(fileName)"
                        }
                        continuation.resume(throwing: error)
                    }
                }

                // This NSFileCoordinator API is strange in that some error paths do not call the
                // completion handler above and may not populate the error; so we must check a
                // separate 'sentinel value' here per the documentation (see <https://developer.apple.com/documentation/foundation/nsfilecoordinator/1407416-coordinate>).
                if writingFailed {
                    log(.error, error: error) {
                        "iCloud Drive [writing failed block]: failed to write file=\(fileName)"
                    }
                    continuation.resume(throwing: iCloudDriveFileStoreError.writeFailed)
                }
            }
        } catch {
            log(.error, error: error) {
                "iCloud Drive [error block]: failed to write file=\(fileName)"
            }
            return error.toCloudFileStoreResultErr()
        }
    }

    // MARK: - Private Methods

    private func fileURLInDocumentsFolder(_ fileName: String) throws -> URL {
        guard let iCloudDriveFolder = fileManager.url(forUbiquityContainerIdentifier: nil) else {
            log(.error) { "iCloud Drive: ubiquity container unavailable" }
            throw iCloudDriveFileStoreError.ubiquityContainerUnavailable
        }

        // The customer visible "Bitkey" folder in iCloud Drive.
        let documentsFolder = iCloudDriveFolder.appendingPathComponent(Paths.documents)

        return documentsFolder.appendingPathComponent(fileName)
    }

}

// MARK: -

enum iCloudDriveFileStoreError: Error {
    case ubiquityContainerUnavailable
    case readFailed
    case writeFailed
}

// MARK: -

private enum Paths {
    /// Necessary path for the customer-visible folder called "Bitkey" to appear in iCloud Drive.
    static let documents = "Documents"
}
