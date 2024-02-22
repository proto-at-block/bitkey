import BitcoinDevKit
import Shared

public class BdkAddressBuilderImpl : BdkAddressBuilder {

    public init() {}
    
    public func build(address: String, bdkNetwork: BdkNetwork) -> BdkResult<BdkAddress> {
        return BdkResult {
            BdkAddressImpl(address: try Address(address: address, network: bdkNetwork.ffiNetwork))
        }
    }

    public func build(script: BdkScript, network: BdkNetwork) -> BdkResult<BdkAddress> {
        let realBdkScript = script as! BdkScriptImpl
        return BdkResult {
            BdkAddressImpl(
                address: try Address.fromScript(
                    script: realBdkScript.ffiScript,
                    network: network.ffiNetwork
                )
            )
        }
    }

}
