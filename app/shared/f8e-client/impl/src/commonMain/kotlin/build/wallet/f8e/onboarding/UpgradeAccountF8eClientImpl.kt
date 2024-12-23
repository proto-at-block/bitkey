package build.wallet.f8e.onboarding

import build.wallet.auth.AuthTokenScope
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.keys.DescriptorPublicKey
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.F8eSpendingPublicKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.keybox.KeyCrossDraft
import build.wallet.catchingResult
import build.wallet.crypto.PublicKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.CreateAccountClientErrorCode
import build.wallet.f8e.error.toF8eError
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
class UpgradeAccountF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : UpgradeAccountF8eClient {
  override suspend fun upgradeAccount(
    liteAccount: LiteAccount,
    keyCrossDraft: KeyCrossDraft.WithAppKeysAndHardwareKeys,
  ): Result<UpgradeAccountF8eClient.Success, F8eError<CreateAccountClientErrorCode>> {
    return f8eHttpClient
      .authenticated(
        f8eEnvironment = liteAccount.config.f8eEnvironment,
        accountId = liteAccount.accountId,
        authTokenScope = AuthTokenScope.Recovery
      )
      .bodyResult<ResponseBody> {
        post("/api/accounts/${liteAccount.accountId.serverId}/upgrade") {
          withDescription("Upgrade account on f8e")
          setRedactedBody(
            RequestBody(
              appKeyBundle = keyCrossDraft.appKeyBundle,
              hardwareKeyBundle = keyCrossDraft.hardwareKeyBundle,
              network = keyCrossDraft.config.bitcoinNetworkType
            )
          )
        }
      }
      .mapError { it.toF8eError<CreateAccountClientErrorCode>() }
      .map { response ->
        val verified = catchingResult {
          f8eHttpClient.wsmVerifier.verify(
            base58Message = DescriptorPublicKey(response.spending).xpub,
            signature = response.spendingSig,
            keyVariant = keyCrossDraft.config.f8eEnvironment.wsmIntegrityKeyVariant
          ).isValid
        }.getOrElse {
          false
        }

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

        UpgradeAccountF8eClient.Success(
          f8eSpendingKeyset =
            F8eSpendingKeyset(
              keysetId = response.keysetId,
              spendingPublicKey = F8eSpendingPublicKey(dpub = response.spending)
            ),
          fullAccountId = FullAccountId(response.accountId)
        )
      }
  }

  /**
   * Request body for the request to upgrade an account from Lite to Full.
   */
  @Serializable
  internal data class RequestBody(
    @SerialName("auth")
    val auth: AuthKeys,
    @SerialName("spending")
    val spending: SpendingKeys,
  ) : RedactedRequestBody {
    constructor(
      appKeyBundle: AppKeyBundle,
      hardwareKeyBundle: HwKeyBundle,
      network: BitcoinNetworkType,
    ) : this(
      auth =
        AuthKeys(
          app = appKeyBundle.authKey,
          hardware = hardwareKeyBundle.authKey
        ),
      spending =
        SpendingKeys(
          app = appKeyBundle.spendingKey,
          hardware = hardwareKeyBundle.spendingKey,
          network = network
        )
    )

    @Serializable
    data class AuthKeys(
      /** The global app auth key, corresponds to [AppGlobalAuthPublicKey] */
      val app: String,
      /** The hardware auth key. */
      val hardware: String,
    ) {
      constructor(
        app: PublicKey<AppGlobalAuthKey>,
        hardware: HwAuthPublicKey,
      ) : this(
        app = app.value,
        hardware = hardware.pubKey.value
      )
    }

    @Serializable
    data class SpendingKeys(
      /** The app spending key, corresponds to [AppSpendingPublicKey] */
      val app: String,
      /** The hardware spending key, corresponds to [HwSpendingPublicKey] */
      val hardware: String,
      /**The bitcoin network these keys were created on. */
      val network: String,
    ) {
      constructor(
        app: AppSpendingPublicKey,
        hardware: HwSpendingPublicKey,
        network: BitcoinNetworkType,
      ) : this(
        app = app.key.dpub,
        hardware = hardware.key.dpub,
        network = network.toJsonString()
      )
    }
  }

  /**
   * Response body for the request to upgrade an account from Lite to Full.
   */
  @Serializable
  internal data class ResponseBody(
    @SerialName("account_id")
    val accountId: String,
    @SerialName("keyset_id")
    val keysetId: String,
    @SerialName("spending")
    val spending: String,
    @SerialName("spending_sig")
    val spendingSig: String,
  ) : RedactedResponseBody
}
