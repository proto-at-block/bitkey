package build.wallet.recovery.socrec

import build.wallet.bitkey.account.Account
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.socrec.ProtectedCustomerEphemeralKey
import build.wallet.bitkey.socrec.ProtectedCustomerIdentityKey
import build.wallet.bitkey.socrec.SocialChallenge
import build.wallet.encrypt.XCiphertext
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.socrec.SocialRecoveryService
import build.wallet.f8e.socrec.models.ChallengeVerificationResponse
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.flatMap

class SocRecChallengeRepositoryImpl(
  private val socRec: SocialRecoveryService,
  private val socRecFake: SocialRecoveryService,
  private val socRecStartedChallengeDao: SocRecStartedChallengeDao,
) : SocRecChallengeRepository {
  override suspend fun startChallenge(
    accountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    isUsingSocRecFakes: Boolean,
    protectedCustomerEphemeralKey: ProtectedCustomerEphemeralKey,
    protectedCustomerIdentityKey: ProtectedCustomerIdentityKey,
  ): Result<SocialChallenge, Error> {
    return binding {
      getSocialRecoveryService(isUsingSocRecFakes).startChallenge(
        f8eEnvironment = f8eEnvironment,
        fullAccountId = accountId,
        protectedCustomerEphemeralKey = protectedCustomerEphemeralKey,
        protectedCustomerIdentityKey = protectedCustomerIdentityKey
      ).bind().also {
        socRecStartedChallengeDao.set(it.challengeId).bind()
      }
    }
  }

  override suspend fun getCurrentChallenge(
    accountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    isUsingSocRecFakes: Boolean,
  ): Result<SocialChallenge?, Error> {
    return socRecStartedChallengeDao.get()
      .flatMap { challengeId ->
        if (challengeId != null) {
          getChallengeById(
            challengeId = challengeId,
            accountId = accountId,
            f8eEnvironment = f8eEnvironment,
            isUsingSocRecFakes = isUsingSocRecFakes
          )
        } else {
          Ok(null)
        }
      }
  }

  override suspend fun getChallengeById(
    challengeId: String,
    accountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    isUsingSocRecFakes: Boolean,
  ): Result<SocialChallenge, Error> {
    return getSocialRecoveryService(isUsingSocRecFakes).getSocialChallengeStatus(
      f8eEnvironment = f8eEnvironment,
      fullAccountId = accountId,
      challengeId = challengeId
    )
  }

  override suspend fun verifyChallenge(
    account: Account,
    recoveryRelationshipId: String,
    code: String,
  ): Result<ChallengeVerificationResponse, Error> {
    return getSocialRecoveryService(account.config.isUsingSocRecFakes).verifyChallenge(
      account = account,
      recoveryRelationshipId = recoveryRelationshipId,
      code = code
    )
  }

  override suspend fun respondToChallenge(
    account: Account,
    socialChallengeId: String,
    sharedSecretCiphertext: XCiphertext,
  ): Result<Unit, Error> {
    return getSocialRecoveryService(account.config.isUsingSocRecFakes).respondToChallenge(
      account = account,
      socialChallengeId = socialChallengeId,
      sharedSecretCiphertext = sharedSecretCiphertext
    )
  }

  private fun getSocialRecoveryService(isUsingSocRecFakes: Boolean): SocialRecoveryService {
    return if (isUsingSocRecFakes) {
      socRecFake
    } else {
      socRec
    }
  }
}
