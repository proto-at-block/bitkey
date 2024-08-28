import firmware
import Foundation
import Shared

public final class FirmwareCommsLogBufferImpl: Shared.FirmwareCommsLogBuffer {
    private var enabled: Bool = false

    public init() {
        firmware.disableProtoExchangeLogging()
    }

    public func configure(enabled: Bool) {
        self.enabled = enabled
        if self.enabled {
            log { "FirmwareCommsLogBufferImpl enabled" }
            firmware.enableProtoExchangeLogging()
        } else {
            log { "FirmwareCommsLogBufferImpl disabled" }
            firmware.disableProtoExchangeLogging()
        }
    }

    public func upload() {
        if !enabled {
            // Nothing should be logged in the LogBuffer by the downstream Rust code, but just
            // to be safe, we'll check the feature flag here as well.
            return
        }

        for logEntry in firmware.getProtoExchangeLogs() {
            log(tag: "WCA") { logEntry }
        }
    }
}
