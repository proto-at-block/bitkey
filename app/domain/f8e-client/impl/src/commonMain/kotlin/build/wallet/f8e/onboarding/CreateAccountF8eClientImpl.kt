package build.wallet.f8e.onboarding

import bitkey.account.LiteAccountConfig
import bitkey.account.SoftwareAccountConfig
import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.CreateAccountClientErrorCode
import bitkey.f8e.error.toF8eError
import build.wallet.bitcoin.keys.DescriptorPublicKey
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.f8e.*
import build.wallet.bitkey.keybox.KeyCrossDraft
import build.wallet.catchingResult
import build.wallet.crypto.PublicKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.logging.withDescription
import build.wallet.f8e.onboarding.model.*
import build.wallet.f8e.wsmIntegrityKeyVariant
import build.wallet.ktor.result.bodyResult
import build.wallet.ktor.result.setRedactedBody
import build.wallet.logging.logError
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import io.ktor.client.request.*

@BitkeyInject(AppScope::class)
class CreateAccountF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : CreateFullAccountF8eClient, CreateLiteAccountF8eClient, CreateSoftwareAccountF8eClient {
  // Full Account
  override suspend fun createAccount(
    keyCrossDraft: KeyCrossDraft.WithAppKeysAndHardwareKeys,
  ): Result<CreateFullAccountF8eClient.Success, F8eError<CreateAccountClientErrorCode>> {
    return createAccount<FullCreateAccountResponseBody>(
      f8eEnvironment = keyCrossDraft.config.f8eEnvironment,
      requestBody = FullCreateAccountRequestBody(
        appKeyBundle = keyCrossDraft.appKeyBundle,
        hardwareKeyBundle = keyCrossDraft.hardwareKeyBundle,
        network = keyCrossDraft.config.bitcoinNetworkType,
        isTestAccount = if (keyCrossDraft.config.isTestAccount) true else null
      )
    ).map { response ->
      val verified = catchingResult {
        f8eHttpClient.wsmVerifier.verify(
          base58Message = DescriptorPublicKey(response.spending).xpub,
          signature = response.spendingSig,
          keyVariant = keyCrossDraft.config.f8eEnvironment.wsmIntegrityKeyVariant
        ).isValid
      }.getOrElse { false }

      if (!verified) {
        // Note: do not remove the '[wsm_integrity_failure]' from the message. We alert on this string in Datadog.
        logError {
          "[wsm_integrity_failure] WSM integrity signature verification failed: " +
            "${response.spendingSig} : " +
            "${response.spending} : " +
            "${response.accountId} : " +
            response.keysetId
        }
        // Just log, don't fail the call.
      }

      CreateFullAccountF8eClient.Success(
        f8eSpendingKeyset = F8eSpendingKeyset(
          keysetId = response.keysetId,
          spendingPublicKey = F8eSpendingPublicKey(dpub = response.spending)
        ),
        fullAccountId = FullAccountId(response.accountId)
      )
    }
  }

  // Lite Account
  override suspend fun createAccount(
    recoveryKey: PublicKey<AppRecoveryAuthKey>,
    config: LiteAccountConfig,
  ): Result<LiteAccountId, F8eError<CreateAccountClientErrorCode>> {
    return createAccount<LiteCreateAccountResponseBody>(
      f8eEnvironment = config.f8eEnvironment,
      requestBody = LiteCreateAccountRequestBody(
        appRecoveryAuthKey = recoveryKey,
        isTestAccount = if (config.isTestAccount) true else null
      )
    ).map { response ->
      LiteAccountId(serverId = response.accountId)
    }
  }

  override suspend fun createAccount(
    authKey: PublicKey<AppGlobalAuthKey>,
    recoveryAuthKey: PublicKey<AppRecoveryAuthKey>,
    accountConfig: SoftwareAccountConfig,
  ): Result<SoftwareAccountId, F8eError<CreateAccountClientErrorCode>> {
    return createAccount<SoftwareCreateAccountResponseBody>(
      f8eEnvironment = accountConfig.f8eEnvironment,
      requestBody = SoftwareCreateAccountRequestBody(
        appGlobalAuthKey = authKey,
        appRecoveryAuthKey = recoveryAuthKey,
        isTestAccount = accountConfig.isTestAccount
      )
    ).map { SoftwareAccountId(it.accountId) }
  }

  private suspend inline fun <reified ResponseBody : CreateAccountResponseBody> createAccount(
    f8eEnvironment: F8eEnvironment,
    requestBody: CreateAccountRequestBody,
  ): Result<ResponseBody, F8eError<CreateAccountClientErrorCode>> {
    return f8eHttpClient.unauthenticated()
      .bodyResult<ResponseBody> {
        post("/api/accounts") {
          withEnvironment(f8eEnvironment)
          withDescription("Create account on f8e")
          setRedactedBody(requestBody)
        }
      }
      .mapError { it.toF8eError<CreateAccountClientErrorCode>() }
  }
}
