package build.wallet.f8e.mobilepay

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class MobilePaySigningF8eClientMock(
  turbine: (String) -> Turbine<Any>,
) : MobilePaySigningF8eClient {
  val signWithSpecificKeysetCalls =
    turbine("sign bitcoin transaction with f8e using specific keyset calls")

  var signWithSpecificKeysetResult: Result<Psbt, NetworkingError>? =
    null

  override suspend fun signWithSpecificKeyset(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    keysetId: String,
    psbt: Psbt,
  ): Result<Psbt, NetworkingError> {
    signWithSpecificKeysetCalls += psbt
    return signWithSpecificKeysetResult ?: Ok(psbt.copy(base64 = "${psbt.base64} ${signature(keysetId)}"))
  }

  fun reset() {
    signWithSpecificKeysetResult = null
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
