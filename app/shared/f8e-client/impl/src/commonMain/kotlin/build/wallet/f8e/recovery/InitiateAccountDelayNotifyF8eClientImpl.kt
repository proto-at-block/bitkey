package build.wallet.f8e.recovery

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.crypto.PublicKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.InitiateAccountDelayNotifyErrorCode
import build.wallet.f8e.error.toF8eError
import build.wallet.f8e.logging.withDescription
import build.wallet.f8e.recovery.InitiateAccountDelayNotifyF8eClient.SuccessfullyInitiated
import build.wallet.ktor.result.HttpError.UnhandledException
import build.wallet.ktor.result.RedactedRequestBody
import build.wallet.ktor.result.RedactedResponseBody
import build.wallet.ktor.result.bodyResult
import build.wallet.ktor.result.setRedactedBody
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.flatMap
import com.github.michaelbull.result.mapError
import io.ktor.client.request.post
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration

@BitkeyInject(AppScope::class)
class InitiateAccountDelayNotifyF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : InitiateAccountDelayNotifyF8eClient {
  override suspend fun initiate(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    // TODO(W-3092): Remove lostFactor
    lostFactor: PhysicalFactor,
    appGlobalAuthKey: PublicKey<AppGlobalAuthKey>,
    appRecoveryAuthKey: PublicKey<AppRecoveryAuthKey>,
    hwFactorProofOfPossession: HwFactorProofOfPossession?,
    delayPeriod: Duration?,
    hardwareAuthKey: HwAuthPublicKey,
  ): Result<SuccessfullyInitiated, F8eError<InitiateAccountDelayNotifyErrorCode>> {
    return f8eHttpClient.authenticated(
      f8eEnvironment = f8eEnvironment,
      accountId = fullAccountId,
      hwFactorProofOfPossession = hwFactorProofOfPossession
    )
      .bodyResult<ResponseBody> {
        post("/api/accounts/${fullAccountId.serverId}/delay-notify") {
          withDescription("Initiate D&N recovery.")
          setRedactedBody(
            RequestBody(
              // TODO(W-3092): Remove delayPeriodNumSec
              delayPeriodNumSec = delayPeriod?.inWholeSeconds?.toInt() ?: 20,
              auth =
                AuthKeypairBody(
                  appGlobal = appGlobalAuthKey.value,
                  appRecovery = appRecoveryAuthKey.value,
                  hardware = hardwareAuthKey.pubKey.value
                ),
              lostFactor = lostFactor.toServerString()
            )
          )
        }
      }
      .flatMap { body ->
        binding {
          val serverRecovery =
            body.pendingDelayNotify
              .toServerRecovery(fullAccountId)
              .mapError(::UnhandledException)
              .bind()
          SuccessfullyInitiated(serverRecovery)
        }
      }
      .mapError { it.toF8eError<InitiateAccountDelayNotifyErrorCode>() }
  }

  @Serializable
  private data class RequestBody(
    @SerialName("delay_period_num_sec")
    private val delayPeriodNumSec: Int,
    @SerialName("auth")
    private val auth: AuthKeypairBody,
    @SerialName("lost_factor")
    private val lostFactor: String,
  ) : RedactedRequestBody

  @Serializable
  private data class ResponseBody(
    @SerialName("pending_delay_notify")
    val pendingDelayNotify: ServerResponseBody,
  ) : RedactedResponseBody
}
