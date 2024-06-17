import BitcoinDevKit
import Shared

public class BdkBumpFeeTxBuilderFactoryImpl: BdkBumpFeeTxBuilderFactory {
    public init() {}

    public func bumpFeeTxBuilder(txid: String, feeRate: Float) -> BdkBumpFeeTxBuilder {
        return BdkBumpFeeTxBuilderImpl(txBuilder: BumpFeeTxBuilder(txid: txid, newFeeRate: feeRate))
    }
}
