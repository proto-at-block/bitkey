package build.wallet.f8e.onboarding

import bitkey.account.LiteAccountConfig
import bitkey.account.SoftwareAccountConfig
import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.CreateAccountClientErrorCode
import bitkey.f8e.error.toF8eError
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.keys.DescriptorPublicKey
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.f8e.*
import build.wallet.bitkey.keybox.KeyCrossDraft
import build.wallet.catchingResult
import build.wallet.chaincode.delegation.ChaincodeDelegationServerKeyGenerator
import build.wallet.chaincode.delegation.PublicKeyUtils
import build.wallet.crypto.PublicKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.logging.withDescription
import build.wallet.f8e.onboarding.model.*
import build.wallet.f8e.serialization.toJsonString
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
  private val serverKeyGenerator: ChaincodeDelegationServerKeyGenerator,
  private val publicKeyUtils: PublicKeyUtils,
) : CreateFullAccountF8eClient, CreateLiteAccountF8eClient, CreateSoftwareAccountF8eClient, CreatePrivateFullAccountF8eClient {
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
          spendingPublicKey = F8eSpendingPublicKey(dpub = response.spending),
          privateWalletRootXpub = null // Legacy accounts don't have this
        ),
        fullAccountId = FullAccountId(response.accountId)
      )
    }
  }

  override suspend fun createPrivateAccount(
    keyCrossDraft: KeyCrossDraft.WithAppKeysAndHardwareKeys,
  ): Result<CreateFullAccountF8eClient.Success, F8eError<CreateAccountClientErrorCode>> {
    val appSpendingPubKey =
      publicKeyUtils
        .extractPublicKey(keyCrossDraft.appKeyBundle.spendingKey.key)
        .result
        .getOrElse { error("Failed to extract app spending public key") }

    val hardwareSpendingPubKey =
      publicKeyUtils
        .extractPublicKey(keyCrossDraft.hardwareKeyBundle.spendingKey.key)
        .result
        .getOrElse { error("Failed to extract hardware spending public key") }

    return createPrivateAccount(
      f8eEnvironment = keyCrossDraft.config.f8eEnvironment,
      requestBody = CreateAccountV2RequestBody(
        auth = FullCreateAccountV2AuthKeys(
          appGlobalAuthPublicKey = keyCrossDraft.appKeyBundle.authKey.value,
          hardwareAuthPublicKey = keyCrossDraft.hardwareKeyBundle.authKey.pubKey.value,
          recoveryAuthPublicKey = keyCrossDraft.appKeyBundle.recoveryAuthKey.value
        ),
        spend = FullCreateAccountV2SpendingKeys(
          app = appSpendingPubKey,
          hardware = hardwareSpendingPubKey,
          network = keyCrossDraft.config.bitcoinNetworkType.toJsonString()
        ),
        isTestAccount = if (keyCrossDraft.config.isTestAccount) true else null
      )
    ).map { response ->
      val verified = catchingResult {
        f8eHttpClient.wsmVerifier.verifyHexMessage(
          hexMessage = response.serverPub,
          signature = response.serverPubIntegritySig,
          keyVariant = keyCrossDraft.config.f8eEnvironment.wsmIntegrityKeyVariant
        ).isValid
      }.getOrElse { false }

      if (!verified) {
        // Note: do not remove the '[wsm_integrity_failure]' from the message. We alert on this string in Datadog.
        logError {
          "[wsm_integrity_failure] WSM integrity signature verification failed: " +
            "${response.serverPubIntegritySig} : " +
            "${response.serverPub} : " +
            "${response.accountId} : " +
            response.keysetId
        }
        // Just log, don't fail the call.
      }

      // Use the returned server public key to create a root xpub for the server
      val f8eSpendingKeyset = serverKeyGenerator.generatePrivateSpendingKeyset(
        network = keyCrossDraft.config.bitcoinNetworkType,
        serverPublicKey = response.serverPub,
        keysetId = response.keysetId
      )

      CreateFullAccountF8eClient.Success(
        f8eSpendingKeyset = f8eSpendingKeyset,
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

  private suspend inline fun createPrivateAccount(
    f8eEnvironment: F8eEnvironment,
    requestBody: CreateAccountV2RequestBody,
  ): Result<CreateAccountV2ResponseBody, F8eError<CreateAccountClientErrorCode>> {
    return f8eHttpClient.unauthenticated()
      .bodyResult<CreateAccountV2ResponseBody> {
        post("/api/v2/accounts") {
          withEnvironment(f8eEnvironment)
          withDescription("Create a private account on f8e")
          setRedactedBody(requestBody)
        }
      }
      .mapError { it.toF8eError<CreateAccountClientErrorCode>() }
  }
}

internal fun ChaincodeDelegationServerKeyGenerator.generatePrivateSpendingKeyset(
  network: BitcoinNetworkType,
  serverPublicKey: String,
  keysetId: String,
): F8eSpendingKeyset {
  val serverXpub = generateRootExtendedPublicKey(network, serverPublicKey)
  val serverDpub = generateAccountDescriptorPublicKey(network, serverXpub)
  return F8eSpendingKeyset(
    keysetId = keysetId,
    spendingPublicKey = F8eSpendingPublicKey(dpub = serverDpub),
    privateWalletRootXpub = serverXpub
  )
}
