package bitkey.f8e.verify

import bitkey.f8e.privilegedactions.OptionalPrivilegedAction
import bitkey.verification.TxVerificationPolicy
import bitkey.verification.VerificationThreshold
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

/**
 * Fake implementation of the transaction verification policy client.
 */
class TxVerifyPolicyF8eClientFake(
  private val httpClient: F8eHttpClient? = null,
) : TxVerifyPolicyF8eClient {
  private var threshold: VerificationThreshold? = null
  override val f8eHttpClient: F8eHttpClient get() = httpClient ?: throw NotImplementedError()

  override suspend fun requestAction(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    request: TxVerificationUpdateRequest,
  ): Result<OptionalPrivilegedAction<VerificationThreshold>, Throwable> {
    this.threshold = request.threshold
    return Ok(OptionalPrivilegedAction.NotRequired(request.threshold))
  }

  override suspend fun getPolicy(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<TxVerificationPolicy?, Error> {
    return Ok(threshold?.let(TxVerificationPolicy::Active))
  }

  fun reset() {
    threshold = null
  }
}
