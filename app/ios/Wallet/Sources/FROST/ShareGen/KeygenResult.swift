import Foundation
import Shared

/**
 * Executes given `body` block while catching any `core.KeygenError` and maps result to
 * `Shared.KeygenResultOk` or `Shared.KeygenResultErr`. Should not be used with any blocks that throw
 * non `KeygenError`s.
 */
func KeygenResult<V>(catching body: () throws -> V) -> Shared.KeygenResult<V> {
    do {
        let result = try body()
        return Shared.KeygenResultOk(value: result)
    } catch {
        return Shared.KeygenResultErr(error: Shared.KeygenError.create(error))
    }
}
