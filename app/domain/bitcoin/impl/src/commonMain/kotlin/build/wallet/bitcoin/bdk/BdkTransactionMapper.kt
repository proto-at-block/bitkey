package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.BdkNetwork
import build.wallet.bdk.bindings.BdkTransactionDetails
import build.wallet.bdk.bindings.BdkWallet
import build.wallet.bitcoin.transactions.BitcoinTransaction

interface BdkTransactionMapper {
  /**
   * Creates a [BitcoinTransaction] from [BdkTransactionDetails]
   */
  suspend fun createTransaction(
    bdkTransaction: BdkTransactionDetails,
    bdkNetwork: BdkNetwork,
    bdkWallet: BdkWallet,
  ): BitcoinTransaction
}
