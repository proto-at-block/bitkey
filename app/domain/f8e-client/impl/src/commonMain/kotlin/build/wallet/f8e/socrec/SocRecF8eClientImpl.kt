package build.wallet.f8e.socrec

import bitkey.auth.AuthTokenScope
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.socrec.SocialChallenge
import build.wallet.bitkey.socrec.StartSocialChallengeRequestTrustedContact
import build.wallet.bitkey.socrec.TrustedContactRecoveryPakeKey
import build.wallet.crypto.PublicKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.di.Impl
import build.wallet.encrypt.XCiphertext
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.logging.withDescription
import build.wallet.f8e.socrec.models.*
import build.wallet.ktor.result.EmptyResponseBody
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.bodyResult
import build.wallet.ktor.result.setRedactedBody
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.*
import okio.ByteString

@Impl
@BitkeyInject(AppScope::class)
class SocRecF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : SocRecF8eClient {
  override suspend fun startChallenge(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    trustedContacts: List<StartSocialChallengeRequestTrustedContact>,
  ): Result<SocialChallenge, NetworkingError> {
    return f8eHttpClient
      .authenticated()
      .bodyResult<SocialChallengeResponseBody> {
        post("/api/accounts/${fullAccountId.serverId}/recovery/social-challenges") {
          withDescription("Start Social Recovery Challenge")
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId, AuthTokenScope.Recovery)
          setRedactedBody(
            StartSocialChallengeRequestBody(
              trustedContacts = trustedContacts
            )
          )
        }
      }
      .map { it.challenge }
  }

  override suspend fun getSocialChallengeStatus(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    challengeId: String,
  ): Result<SocialChallenge, NetworkingError> {
    return f8eHttpClient
      .authenticated()
      .bodyResult<SocialChallengeResponseBody> {
        get("/api/accounts/${fullAccountId.serverId}/recovery/social-challenges/$challengeId") {
          withDescription("Fetch Social Recovery Challenge")
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId, AuthTokenScope.Recovery)
        }
      }
      .map { it.challenge }
  }

  override suspend fun verifyChallenge(
    account: Account,
    recoveryRelationshipId: String,
    counter: Int,
  ): Result<ChallengeVerificationResponse, NetworkingError> {
    return f8eHttpClient
      .authenticated()
      .bodyResult<VerifyChallengeResponseBody> {
        post(
          "/api/accounts/${account.accountId.serverId}/recovery/verify-social-challenge"
        ) {
          withDescription("Verify challenge code")
          withEnvironment(account.config.f8eEnvironment)
          withAccountId(account.accountId, AuthTokenScope.Recovery)
          setRedactedBody(
            VerifyChallengeRequestBody(
              recoveryRelationshipId = recoveryRelationshipId,
              code = counter
            )
          )
        }
      }
      .map { it.challenge }
  }

  override suspend fun respondToChallenge(
    account: Account,
    socialChallengeId: String,
    trustedContactRecoveryPakePubkey: PublicKey<TrustedContactRecoveryPakeKey>,
    recoveryPakeConfirmation: ByteString,
    resealedDek: XCiphertext,
  ): Result<Unit, NetworkingError> {
    return f8eHttpClient
      .authenticated()
      .bodyResult<EmptyResponseBody> {
        put(
          "/api/accounts/${account.accountId.serverId}/recovery/social-challenges/$socialChallengeId"
        ) {
          withDescription("Verify challenge code")
          withEnvironment(account.config.f8eEnvironment)
          withAccountId(account.accountId, AuthTokenScope.Recovery)
          setRedactedBody(
            RespondToChallengeRequestBody(
              trustedContactRecoveryPakePubkey = trustedContactRecoveryPakePubkey,
              recoveryPakeConfirmation = recoveryPakeConfirmation,
              resealedDek = resealedDek
            )
          )
        }
      }.map { Unit }
  }
}
