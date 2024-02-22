package build.wallet.bdk.bindings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * https://github.com/bitcoindevkit/bdk-ffi/blob/v0.28.0/bdk-ffi/src/bdk.udl#L220
 */
interface BdkWallet {
  fun syncBlocking(
    blockchain: BdkBlockchain,
    progress: BdkProgress?,
  ): BdkResult<Unit>

  fun listTransactionsBlocking(includeRaw: Boolean): BdkResult<List<BdkTransactionDetails>>

  fun getBalanceBlocking(): BdkResult<BdkBalance>

  fun signBlocking(psbt: BdkPartiallySignedTransaction): BdkResult<Boolean>

  fun getAddressBlocking(addressIndex: BdkAddressIndex): BdkResult<BdkAddressInfo>

  fun isMineBlocking(script: BdkScript): BdkResult<Boolean>
}

suspend fun BdkWallet.sync(
  blockchain: BdkBlockchain,
  progress: BdkProgress?,
): BdkResult<Unit> {
  return withContext(Dispatchers.BdkIO) {
    syncBlocking(blockchain, progress)
  }
}

suspend fun BdkWallet.listTransactions(
  includeRaw: Boolean,
): BdkResult<List<BdkTransactionDetails>> {
  return withContext(Dispatchers.BdkIO) {
    listTransactionsBlocking(includeRaw)
  }
}

suspend fun BdkWallet.getBalance(): BdkResult<BdkBalance> {
  return withContext(Dispatchers.BdkIO) {
    getBalanceBlocking()
  }
}

suspend fun BdkWallet.sign(psbt: BdkPartiallySignedTransaction): BdkResult<Boolean> {
  return withContext(Dispatchers.BdkIO) {
    signBlocking(psbt)
  }
}

suspend fun BdkWallet.getAddress(addressIndex: BdkAddressIndex): BdkResult<BdkAddressInfo> {
  return withContext(Dispatchers.BdkIO) {
    getAddressBlocking(addressIndex)
  }
}

suspend fun BdkWallet.isMine(script: BdkScript): BdkResult<Boolean> {
  return withContext(Dispatchers.BdkIO) {
    isMineBlocking(script)
  }
}
