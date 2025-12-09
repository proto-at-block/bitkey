import BitcoinDevKitLegacy
import Shared

public class BdkWalletFactoryImpl: BdkWalletFactory {

    public init() {}

    public func walletBlocking(
        descriptor: String,
        changeDescriptor: String?,
        network: BdkNetwork,
        databaseConfig: BdkDatabaseConfig
    ) -> BdkResult<BdkWallet> {
        var ffiNetwork: Network {
            switch network {
            case .bitcoin:
                return Network.bitcoin
            case .signet:
                return Network.signet
            case .regtest:
                return Network.regtest
            case .testnet:
                return Network.testnet
            default:
                fatalError()
            }
        }

        var ffiDatabaseConfig: DatabaseConfig {
            switch databaseConfig {
            case is BdkDatabaseConfig.Memory:
                return DatabaseConfig.memory
            case let sqliteConfig as BdkDatabaseConfig.Sqlite:
                return DatabaseConfig
                    .sqlite(config: SqliteDbConfiguration(path: sqliteConfig.config.path))
            default:
                fatalError()
            }
        }

        return BdkResult {
            return try BdkWalletImpl(
                wallet: Wallet(
                    descriptor: Descriptor(descriptor: descriptor, network: ffiNetwork),
                    changeDescriptor: changeDescriptor.map {
                        try Descriptor(descriptor: $0, network: ffiNetwork)
                    },
                    network: ffiNetwork,
                    databaseConfig: ffiDatabaseConfig
                )
            )
        }
    }
}
