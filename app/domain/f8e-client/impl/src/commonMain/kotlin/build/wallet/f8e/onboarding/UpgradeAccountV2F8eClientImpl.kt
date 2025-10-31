package build.wallet.f8e.onboarding

import bitkey.auth.AuthTokenScope
import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.CreateAccountClientErrorCode
import bitkey.f8e.error.toF8eError
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.keybox.KeyCrossDraft
import build.wallet.catchingResult
import build.wallet.chaincode.delegation.ChaincodeDelegationServerKeyGenerator
import build.wallet.chaincode.delegation.PublicKeyUtils
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.logging.withDescription
import build.wallet.f8e.serialization.toJsonString
import build.wallet.f8e.wsmIntegrityKeyVariant
import build.wallet.ktor.result.RedactedRequestBody
import build.wallet.ktor.result.RedactedResponseBody
import build.wallet.ktor.result.bodyResult
import build.wallet.ktor.result.setRedactedBody
import build.wallet.logging.*
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import io.ktor.client.request.post
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@BitkeyInject(AppScope::class)
class UpgradeAccountV2F8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
  private val publicKeyUtils: PublicKeyUtils,
  private val serverKeyGenerator: ChaincodeDelegationServerKeyGenerator,
) : UpgradeAccountV2F8eClient {
  override suspend fun upgradeAccount(
    liteAccount: LiteAccount,
    keyCrossDraft: KeyCrossDraft.WithAppKeysAndHardwareKeys,
  ): Result<UpgradeAccountV2F8eClient.Success, F8eError<CreateAccountClientErrorCode>> {
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

    return f8eHttpClient
      .authenticated()
      .bodyResult<ResponseBody> {
        post("/api/v2/accounts/${liteAccount.accountId.serverId}/upgrade") {
          withDescription("Upgrade account on f8e")
          withEnvironment(liteAccount.config.f8eEnvironment)
          withAccountId(liteAccount.accountId, AuthTokenScope.Recovery)
          setRedactedBody(
            RequestBody(
              auth = AuthKeys(
                app = keyCrossDraft.appKeyBundle.authKey.value,
                hardware = keyCrossDraft.hardwareKeyBundle.authKey.pubKey.value
              ),
              spend = SpendingKeys(
                app = appSpendingPubKey,
                hardware = hardwareSpendingPubKey,
                network = keyCrossDraft.config.bitcoinNetworkType.toJsonString()
              )
            )
          )
        }
      }
      .mapError { it.toF8eError<CreateAccountClientErrorCode>() }
      .map { response ->
        val verified = catchingResult {
          f8eHttpClient.wsmVerifier.verifyHexMessage(
            hexMessage = response.serverRawPublicKey,
            signature = response.serverPubIntegritySig,
            keyVariant = keyCrossDraft.config.f8eEnvironment.wsmIntegrityKeyVariant
          ).isValid
        }.getOrElse {
          false
        }

        if (!verified) {
          // Note: do not remove the '[wsm_integrity_failure]' from the message. We alert on this string in Datadog.
          logError {
            "[wsm_integrity_failure] WSM integrity signature verification failed: " +
              "${response.serverPubIntegritySig} : " +
              "${response.serverRawPublicKey} : " +
              response.keysetId
          }
          // Just log, don't fail the call.
        }

        // Use the returned server public key to create a root xpub for the server
        val f8eSpendingKeyset = serverKeyGenerator.generatePrivateSpendingKeyset(
          network = keyCrossDraft.config.bitcoinNetworkType,
          serverPublicKey = response.serverRawPublicKey,
          keysetId = response.keysetId
        )

        UpgradeAccountV2F8eClient.Success(
          f8eSpendingKeyset = f8eSpendingKeyset,
          fullAccountId = FullAccountId(liteAccount.accountId.serverId)
        )
      }
  }

  /**
   * Request body for the request to upgrade an account from Lite to Full.
   */
  @Serializable
  private data class RequestBody(
    @SerialName("auth")
    val auth: AuthKeys,
    @SerialName("spend")
    val spend: SpendingKeys,
  ) : RedactedRequestBody

  @Serializable
  private class AuthKeys(
    /** The global app auth key, corresponds to [AppGlobalAuthPublicKey] */
    @SerialName("app_pub")
    val app: String,
    /** The hardware auth key. */
    @SerialName("hardware_pub")
    val hardware: String,
  )

  @Serializable
  private class SpendingKeys(
    /** The app spending key */
    @SerialName("app_pub")
    val app: String,
    /** The hardware spending key */
    @SerialName("hardware_pub")
    val hardware: String,
    /** The bitcoin network these keys were created on. */
    val network: String,
  )

  /**
   * Response body for the request to upgrade an account from Lite to a Full CCD account
   */
  @Serializable
  private data class ResponseBody(
    @SerialName("keyset_id")
    val keysetId: String,
    @SerialName("server_pub")
    val serverRawPublicKey: String,
    @SerialName("server_pub_integrity_sig")
    val serverPubIntegritySig: String,
  ) : RedactedResponseBody
}
