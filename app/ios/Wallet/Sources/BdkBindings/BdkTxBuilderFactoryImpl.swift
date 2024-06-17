import BitcoinDevKit
import Shared

public class BdkTxBuilderFactoryImpl: BdkTxBuilderFactory {

    public init() {}

    public func txBuilder() -> BdkTxBuilder {
        return BdkTxBuilderImpl(txBuilder: TxBuilder())
    }
}
