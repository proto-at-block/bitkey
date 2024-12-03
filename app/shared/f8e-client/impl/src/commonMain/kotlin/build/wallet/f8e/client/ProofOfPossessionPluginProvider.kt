package build.wallet.f8e.client

import build.wallet.auth.AccessToken
import build.wallet.auth.AppAuthKeyMessageSigner
import build.wallet.auth.AuthTokenScope
import build.wallet.auth.AuthTokensRepository
import build.wallet.bitkey.app.AppAuthKey
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.AccountId
import build.wallet.crypto.PublicKey
import build.wallet.f8e.auth.AppFactorProofOfPossession
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.keybox.KeyboxDao
import build.wallet.logging.logFailure
import com.github.michaelbull.result.*
import io.ktor.client.plugins.api.*
import okio.ByteString.Companion.encodeUtf8

/**
 * Adds proof-of-possession headers for HW if provided, and for App if access tokens exist.
 */
class ProofOfPossessionPluginProvider(
  private val authTokensRepository: AuthTokensRepository,
  private val appAuthKeyMessageSigner: AppAuthKeyMessageSigner,
  private val keyboxDao: KeyboxDao,
) {
  fun getPlugin(
    accountId: AccountId?,
    appAuthKey: PublicKey<out AppAuthKey>?,
    hwProofOfPossession: HwFactorProofOfPossession?,
  ) = createClientPlugin("ProofOfPossessionPluginProvider") {
    onRequest { request, _ ->
      hwProofOfPossession?.let {
        request.headers.append(
          F8eHttpClientImpl.CONSTANT_PROOF_OF_POSSESSION_HW_HEADER,
          it.hwSignedToken
        )
      }

      accountId?.let {
        // We only use the [Global] auth token type here, because this PoP is only meant to
        // differentiate between tokens generated via the app public key or via the hw public key
        authTokensRepository.getAuthTokens(accountId, AuthTokenScope.Global).get()?.let { tokens ->
          val authKey: PublicKey<out AppAuthKey>? =
            when (appAuthKey) {
              null -> getAppAuthKeyFromKeybox()
              else -> appAuthKey
            }

          if (authKey != null) {
            createAppProofOfPossession(authKey, tokens.accessToken)
              .onSuccess { signedMessage ->
                // add our proof-of-possession http header
                request.headers.append(
                  F8eHttpClientImpl.CONSTANT_PROOF_OF_POSSESSION_APP_HEADER,
                  signedMessage.appSignedToken
                )
              }
              .logFailure { "Error signing access token for app proof of possession." }
          }
        }
      }
    }
  }

  private suspend fun getAppAuthKeyFromKeybox(): PublicKey<AppGlobalAuthKey>? {
    return keyboxDao.getActiveOrOnboardingKeybox()
      .logFailure {
        "Error reading active or onboarding keybox, not creating app proof of possession"
      }
      .fold(
        success = { keybox ->
          when {
            keybox != null -> {
              keybox.activeAppKeyBundle.authKey
            }

            else -> {
              null
            }
          }
        },
        failure = { null }
      )
  }

  private suspend fun createAppProofOfPossession(
    authKey: PublicKey<out AppAuthKey>,
    accessToken: AccessToken,
  ): Result<AppFactorProofOfPossession, Throwable> {
    return appAuthKeyMessageSigner
      .signMessage(
        publicKey = authKey,
        message = accessToken.raw.encodeUtf8()
      )
      // Wrapped into type for documentation sake.
      .map(::AppFactorProofOfPossession)
      .logFailure { "Error creating app proof of possession." }
  }
}
