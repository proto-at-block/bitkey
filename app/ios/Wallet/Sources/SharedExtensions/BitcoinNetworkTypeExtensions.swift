import Foundation
import Shared
import core

public extension BitcoinNetworkType {
    
    /*
        Converts the Shared `BitcoinNetworkType` to the Core `BtcNetwork`
     */
    var btcNetwork: BtcNetwork {
        switch self {
        case .bitcoin: return .bitcoin
        case .testnet: return .testnet
        case .signet: return .signet
        case .regtest: return .regtest
        default:
           fatalError()
        }
    }
}
