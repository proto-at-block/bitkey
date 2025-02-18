import Foundation
import Shared

/**
 * Executes given `body` block while catching any `core.SigningResult` and maps result to
 * `Shared.SigningResultOk` or `Shared.SigningResultErr`. Should not be used with any blocks that throw
 * non `KeygenError`s.
 */
func SigningResult<V>(catching body: () throws -> V) -> Shared.SigningResult<V> {
    do {
        let result = try body()
        return Shared.SigningResultOk(value: result)
    } catch {
        return Shared.SigningResultErr(error: Shared.SigningError.create(error))
    }
}
