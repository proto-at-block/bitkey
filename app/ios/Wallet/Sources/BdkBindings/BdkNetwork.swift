import BitcoinDevKit
import Shared

extension Network {
    var bdkNetwork: BdkNetwork {
        switch self {
        case .bitcoin: return BdkNetwork.bitcoin
        case .testnet: return BdkNetwork.testnet
        case .signet: return BdkNetwork.signet
        case .regtest: return BdkNetwork.regtest
        }
    }
}

extension BdkNetwork {

    var ffiNetwork : Network {
        switch self {
        case BdkNetwork.bitcoin: return .bitcoin
        case BdkNetwork.regtest: return .regtest
        case BdkNetwork.testnet: return .testnet
        case BdkNetwork.signet: return .signet
        default: fatalError()
        }
    }

}
