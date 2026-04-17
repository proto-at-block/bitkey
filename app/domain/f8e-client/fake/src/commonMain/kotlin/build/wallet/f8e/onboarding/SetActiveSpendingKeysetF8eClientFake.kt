package build.wallet.f8e.onboarding

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.f8e.recovery.SignedKeysetVerificationResponse
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class SetActiveSpendingKeysetF8eClientFake : SetActiveSpendingKeysetF8eClient {
  var setResult: Result<SignedKeysetVerificationResponse?, NetworkingError> = Ok(null)
  var lastSetArguments: SetArguments? = null

  data class SetArguments(
    val fullAccountId: FullAccountId,
    val keysetId: String,
    val appAuthKey: PublicKey<AppGlobalAuthKey>,
    val proof: PrivilegedActionProof,
  )

  override suspend fun set(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    keysetId: String,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
    proof: PrivilegedActionProof,
  ): Result<SignedKeysetVerificationResponse?, NetworkingError> {
    lastSetArguments = SetArguments(
      fullAccountId = fullAccountId,
      keysetId = keysetId,
      appAuthKey = appAuthKey,
      proof = proof
    )
    return setResult
  }

  fun reset() {
    setResult = Ok(null)
    lastSetArguments = null
  }
}
