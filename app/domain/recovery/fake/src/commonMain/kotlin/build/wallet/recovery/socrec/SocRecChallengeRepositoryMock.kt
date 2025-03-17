package build.wallet.recovery.socrec

import build.wallet.bitkey.account.Account
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.relationships.ChallengeWrapper
import build.wallet.bitkey.relationships.EndorsedTrustedContact
import build.wallet.bitkey.socrec.TrustedContactRecoveryPakeKey
import build.wallet.crypto.PublicKey
import build.wallet.encrypt.XCiphertext
import build.wallet.f8e.socrec.SocialChallengeFake
import build.wallet.f8e.socrec.models.ChallengeVerificationResponse
import build.wallet.ktor.result.HttpError
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.collections.immutable.ImmutableList
import okio.ByteString

class SocRecChallengeRepositoryMock : SocRecChallengeRepository {
  override suspend fun startChallenge(
    accountId: FullAccountId,
    endorsedTrustedContacts: ImmutableList<EndorsedTrustedContact>,
    sealedDekMap: Map<String, XCiphertext>,
    isUsingSocRecFakes: Boolean,
  ): Result<ChallengeWrapper, Error> {
    return Ok(SocialChallengeFake)
  }

  override suspend fun getCurrentChallenge(
    accountId: FullAccountId,
    isUsingSocRecFakes: Boolean,
  ): Result<ChallengeWrapper?, Error> {
    return Ok(SocialChallengeFake)
  }

  override suspend fun getChallengeById(
    challengeId: String,
    accountId: FullAccountId,
    isUsingSocRecFakes: Boolean,
  ): Result<ChallengeWrapper, Error> {
    return Ok(SocialChallengeFake)
  }

  override suspend fun verifyChallenge(
    account: Account,
    recoveryRelationshipId: String,
    code: Int,
  ): Result<ChallengeVerificationResponse, Error> {
    return with(code) {
      when {
        code > 0 ->
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
    trustedContactRecoveryPakePubkey: PublicKey<TrustedContactRecoveryPakeKey>,
    recoveryPakeConfirmation: ByteString,
    resealedDek: XCiphertext,
  ): Result<Unit, Error> {
    return Ok(Unit)
  }
}
