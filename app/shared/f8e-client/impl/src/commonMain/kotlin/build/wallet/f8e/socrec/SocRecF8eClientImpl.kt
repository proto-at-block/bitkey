package build.wallet.f8e.socrec

import build.wallet.auth.AuthTokenScope
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
      .authenticated(
        f8eEnvironment = f8eEnvironment,
        accountId = fullAccountId,
        authTokenScope = AuthTokenScope.Recovery
      )
      .bodyResult<SocialChallengeResponseBody> {
        post("/api/accounts/${fullAccountId.serverId}/recovery/social-challenges") {
          withDescription("Start Social Recovery Challenge")
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
      .authenticated(
        f8eEnvironment = f8eEnvironment,
        accountId = fullAccountId,
        authTokenScope = AuthTokenScope.Recovery
      )
      .bodyResult<SocialChallengeResponseBody> {
        get("/api/accounts/${fullAccountId.serverId}/recovery/social-challenges/$challengeId") {
          withDescription("Fetch Social Recovery Challenge")
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
      .authenticated(
        f8eEnvironment = account.config.f8eEnvironment,
        accountId = account.accountId,
        authTokenScope = AuthTokenScope.Recovery
      ).bodyResult<VerifyChallengeResponseBody> {
        post(
          "/api/accounts/${account.accountId.serverId}/recovery/verify-social-challenge"
        ) {
          withDescription("Verify challenge code")
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
      .authenticated(
        f8eEnvironment = account.config.f8eEnvironment,
        accountId = account.accountId,
        authTokenScope = AuthTokenScope.Recovery
      ).bodyResult<EmptyResponseBody> {
        put(
          "/api/accounts/${account.accountId.serverId}/recovery/social-challenges/$socialChallengeId"
        ) {
          withDescription("Verify challenge code")
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
