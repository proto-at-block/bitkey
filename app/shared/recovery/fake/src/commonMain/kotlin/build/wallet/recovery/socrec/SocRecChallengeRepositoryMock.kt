package build.wallet.recovery.socrec

import build.wallet.bitkey.account.Account
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.socrec.ProtectedCustomerEphemeralKey
import build.wallet.bitkey.socrec.ProtectedCustomerIdentityKey
import build.wallet.bitkey.socrec.SocialChallenge
import build.wallet.encrypt.XCiphertext
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.socrec.SocialChallengeFake
import build.wallet.f8e.socrec.models.ChallengeVerificationResponse
import build.wallet.f8e.socrec.models.ChallengeVerificationResponseFake
import build.wallet.ktor.result.HttpError
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class SocRecChallengeRepositoryMock : SocRecChallengeRepository {
  override suspend fun startChallenge(
    accountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    isUsingSocRecFakes: Boolean,
    protectedCustomerEphemeralKey: ProtectedCustomerEphemeralKey,
    protectedCustomerIdentityKey: ProtectedCustomerIdentityKey,
  ): Result<SocialChallenge, Error> {
    return Ok(SocialChallengeFake)
  }

  override suspend fun getCurrentChallenge(
    accountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    isUsingSocRecFakes: Boolean,
  ): Result<SocialChallenge?, Error> {
    return Ok(SocialChallengeFake)
  }

  override suspend fun getChallengeById(
    challengeId: String,
    accountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    isUsingSocRecFakes: Boolean,
  ): Result<SocialChallenge, Error> {
    return Ok(SocialChallengeFake)
  }

  override suspend fun verifyChallenge(
    account: Account,
    recoveryRelationshipId: String,
    code: String,
  ): Result<ChallengeVerificationResponse, Error> {
    return with(code) {
      when {
        first().isLetterOrDigit() ->
          Ok(ChallengeVerificationResponseFake)

        else ->
          Err(
            HttpError.NetworkError(Exception("Invalid code"))
          )
      }
    }
  }

  override suspend fun respondToChallenge(
    account: Account,
    socialChallengeId: String,
    sharedSecretCiphertext: XCiphertext,
  ): Result<Unit, Error> {
    return Ok(Unit)
  }
}
