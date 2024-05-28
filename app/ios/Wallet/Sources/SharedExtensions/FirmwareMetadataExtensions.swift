import firmware
import Foundation
import Shared

extension Shared.FirmwareMetadata {

    convenience init(coreMetadata metadata: firmware.FirmwareMetadata) {
        self.init(
            activeSlot: {
                switch metadata.activeSlot {
                case .a: return .a
                case .b: return .b
                }
            }(),
            gitId: metadata.gitId,
            gitBranch: metadata.gitBranch,
            version: metadata.version,
            build: metadata.build,
            timestamp: .Companion.shared.fromEpochSeconds(epochSeconds: Int64(metadata.timestamp), nanosecondAdjustment: 0),
            hash: .Companion.shared.decodeHex(metadata.hash.map { String(format: "%02X", $0) }.joined()),
            hwRevision: metadata.hwRevision
        )
    }

}
