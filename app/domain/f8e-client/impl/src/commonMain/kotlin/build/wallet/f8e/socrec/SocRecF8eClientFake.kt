package build.wallet.f8e.socrec

import build.wallet.bitkey.account.Account
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.relationships.PakeCode
import build.wallet.bitkey.socrec.*
import build.wallet.crypto.PublicKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.di.Fake
import build.wallet.encrypt.XCiphertext
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.socrec.models.ChallengeVerificationResponse
import build.wallet.ktor.result.HttpError
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.test.HttpResponseMock
import build.wallet.platform.random.UuidGenerator
import build.wallet.relationships.RelationshipsCryptoFake
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrThrow
import io.ktor.http.*
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import kotlin.random.Random

/**
 * A functional fake implementation of the SocialRecoveryService for testing and local development.
 *
 * @param uuidGenerator - the uuid to use for generating random ids
 */
@Fake
@BitkeyInject(AppScope::class)
class SocRecF8eClientFake(
  private val uuidGenerator: UuidGenerator,
) : SocRecF8eClient {
  private val relationshipsCrypto = RelationshipsCryptoFake()
  val challengeResponses = mutableListOf<SocialChallengeResponse>()
  val challenges = mutableListOf<FakeServerChallenge>()
  var fakeNetworkingError: NetworkingError? = null

  data class FakeServerChallenge(
    var response: SocialChallenge,
    var recoveryRelationshipId: String? = null,
    val protectedCustomerRecoveryPakePubkey: PublicKey<ProtectedCustomerRecoveryPakeKey>,
    val sealedDek: String,
  )

  override suspend fun startChallenge(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    trustedContacts: List<StartSocialChallengeRequestTrustedContact>,
  ): Result<SocialChallenge, NetworkingError> {
    val code = Random.nextInt(0, 100)
    val challengeId = uuidGenerator.random() + code
    val challenge =
      SocialChallenge(
        challengeId = challengeId,
        counter = code,
        responses = challengeResponses
      )

    val pakeCode = PakeCode("12345678901".encodeUtf8())
    challenges.add(
      FakeServerChallenge(
        response = challenge,
        protectedCustomerRecoveryPakePubkey = relationshipsCrypto.generateProtectedCustomerRecoveryPakeKey(
          pakeCode
        )
          .getOrThrow().publicKey,
        sealedDek = "sealed-dek"
      )
    )
    return fakeNetworkingError?.let(::Err) ?: Ok(challenge)
  }

  override suspend fun verifyChallenge(
    account: Account,
    recoveryRelationshipId: String,
    counter: Int,
  ): Result<ChallengeVerificationResponse, NetworkingError> {
    val challenge = challenges.find { it.response.counter == counter }
    if (challenge == null) {
      return Err(
        HttpError.ClientError(HttpResponseMock(HttpStatusCode.Unauthorized))
      )
    }

    return Ok(
      ChallengeVerificationResponse(
        socialChallengeId = challenge.response.challengeId,
        protectedCustomerRecoveryPakePubkey = challenge.protectedCustomerRecoveryPakePubkey,
        sealedDek = challenge.sealedDek
      )
    )
  }

  override suspend fun respondToChallenge(
    account: Account,
    socialChallengeId: String,
    trustedContactRecoveryPakePubkey: PublicKey<TrustedContactRecoveryPakeKey>,
    recoveryPakeConfirmation: ByteString,
    resealedDek: XCiphertext,
  ): Result<Unit, NetworkingError> {
    val challenge =
      challenges
        .find { it.response.challengeId == socialChallengeId }
    if (challenge == null) {
      return Err(
        HttpError.ClientError(HttpResponseMock(HttpStatusCode.Unauthorized))
      )
    }
    challenge.response =
      challenge.response.copy(
        responses =
          challenge.response.responses +
            SocialChallengeResponse(
              recoveryRelationshipId = challenge.recoveryRelationshipId!!,
              trustedContactRecoveryPakePubkey = trustedContactRecoveryPakePubkey,
              recoveryPakeConfirmation = recoveryPakeConfirmation,
              resealedDek = resealedDek
            )
      )
    return Ok(Unit)
  }

  override suspend fun getSocialChallengeStatus(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    challengeId: String,
  ): Result<SocialChallenge, NetworkingError> {
    return fakeNetworkingError?.let(::Err) ?: run {
      val challenge =
        challenges
          .find { it.response.challengeId == challengeId }
      challenge
        ?.let { Ok(it.response) }
        ?: Err(HttpError.ClientError(HttpResponseMock(HttpStatusCode.Unauthorized)))
    }
  }

  fun reset() {
    challenges.clear()
    fakeNetworkingError = null
  }
}
