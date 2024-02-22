package build.wallet.f8e.client

import build.wallet.auth.AccessToken
import build.wallet.auth.AppAuthKeyMessageSigner
import build.wallet.auth.AuthTokenScope
import build.wallet.auth.AuthTokensRepository
import build.wallet.bitkey.app.AppAuthPublicKey
import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.auth.AppFactorProofOfPossession
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.keybox.KeyboxDao
import build.wallet.logging.LogLevel
import build.wallet.logging.log
import build.wallet.logging.logFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.fold
import com.github.michaelbull.result.get
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onSuccess
import io.ktor.client.plugins.api.createClientPlugin
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
    appAuthKey: AppAuthPublicKey?,
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
          val authKey: AppAuthPublicKey? =
            when (appAuthKey) {
              null -> getAppAuthKeyFromKeybox()
              else -> {
                log { "Attempting to use provided app auth key for app proof of possession" }
                appAuthKey
              }
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
          } else {
            log { "No app auth key found, not including app proof of possession in this request" }
          }
        }
      }
    }
  }

  private suspend fun getAppAuthKeyFromKeybox(): AppGlobalAuthPublicKey? {
    return keyboxDao.getActiveOrOnboardingKeybox()
      .fold(
        success = { keybox ->
          when {
            keybox != null -> {
              log {
                "Found active or onboarding keybox, using its app auth key to create app proof of possession"
              }
              keybox.activeKeyBundle.authKey
            }

            else -> {
              log { "No active or onboarding keybox found, not creating app proof of possession" }
              null
            }
          }
        },
        failure = { error ->
          log(
            level = LogLevel.Error,
            throwable = error
          ) { "Error reading active or onboarding keybox, not creating app proof of possession" }
          null
        }
      )
  }

  private suspend fun createAppProofOfPossession(
    authKey: AppAuthPublicKey,
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
