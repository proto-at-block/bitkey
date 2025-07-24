package build.wallet.f8e.mobilepay

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import bitkey.verification.FakePendingVerification
import bitkey.verification.PendingTransactionVerification
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.display.BitcoinDisplayUnit
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class MobilePaySigningF8eClientMock(
  turbine: (String) -> Turbine<Any>,
) : MobilePaySigningF8eClient {
  val signWithSpecificKeysetCalls =
    turbine("sign bitcoin transaction with f8e using specific keyset calls")

  var signWithSpecificKeysetResult: Result<Psbt, Error>? = null
  var requestVerificationForSigningResult: Result<PendingTransactionVerification, Error> = Ok(FakePendingVerification)

  override suspend fun signWithSpecificKeyset(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    keysetId: String,
    psbt: Psbt,
  ): Result<Psbt, Error> {
    signWithSpecificKeysetCalls += psbt
    return signWithSpecificKeysetResult ?: Ok(psbt.copy(base64 = "${psbt.base64} ${signature(keysetId)}"))
  }

  override suspend fun requestVerificationForSigning(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    keysetId: String,
    psbt: Psbt,
    fiatCurrency: FiatCurrency,
    bitcoinDisplayUnit: BitcoinDisplayUnit,
  ): Result<PendingTransactionVerification, Error> {
    return requestVerificationForSigningResult
  }

  fun reset() {
    signWithSpecificKeysetResult = null
    requestVerificationForSigningResult = Ok(FakePendingVerification)
  }
}

private fun signature(keysetId: String) = "is_server_signed($keysetId)"

fun Psbt.isServerSignedWithKeyset(keysetId: String) = this.base64.contains(signature(keysetId))

fun Psbt.isServerSignedWithActiveKeyset() =
  this.base64.contains(
    signature("ACTIVE")
  )

fun Psbt.isServerSigned() =
  Regex(
    """is_server_signed\(\S+\)"""
  ).containsMatchIn(this.base64)
