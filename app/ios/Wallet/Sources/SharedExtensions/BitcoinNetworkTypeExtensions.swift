import core
import firmware
import Foundation
import Shared

public extension BitcoinNetworkType {

    /*
        Converts the Shared `BitcoinNetworkType` to the Firmware FFI's `BtcNetwork`
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

    /*
        Converts the Shared `BitcoinNetworkType` to the Core FFI's `Network`
     */
    var coreNetwork: core.Network {
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
