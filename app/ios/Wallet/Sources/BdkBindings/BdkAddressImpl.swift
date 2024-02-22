import BitcoinDevKit
import Shared

class BdkAddressImpl : BdkAddress {
    
    private let address: Address
    
    init(address: Address) {
        self.address = address
    }

    func asString() -> String {
        return address.asString()
    }
    
    func scriptPubkey() -> BdkScript {
        return BdkScriptImpl(ffiScript: address.scriptPubkey())
    }

    func network() -> BdkNetwork {
        return address.network().bdkNetwork
    }
    
    func isValidForNetwork(network: BdkNetwork) -> Bool {
        return address.isValidForNetwork(network: network.ffiNetwork)
    }
}
