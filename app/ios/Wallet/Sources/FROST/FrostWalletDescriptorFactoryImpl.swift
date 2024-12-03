import core
import Foundation
import Shared

class FrostWalletDescriptorFactoryImpl: Shared.FrostWalletDescriptorFactory {
    func watchingWalletDescriptor(softwareKeybox: SoftwareKeybox) -> WatchingWalletDescriptor {
        let networkType = softwareKeybox.networkType
        let coreDescriptor = computeFrostWalletDescriptor(
            aggPublicKey: softwareKeybox.shareDetails.keyCommitments.aggregatePublicKey.asString(),
            network: networkType.coreNetwork
        )

        return WatchingWalletDescriptor(
            identifier: softwareKeybox.id,
            networkType: networkType,
            receivingDescriptor: BitcoinDescriptorWatching(raw: "tr(\(coreDescriptor.external)"),
            changeDescriptor: BitcoinDescriptorWatching(raw: "tr(\(coreDescriptor.change)")
        )
    }

    func spendingWalletDescriptor(softwareKeybox: SoftwareKeybox) -> SpendingWalletDescriptor {
        let networkType = softwareKeybox.networkType
        let coreDescriptor = computeFrostWalletDescriptor(
            aggPublicKey: softwareKeybox.shareDetails.keyCommitments.aggregatePublicKey.asString(),
            network: networkType.coreNetwork
        )

        return SpendingWalletDescriptor(
            identifier: softwareKeybox.id,
            networkType: networkType,
            receivingDescriptor: BitcoinDescriptorSpending(raw: "tr(\(coreDescriptor.external)"),
            changeDescriptor: BitcoinDescriptorSpending(raw: "tr(\(coreDescriptor.change)")
        )
    }
}
