import core
import Foundation
import Shared
import ZIPFoundation

public class FileManagerImpl: Shared.FileManager {

    // MARK: - Private Properties

    private let fileDirectoryProvider: FileDirectoryProvider
    private let fileManager = FileManager.default

    private var applicationFilesDirectory: URL {
        return URL(fileURLWithPath: fileDirectoryProvider.filesDir())
    }

    // MARK: - Life Cycle

    public init(fileDirectoryProvider: FileDirectoryProvider) {
        self.fileDirectoryProvider = fileDirectoryProvider
    }

    // MARK: - FileManager

    public func writeFile(
        data kData: KotlinByteArray,
        fileName: String
    ) async throws -> FileManagerResult<KotlinUnit> {
        do {
            // First, create a directory at the path given by `FilesDirectoryProvider`
            try fileManager.createDirectory(
                at: applicationFilesDirectory,
                withIntermediateDirectories: true
            )
            // Then, write the data to the path with the given file name
            let filePath = applicationFilesDirectory.appendingPathComponent(fileName)
            try kData.asData().write(to: filePath, options: .atomic)
            return FileManagerResultOk(value: KotlinUnit())
        } catch {
            return error.toFileManagerResultErr()
        }
    }

    public func readFileAsBytes(fileName: String) async throws
        -> FileManagerResult<KotlinByteArray>
    {
        do {
            let filePath = applicationFilesDirectory.appendingPathComponent(fileName)
            let fileData = try Data(contentsOf: filePath)
            return FileManagerResultOk(value: .init(fileData.bytes))
        } catch {
            return error.toFileManagerResultErr()
        }
    }

    public func readFileAsString(fileName: String) async throws -> FileManagerResult<NSString> {
        do {
            let filePath = applicationFilesDirectory.appendingPathComponent(fileName)
            let fileString = try String(contentsOf: filePath)
            return FileManagerResultOk(value: fileString as NSString)
        } catch {
            return error.toFileManagerResultErr()
        }
    }

    public func unzipFile(
        zipPath: String,
        targetDirectory: String
    ) async throws -> FileManagerResult<KotlinUnit> {
        do {
            // Build the file paths
            let zipFile = applicationFilesDirectory.appendingPathComponent(zipPath)
            let targetFile = applicationFilesDirectory.appendingPathComponent(targetDirectory)

            // Try to remove any file at the target path in case we need to
            try? fileManager.removeItem(at: targetFile)

            // Unzip the file
            try fileManager.unzipItem(at: zipFile, to: targetFile)

            // Remove the no longer needed zip file
            try fileManager.removeItem(at: zipFile)

            return FileManagerResultOk(value: KotlinUnit())

        } catch {
            return error.toFileManagerResultErr()
        }
    }

    public func fileExists(fileName: String) async throws -> KotlinBoolean {
        let file = applicationFilesDirectory.appendingPathComponent(fileName)
        return KotlinBoolean(bool: fileManager.fileExists(atPath: file.path))
    }

    public func removeDir(path: String) async throws -> FileManagerResult<KotlinUnit> {
        do {
            let dir = applicationFilesDirectory.appendingPathComponent(path)

            if fileManager.fileExists(atPath: dir.path) {
                try fileManager.removeItem(at: dir)
            }

            return FileManagerResultOk(value: KotlinUnit())
        } catch {
            return error.toFileManagerResultErr()
        }
    }

    public func mkdirs(path: String) async throws -> FileManagerResult<KotlinBoolean> {
        do {
            try fileManager.createDirectory(atPath: path, withIntermediateDirectories: true)

            return FileManagerResultOk(value: KotlinBoolean(bool: true))
        } catch {
            return error.toFileManagerResultErr()
        }
    }

}

// MARK: -

private extension Swift.Error {
    func toFileManagerResultErr<V: AnyObject>() -> FileManagerResultErr<V> {
        return FileManagerResultErr(error: .init(throwable: .init(message: localizedDescription)))
    }
}

// MARK: -

private extension Data {
    var bytes: [UInt8] {
        return [UInt8](self)
    }
}
