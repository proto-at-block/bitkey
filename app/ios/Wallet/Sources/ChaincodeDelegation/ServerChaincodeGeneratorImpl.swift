import core
import Foundation
import Shared

public final class ServerChaincodeGeneratorImpl: Shared.ChaincodeDelegationServerDpubGenerator {
    public func generate(
        network: Shared.BitcoinNetworkType,
        serverRootPublicKey: String
    ) -> String {
        return core.serverAccountDpub(
            network: core.FfiNetwork(network),
            serverRootPubkey: serverRootPublicKey
        )
    }

    public init() {}
}

extension core.FfiNetwork {
    init(_ network: Shared.BitcoinNetworkType) {
        switch network {
        case .bitcoin:
            self = core.FfiNetwork.bitcoin
        case .regtest:
            self = core.FfiNetwork.regtest
        case .signet:
            self = core.FfiNetwork.signet
        case .testnet:
            self = core.FfiNetwork.testnet
        default: // default to mainnet
            self = core.FfiNetwork.bitcoin
        }
    }
}
