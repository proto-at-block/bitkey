package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.BdkUtxo
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.transactions.BitcoinTransaction
import uniffi.bdk.LocalOutput
import uniffi.bdk.TxDetails
import uniffi.bdk.Wallet

/**
 * Mapper for converting BDK v2 FFI types to domain types.
 */
interface BdkTransactionMapperV2 {
  /**
   * Creates a [BitcoinTransaction] from BDK v2's [TxDetails].
   *
   * @param txDetails The transaction details from BDK v2.
   * @param wallet The wallet instance, used to determine if outputs are owned by us.
   * @param networkType The Bitcoin network type, used for address conversion.
   */
  suspend fun createTransaction(
    txDetails: TxDetails,
    wallet: Wallet,
    networkType: BitcoinNetworkType,
  ): BitcoinTransaction

  /**
   * Creates a [BdkUtxo] from BDK v2's [LocalOutput].
   *
   * @param localOutput The local output from BDK v2.
   */
  fun createUtxo(localOutput: LocalOutput): BdkUtxo
}
