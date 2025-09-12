import Foundation
import Shared

/**
 * Executes given `body` block while catching any `core.ChaincodeDelegationResult` and maps result to
 * `Shared.ChaincodeDelegationResultOk` or `Shared.ChaincodeDelegationResultErr`. Should not be used with any blocks that throw
 * non `ChaincodeDelegationError`s.
 */
func ChaincodeDelegationResult<V>(catching body: () throws -> V) -> Shared
    .ChaincodeDelegationResult<V>
{
    do {
        let result = try body()
        return Shared.ChaincodeDelegationResultOk(value: result)
    } catch {
        return Shared
            .ChaincodeDelegationResultErr(error: Shared.ChaincodeDelegationError.create(error))
    }
}
