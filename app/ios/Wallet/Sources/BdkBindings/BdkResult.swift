import BitcoinDevKit
import Shared

/**
 * Executes given `body` block while catching any `BitcoinDevKit.BdkError` and maps result to
 * `Shared.BdkResultOk` or `Shared.BdkResultErr`. Should not be used with any blocks that throw
 * non `BdkError`s.
 */
func BdkResult<V>(catching body: () throws -> V) -> Shared.BdkResult<V> {
    do {
        let result = try body()
        return Shared.BdkResultOk(value: result)
    } catch {
        return Shared.BdkResultErr(error: Shared.BdkError.create(error))
    }
}
