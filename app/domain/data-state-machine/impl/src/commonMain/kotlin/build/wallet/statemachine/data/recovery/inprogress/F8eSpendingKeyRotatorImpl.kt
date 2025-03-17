package build.wallet.statemachine.data.recovery.inprogress

import bitkey.account.AccountConfigService
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
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding

@BitkeyInject(AppScope::class)
class F8eSpendingKeyRotatorImpl(
  private val createAccountKeysetF8eClient: CreateAccountKeysetF8eClient,
  private val setActiveSpendingKeysetF8eClient: SetActiveSpendingKeysetF8eClient,
  private val accountConfigService: AccountConfigService,
) : F8eSpendingKeyRotator {
  override suspend fun rotateSpendingKey(
    fullAccountId: FullAccountId,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
    hardwareProofOfPossession: HwFactorProofOfPossession,
    appSpendingKey: AppSpendingPublicKey,
    hardwareSpendingKey: HwSpendingPublicKey,
  ): Result<F8eSpendingKeyset, Error> {
    return coroutineBinding {
      val config = accountConfigService.activeOrDefaultConfig().value
      val f8eSpendingKeyset =
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

      setActiveSpendingKeysetF8eClient
        .set(
          f8eEnvironment = config.f8eEnvironment,
          fullAccountId = fullAccountId,
          keysetId = f8eSpendingKeyset.keysetId,
          appAuthKey = appAuthKey,
          hwFactorProofOfPossession = hardwareProofOfPossession
        )
        .bind()

      f8eSpendingKeyset
    }
  }
}
