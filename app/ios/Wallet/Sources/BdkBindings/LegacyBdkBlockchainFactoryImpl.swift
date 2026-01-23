import BitcoinDevKitLegacy
import Shared

/// Factory for creating legacy BDK blockchains.
/// Uses BitcoinDevKitLegacy Swift bindings (legacy BDK).
public class LegacyBdkBlockchainFactoryImpl: LegacyBdkBlockchainFactory {

    public init() {}

    public func blockchainBlocking(config: BdkBlockchainConfig) -> BdkResult<BdkBlockchain> {
        var ffiBlockchainConfig: BlockchainConfig {
            switch config {
            case let electrumConfig as BdkBlockchainConfig.Electrum:
                return BlockchainConfig.electrum(
                    config: ElectrumConfig(
                        url: electrumConfig.config.url,
                        socks5: electrumConfig.config.socks5,
                        retry: UInt8(electrumConfig.config.retry),
                        timeout: electrumConfig.config.timeout as? UInt8,
                        stopGap: UInt64(electrumConfig.config.stopGap),
                        validateDomain: electrumConfig.config.validateDomain
                    )
                )
            default:
                fatalError()
            }
        }
        return BdkResult {
            try LegacyBdkBlockchainImpl(ffiBlockchain: Blockchain(config: ffiBlockchainConfig))
        }
    }

}
