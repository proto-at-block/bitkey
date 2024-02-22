package build.wallet.f8e.onboarding

import build.wallet.auth.AuthTokenScope
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.F8eSpendingPublicKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.keybox.KeyCrossDraft
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.CreateAccountClientErrorCode
import build.wallet.f8e.error.logF8eFailure
import build.wallet.f8e.error.toF8eError
import build.wallet.f8e.serialization.toJsonString
import build.wallet.ktor.result.bodyResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class UpgradeAccountServiceImpl(
  private val f8eHttpClient: F8eHttpClient,
) : UpgradeAccountService {
  override suspend fun upgradeAccount(
    liteAccount: LiteAccount,
    keyCrossDraft: KeyCrossDraft.WithAppKeysAndHardwareKeys,
  ): Result<UpgradeAccountService.Success, F8eError<CreateAccountClientErrorCode>> {
    return f8eHttpClient
      .authenticated(
        f8eEnvironment = liteAccount.config.f8eEnvironment,
        accountId = liteAccount.accountId,
        authTokenScope = AuthTokenScope.Recovery
      )
      .bodyResult<ResponseBody> {
        post("/api/accounts/${liteAccount.accountId.serverId}/upgrade") {
          setBody(
            RequestBody(
              appKeyBundle = keyCrossDraft.appKeyBundle,
              hardwareKeyBundle = keyCrossDraft.hardwareKeyBundle,
              network = keyCrossDraft.config.networkType
            )
          )
        }
      }
      .mapError { it.toF8eError<CreateAccountClientErrorCode>() }
      .logF8eFailure { "Failed to upgrade account on f8e" }
      .map { response ->
        UpgradeAccountService.Success(
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
  ) {
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
        app: AppGlobalAuthPublicKey,
        hardware: HwAuthPublicKey,
      ) : this(
        app = app.pubKey.value,
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
  )
}
