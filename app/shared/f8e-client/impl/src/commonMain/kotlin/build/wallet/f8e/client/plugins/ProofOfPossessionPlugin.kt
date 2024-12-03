package build.wallet.f8e.client.plugins

import build.wallet.auth.AccessToken
import build.wallet.auth.AppAuthKeyMessageSigner
import build.wallet.auth.AuthTokenScope
import build.wallet.auth.AuthTokensRepository
import build.wallet.bitkey.app.AppAuthKey
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.crypto.PublicKey
import build.wallet.f8e.auth.AppFactorProofOfPossession
import build.wallet.keybox.KeyboxDao
import build.wallet.logging.logFailure
import com.github.michaelbull.result.*
import io.ktor.client.plugins.api.*
import okio.ByteString.Companion.encodeUtf8

class ProofOfPossessionPluginConfig {
  lateinit var authTokensRepository: AuthTokensRepository
  lateinit var appAuthKeyMessageSigner: AppAuthKeyMessageSigner
  lateinit var keyboxDao: KeyboxDao
}

object ProofOfPossessionHeaders {
  const val APP_SIGNATURE = "X-App-Signature"
  const val HW_SIGNATURE = "X-Hw-Signature"
}

/**
 * A Ktor Client Plugin to add proof-of-possession headers for HW if provided,
 * and for App if access tokens exist.
 */
val ProofOfPossessionPlugin = createClientPlugin(
  "proof-of-possession",
  ::ProofOfPossessionPluginConfig
) {
  val authTokensRepository = pluginConfig.authTokensRepository
  val appAuthKeyMessageSigner = pluginConfig.appAuthKeyMessageSigner
  val keyboxDao = pluginConfig.keyboxDao

  onRequest { request, _ ->
    if (request.attributes.contains(HwProofOfPossessionAttribute)) {
      val hwProofOfPossession = request.attributes[HwProofOfPossessionAttribute]
      request.headers.append(
        ProofOfPossessionHeaders.HW_SIGNATURE,
        hwProofOfPossession.hwSignedToken
      )
    }

    if (request.attributes.contains(AccountIdAttribute)) {
      val accountId = request.attributes[AccountIdAttribute]
      val appAuthKey = request.attributes.getOrNull(AppAuthKeyAttribute)

      // We only use the [Global] auth token type here, because this PoP is only meant to
      // differentiate between tokens generated via the app public key or via the hw public key
      authTokensRepository.getAuthTokens(accountId, AuthTokenScope.Global).get()
        ?.let { tokens ->
          val authKey: PublicKey<out AppAuthKey>? =
            when (appAuthKey) {
              null -> keyboxDao.getAppAuthKey()
              else -> appAuthKey
            }

          if (authKey != null) {
            appAuthKeyMessageSigner.createAppProofOfPossession(authKey, tokens.accessToken)
              .onSuccess { signedMessage ->
                // add our proof-of-possession http header
                request.headers.append(
                  ProofOfPossessionHeaders.APP_SIGNATURE,
                  signedMessage.appSignedToken
                )
              }
              .logFailure { "Error signing access token for app proof of possession." }
          }
        }
    }
  }
}

private suspend fun KeyboxDao.getAppAuthKey(): PublicKey<AppGlobalAuthKey>? {
  return getActiveOrOnboardingKeybox()
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

private suspend fun AppAuthKeyMessageSigner.createAppProofOfPossession(
  authKey: PublicKey<out AppAuthKey>,
  accessToken: AccessToken,
): Result<AppFactorProofOfPossession, Throwable> {
  return signMessage(
    publicKey = authKey,
    message = accessToken.raw.encodeUtf8()
  )
    // Wrapped into type for documentation sake.
    .map(::AppFactorProofOfPossession)
    .logFailure { "Error creating app proof of possession." }
}
