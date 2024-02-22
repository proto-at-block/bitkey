import LightningDevKitNode
import Shared

/**
 * Executes given `body` block while catching any `BitcoinDevKit.BdkError` and maps result to
 * `Shared.BdkResultOk` or `Shared.BdkResultErr`. Should not be used with any blocks that throw
 * non `BdkError`s.
 */
func LdkResult<V>(catching body: () throws -> V) -> Shared.LdkResult<V> {
    do {
        let result = try body()
        return Shared.LdkResultOk(value: result)
    } catch {
        return Shared.LdkResultErr(error: Shared.LdkNodeError.create(error as! LightningDevKitNode.NodeError))
    }
}
