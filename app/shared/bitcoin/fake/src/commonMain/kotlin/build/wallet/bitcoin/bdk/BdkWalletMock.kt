package build.wallet.bitcoin.bdk

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bdk.bindings.BdkAddressIndex
import build.wallet.bdk.bindings.BdkAddressInfo
import build.wallet.bdk.bindings.BdkBalance
import build.wallet.bdk.bindings.BdkBlockchain
import build.wallet.bdk.bindings.BdkPartiallySignedTransaction
import build.wallet.bdk.bindings.BdkProgress
import build.wallet.bdk.bindings.BdkResult
import build.wallet.bdk.bindings.BdkResult.Err
import build.wallet.bdk.bindings.BdkResult.Ok
import build.wallet.bdk.bindings.BdkScript
import build.wallet.bdk.bindings.BdkTransactionDetails
import build.wallet.bdk.bindings.BdkWallet
import build.wallet.bdk.bindings.someBdkError

class BdkWalletMock(
  turbine: (String) -> Turbine<Any>,
) : BdkWallet {
  override fun syncBlocking(
    blockchain: BdkBlockchain,
    progress: BdkProgress?,
  ): BdkResult<Unit> {
    return Ok(Unit)
  }

  override fun listTransactionsBlocking(
    includeRaw: Boolean,
  ): BdkResult<List<BdkTransactionDetails>> {
    return Ok(listOf())
  }

  override fun getBalanceBlocking(): BdkResult<BdkBalance> {
    return Ok(BdkBalanceMock)
  }

  override fun signBlocking(psbt: BdkPartiallySignedTransaction): BdkResult<Boolean> {
    return Ok(true)
  }

  var getAddressBlockingResult: BdkResult<BdkAddressInfo>? = null

  override fun getAddressBlocking(addressIndex: BdkAddressIndex): BdkResult<BdkAddressInfo> {
    println("getAddressBlocking")
    return getAddressBlockingResult ?: Ok(BdkAddressInfoMock)
  }

  val isMineCalls = turbine("is mine calls")
  var isMineResultMap: Map<BdkScript, Boolean> = emptyMap()

  override fun isMineBlocking(script: BdkScript): BdkResult<Boolean> {
    isMineCalls += script
    return isMineResultMap[script]?.let { Ok(it) }
      ?: Err(someBdkError)
  }

  fun reset() {
    isMineResultMap = emptyMap()
  }
}
