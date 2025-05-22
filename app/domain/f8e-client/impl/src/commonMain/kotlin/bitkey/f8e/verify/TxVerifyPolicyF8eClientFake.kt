package bitkey.f8e.verify

import bitkey.verification.TxVerificationPolicy
import bitkey.verification.VerificationThreshold
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fake implementation of the transaction verification policy client.
 *
 * TODO: This needs to be moved to the `fake` module once the real API is
 *       available, where it can then be used for testing.
 */
class TxVerifyPolicyF8eClientFake : TxVerifyPolicyF8eClient {
  private val policy = MutableStateFlow<VerificationThreshold?>(null)

  override suspend fun setPolicy(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    threshold: VerificationThreshold,
  ): Result<TxVerificationPolicy, Throwable> {
    policy.value = threshold

    return Ok(
      TxVerificationPolicy(
        id = TxVerificationPolicy.Id("fake-id"),
        threshold = threshold
      )
    )
  }
}
