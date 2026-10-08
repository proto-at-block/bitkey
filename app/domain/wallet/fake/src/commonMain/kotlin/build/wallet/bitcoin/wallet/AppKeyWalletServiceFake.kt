package build.wallet.bitcoin.wallet

import app.cash.turbine.Turbine
import build.wallet.bitcoin.address.BitcoinAddressInfo
import build.wallet.bitcoin.address.someBitcoinAddress
import build.wallet.bitkey.spending.SpendingKeyset
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.awaitCancellation

class AppKeyWalletServiceFake(
  turbine: (String) -> Turbine<Any>,
) : AppKeyWalletService {
  val generateAddressInfoCalls = turbine("App Key Wallet generate address info calls")
  val fullScanCalls = turbine("App Key Wallet full scan calls")
  val prepareCalls = turbine("App Key Wallet prepare sweep calls")
  val signCalls = turbine("App Key Wallet sign sweep calls")
  val broadcastCalls = turbine("App Key Wallet broadcast sweep calls")
  var addressInfoResult: Result<BitcoinAddressInfo, AppKeyWalletServiceError> =
    Ok(BitcoinAddressInfo(someBitcoinAddress, 0u))
  var fullScanResult: Result<AppKeyWalletBalance, AppKeyWalletServiceError> =
    Ok(AppKeyWalletBalance(0uL, 0uL))
  var prepareResult: Result<AppKeyWalletSweepPreview, AppKeyWalletServiceError>? = null
  var signResult: Result<Unit, AppKeyWalletServiceError>? = null
  var broadcastResult: Result<AppKeyWalletSweepResult, AppKeyWalletServiceError>? = null
  var suspendScan = false

  override suspend fun generateAddressInfo(keyset: SpendingKeyset): Result<BitcoinAddressInfo, AppKeyWalletServiceError> {
    generateAddressInfoCalls.add(keyset)
    return addressInfoResult
  }

  override suspend fun fullScan(keyset: SpendingKeyset): Result<AppKeyWalletBalance, AppKeyWalletServiceError> {
    fullScanCalls.add(keyset)
    if (suspendScan) awaitCancellation()
    return fullScanResult
  }

  override suspend fun prepareSweep(keyset: SpendingKeyset): Result<AppKeyWalletSweepPreview, AppKeyWalletServiceError> {
    prepareCalls.add(keyset)
    return requireNotNull(prepareResult) { "prepareResult must be configured before prepareSweep" }
  }

  override suspend fun signSweep(
    keyset: SpendingKeyset,
    previewId: AppKeyWalletSweepId,
  ): Result<Unit, AppKeyWalletServiceError> {
    signCalls.add(keyset to previewId)
    return requireNotNull(signResult) { "signResult must be configured before signSweep" }
  }

  override suspend fun broadcastSweep(
    keyset: SpendingKeyset,
    previewId: AppKeyWalletSweepId,
  ): Result<AppKeyWalletSweepResult, AppKeyWalletServiceError> {
    broadcastCalls.add(keyset to previewId)
    return requireNotNull(broadcastResult) { "broadcastResult must be configured before broadcastSweep" }
  }

  fun reset() {
    addressInfoResult = Ok(BitcoinAddressInfo(someBitcoinAddress, 0u))
    fullScanResult = Ok(AppKeyWalletBalance(0uL, 0uL))
    prepareResult = null
    signResult = null
    broadcastResult = null
    suspendScan = false
  }
}
