package build.wallet.bdk.legacy

import build.wallet.bdk.bindings.BdkAddressIndex
import build.wallet.bdk.bindings.BdkAddressInfo
import build.wallet.bdk.bindings.BdkBalance
import build.wallet.bdk.bindings.BdkBlockchain
import build.wallet.bdk.bindings.BdkPartiallySignedTransaction
import build.wallet.bdk.bindings.BdkProgress
import build.wallet.bdk.bindings.BdkResult
import build.wallet.bdk.bindings.BdkScript
import build.wallet.bdk.bindings.BdkTransactionDetails
import build.wallet.bdk.bindings.BdkUtxo
import build.wallet.bdk.bindings.BdkWallet

/**
 * Constructed by [BdkWalletFactoryImpl].
 */
internal class BdkWalletImpl(
  val ffiWallet: FfiWallet,
) : BdkWallet {
  override fun syncBlocking(
    blockchain: BdkBlockchain,
    progress: BdkProgress?,
  ): BdkResult<Unit> {
    require(blockchain is LegacyBdkBlockchainImpl)
    return runCatchingBdkError {
      ffiWallet.sync(
        blockchain = blockchain.ffiBlockchain,
        progress = progress?.ffiProgress
      )
    }
  }

  override fun listTransactionsBlocking(
    includeRaw: Boolean,
  ): BdkResult<List<BdkTransactionDetails>> {
    return runCatchingBdkError {
      ffiWallet.listTransactions(includeRaw = includeRaw).map { it.bdkTransactionDetails }
    }
  }

  override fun getBalanceBlocking(): BdkResult<BdkBalance> {
    return runCatchingBdkError { ffiWallet.getBalance().bdkBalance }
  }

  override fun signBlocking(psbt: BdkPartiallySignedTransaction): BdkResult<Boolean> {
    require(psbt is BdkPartiallySignedTransactionImpl)
    return runCatchingBdkError { ffiWallet.sign(psbt = psbt.ffiPsbt, signOptions = null) }
  }

  override fun getAddressBlocking(addressIndex: BdkAddressIndex): BdkResult<BdkAddressInfo> {
    return runCatchingBdkError { ffiWallet.getAddress(addressIndex.ffiAddressIndex).bdkAddressInfo }
  }

  override fun isMineBlocking(script: BdkScript): BdkResult<Boolean> {
    return runCatchingBdkError { ffiWallet.isMine(script.ffiScript) }
  }

  override fun listUnspentBlocking(): BdkResult<List<BdkUtxo>> {
    return runCatchingBdkError { ffiWallet.listUnspent().map { it.bdkUtxo } }
  }
}
