package build.wallet.f8e.recovery

import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.app.AppRecoveryAuthPublicKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.InitiateAccountDelayNotifyErrorCode
import com.github.michaelbull.result.Result
import kotlin.time.Duration

/**
 * Request to the server to initiate the Delay and Notify flow so we can activate Recovery Mode
 */
interface InitiateAccountDelayNotifyService {
  /**
   * Initiates a delay and notify recovery instance on F8E.
   *
   * @param f8eEnvironment The f8e environment to use.
   * @param fullAccountId The customer's accountId.
   * @param delayPeriodNumSec The number of seconds the delay period should be.
   * Overrides the server's default value.
   * @param lostFactor The factor that is lost and we are recovering.
   * @param appGlobalAuthKey The new application authentication public key to rotate onto upon completion.
   * @param hardwareAuthKey The new hardware authentication public key to rotate onto upon completion.
   * @param hwFactorProofOfPossession For lost app initiations, a proof of possession for the requesting
   * factor needs to be provided. F8E services automatically add App proof-of-possession to all
   * authenticated endpoints. But HW proofs of possession must be provided externally when needed.
   * required.
   * @return The successfully initiated recovery.
   */
  suspend fun initiate(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    lostFactor: PhysicalFactor,
    // TODO(W-2863): use auth keys for Recovery V2
    appGlobalAuthKey: AppGlobalAuthPublicKey,
    appRecoveryAuthKey: AppRecoveryAuthPublicKey,
    hwFactorProofOfPossession: HwFactorProofOfPossession? = null,
    delayPeriod: Duration? = null,
    hardwareAuthKey: HwAuthPublicKey,
  ): Result<SuccessfullyInitiated, F8eError<InitiateAccountDelayNotifyErrorCode>>

  data class SuccessfullyInitiated(
    val serverRecovery: ServerRecovery,
  )
}
