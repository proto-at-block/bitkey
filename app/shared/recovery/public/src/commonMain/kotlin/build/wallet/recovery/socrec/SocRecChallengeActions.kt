package build.wallet.recovery.socrec

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.socrec.ChallengeWrapper
import build.wallet.bitkey.socrec.EndorsedTrustedContact
import build.wallet.encrypt.XCiphertext
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Result
import kotlinx.collections.immutable.ImmutableList

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
    endorsedTrustedContacts: ImmutableList<EndorsedTrustedContact>,
    sealedDekMap: Map<String, XCiphertext>,
  ): Result<ChallengeWrapper, Error> =
    repository.startChallenge(
      accountId = accountId,
      f8eEnvironment = f8eEnvironment,
      endorsedTrustedContacts = endorsedTrustedContacts,
      sealedDekMap = sealedDekMap,
      isUsingSocRecFakes = isUsingSocRecFakes
    )

  suspend fun getCurrentChallenge(): Result<ChallengeWrapper?, Error> =
    repository.getCurrentChallenge(
      accountId = accountId,
      f8eEnvironment = f8eEnvironment,
      isUsingSocRecFakes = isUsingSocRecFakes
    )

  suspend fun getChallengeById(challengeId: String): Result<ChallengeWrapper, Error> =
    repository.getChallengeById(
      challengeId = challengeId,
      accountId = accountId,
      f8eEnvironment = f8eEnvironment,
      isUsingSocRecFakes = isUsingSocRecFakes
    )
}
