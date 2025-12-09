package bitkey.f8e.verify

import bitkey.verification.TxVerificationPolicy
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.money.BitcoinMoney
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

/**
 * Fake implementation of the transaction verification policy client.
 */
class TxVerifyPolicyF8eClientFake : TxVerifyPolicyF8eClient {
  private var policy: TxVerificationPolicy? = null

  override suspend fun setPolicy(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    policy: TxVerificationPolicy,
    amountBtc: BitcoinMoney?,
    hwFactorProofOfPossession: HwFactorProofOfPossession,
  ): Result<TxVerificationPolicy, Error> {
    this.policy = policy
    return Ok(policy)
  }

  override suspend fun getPolicy(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<TxVerificationPolicy?, Error> {
    return Ok(policy)
  }

  fun reset() {
    policy = null
  }
}
