package build.wallet.recovery.socrec

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.socrec.ProtectedCustomerEphemeralKey
import build.wallet.bitkey.socrec.ProtectedCustomerIdentityKey
import build.wallet.bitkey.socrec.SocialChallenge
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Result

/**
 * Wraps the Social Challenge repository with an account to provide the
 * actions that a user can take on the social challenge screens.
 */
class SocRecChallengeActions(
  private val repository: SocRecChallengeRepository,
  private val accountId: FullAccountId,
  private val f8eEnvironment: F8eEnvironment,
  private val isUsingSocRecFakes: Boolean,
) {
  suspend fun startChallenge(
    protectedCustomerEphemeralKey: ProtectedCustomerEphemeralKey,
    protectedCustomerIdentityKey: ProtectedCustomerIdentityKey,
  ): Result<SocialChallenge, Error> =
    repository.startChallenge(
      accountId = accountId,
      f8eEnvironment = f8eEnvironment,
      isUsingSocRecFakes = isUsingSocRecFakes,
      protectedCustomerEphemeralKey = protectedCustomerEphemeralKey,
      protectedCustomerIdentityKey = protectedCustomerIdentityKey
    )

  suspend fun getCurrentChallenge(): Result<SocialChallenge?, Error> =
    repository.getCurrentChallenge(
      accountId = accountId,
      f8eEnvironment = f8eEnvironment,
      isUsingSocRecFakes = isUsingSocRecFakes
    )

  suspend fun getChallengeById(challengeId: String): Result<SocialChallenge, Error> =
    repository.getChallengeById(
      challengeId = challengeId,
      accountId = accountId,
      f8eEnvironment = f8eEnvironment,
      isUsingSocRecFakes = isUsingSocRecFakes
    )
}
