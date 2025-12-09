import BitcoinDevKitLegacy
import Shared

public class BdkAddressBuilderImpl: BdkAddressBuilder {

    public init() {}

    public func build(address: String, bdkNetwork: BdkNetwork) -> BdkResult<BdkAddress> {
        return BdkResult {
            try BdkAddressImpl(address: Address(address: address, network: bdkNetwork.ffiNetwork))
        }
    }

    public func build(script: BdkScript, network: BdkNetwork) -> BdkResult<BdkAddress> {
        let realBdkScript = script as! BdkScriptImpl
        return BdkResult {
            try BdkAddressImpl(
                address: Address.fromScript(
                    script: realBdkScript.toFfiScript(),
                    network: network.ffiNetwork
                )
            )
        }
    }

}
