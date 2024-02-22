package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.BdkNetwork
import build.wallet.bdk.bindings.BdkTransactionDetails
import build.wallet.bdk.bindings.BdkWallet
import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.bitcoin.transactions.BitcoinTransactionFake

class BdkTransactionMapperMock : BdkTransactionMapper {
  override suspend fun createTransaction(
    bdkTransaction: BdkTransactionDetails,
    bdkNetwork: BdkNetwork,
    bdkWallet: BdkWallet,
  ): BitcoinTransaction {
    return BitcoinTransactionFake
  }
}
