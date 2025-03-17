package build.wallet.bdk.bindings

import build.wallet.bdk.bindings.BdkResult.Ok
import com.ionspin.kotlin.bignum.integer.toBigInteger

class BdkWalletMock : BdkWallet {
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
    return Ok(
      BdkBalance(
        0u.toBigInteger(),
        0u.toBigInteger(),
        0u.toBigInteger(),
        0u.toBigInteger(),
        0u.toBigInteger(),
        0u.toBigInteger()
      )
    )
  }

  override fun signBlocking(psbt: BdkPartiallySignedTransaction): BdkResult<Boolean> {
    return Ok(true)
  }

  override fun getAddressBlocking(addressIndex: BdkAddressIndex): BdkResult<BdkAddressInfo> {
    return Ok(BdkAddressInfo(0, BdkAddressMock()))
  }

  override fun isMineBlocking(script: BdkScript): BdkResult<Boolean> {
    return Ok(false)
  }

  override fun listUnspentBlocking(): BdkResult<List<BdkUtxo>> {
    return Ok(listOf())
  }
}
