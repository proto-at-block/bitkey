package bitkey.f8e.verify

import bitkey.verification.TxVerificationPolicy
import bitkey.verification.TxVerificationPolicyAuthFake
import bitkey.verification.VerificationThreshold
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Fake implementation of the transaction verification policy client.
 */
class TxVerifyPolicyF8eClientFake(
  private val clock: Clock,
) : TxVerifyPolicyF8eClient {
  private val thresholdState = MutableStateFlow<VerificationThreshold>(VerificationThreshold.Disabled)

  override suspend fun setPolicy(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    threshold: VerificationThreshold,
    hwFactorProofOfPossession: HwFactorProofOfPossession,
  ): Result<TxVerificationPolicy.DelayNotifyAuthorization?, Error> {
    if (threshold > thresholdState.value) {
      return Ok(
        TxVerificationPolicyAuthFake.copy(
          delayEndTime = clock.now() + 5.minutes
        )
      )
    }
    thresholdState.value = threshold
    return Ok(null)
  }

  override suspend fun getPolicy(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<VerificationThreshold, Error> {
    return Ok(thresholdState.value)
  }

  fun reset() {
    thresholdState.value = VerificationThreshold.Disabled
  }
}
