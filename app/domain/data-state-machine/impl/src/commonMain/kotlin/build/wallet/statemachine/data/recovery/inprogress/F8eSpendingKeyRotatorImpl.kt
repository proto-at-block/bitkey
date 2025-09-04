package build.wallet.statemachine.data.recovery.inprogress

import bitkey.account.AccountConfigService
import bitkey.recovery.RecoveryStatusService
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.crypto.PublicKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.onboarding.CreateAccountKeysetF8eClient
import build.wallet.f8e.onboarding.SetActiveSpendingKeysetF8eClient
import build.wallet.recovery.LocalRecoveryAttemptProgress
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding

@BitkeyInject(AppScope::class)
class F8eSpendingKeyRotatorImpl(
  private val createAccountKeysetF8eClient: CreateAccountKeysetF8eClient,
  private val setActiveSpendingKeysetF8eClient: SetActiveSpendingKeysetF8eClient,
  private val accountConfigService: AccountConfigService,
  private val recoveryStatusService: RecoveryStatusService,
) : F8eSpendingKeyRotator {
  override suspend fun createSpendingKeyset(
    fullAccountId: FullAccountId,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
    hardwareProofOfPossession: HwFactorProofOfPossession,
    appSpendingKey: AppSpendingPublicKey,
    hardwareSpendingKey: HwSpendingPublicKey,
  ): Result<F8eSpendingKeyset, Error> {
    return coroutineBinding {
      val config = accountConfigService.activeOrDefaultConfig().value
      createAccountKeysetF8eClient
        .createKeyset(
          f8eEnvironment = config.f8eEnvironment,
          fullAccountId = fullAccountId,
          appSpendingKey = appSpendingKey,
          hardwareSpendingKey = hardwareSpendingKey,
          network = config.bitcoinNetworkType,
          appAuthKey = appAuthKey,
          hardwareProofOfPossession = hardwareProofOfPossession
        )
        .bind()
    }
  }

  override suspend fun activateSpendingKeyset(
    fullAccountId: FullAccountId,
    keyset: F8eSpendingKeyset,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
    hardwareProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, Error> {
    return coroutineBinding {
      val config = accountConfigService.activeOrDefaultConfig().value
      setActiveSpendingKeysetF8eClient
        .set(
          f8eEnvironment = config.f8eEnvironment,
          fullAccountId = fullAccountId,
          keysetId = keyset.keysetId,
          appAuthKey = appAuthKey,
          hwFactorProofOfPossession = hardwareProofOfPossession
        )
        .bind()

      recoveryStatusService.setLocalRecoveryProgress(
        LocalRecoveryAttemptProgress.ActivatedSpendingKeys(
          f8eSpendingKeyset = keyset
        )
      )
        .bind()
    }
  }
}
