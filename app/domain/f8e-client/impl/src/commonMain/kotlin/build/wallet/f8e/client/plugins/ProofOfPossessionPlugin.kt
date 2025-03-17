package build.wallet.f8e.client.plugins

import bitkey.auth.AccessToken
import bitkey.auth.AuthTokenScope
import build.wallet.auth.AppAuthKeyMessageSigner
import build.wallet.auth.AuthTokensService
import build.wallet.bitkey.app.AppAuthKey
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.crypto.PublicKey
import build.wallet.f8e.auth.AppFactorProofOfPossession
import build.wallet.keybox.KeyboxDao
import build.wallet.logging.logFailure
import com.github.michaelbull.result.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.HttpRequestPipeline
import okio.ByteString.Companion.encodeUtf8

class ProofOfPossessionPluginConfig {
  lateinit var authTokensService: AuthTokensService
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
  val authTokensRepository = pluginConfig.authTokensService
  val appAuthKeyMessageSigner = pluginConfig.appAuthKeyMessageSigner
  val keyboxDao = pluginConfig.keyboxDao
  client.requestPipeline.intercept(HttpRequestPipeline.Before) {
    if (context.attributes.contains(HwProofOfPossessionAttribute)) {
      val hwProofOfPossession = context.attributes[HwProofOfPossessionAttribute]
      context.headers.append(
        ProofOfPossessionHeaders.HW_SIGNATURE,
        hwProofOfPossession.hwSignedToken
      )
    }

    if (context.attributes.contains(AccountIdAttribute)) {
      val accountId = context.attributes[AccountIdAttribute]
      val appAuthKey = context.attributes.getOrNull(AppAuthKeyAttribute)

      // We only use the [Global] auth token type here, because this PoP is only meant to
      // differentiate between tokens generated via the app public key or via the hw public key
      authTokensRepository.getTokens(accountId, AuthTokenScope.Global).get()
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
                context.headers.append(
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
