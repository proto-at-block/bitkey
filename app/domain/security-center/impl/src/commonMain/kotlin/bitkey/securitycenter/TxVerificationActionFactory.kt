package bitkey.securitycenter

import bitkey.verification.TxVerificationService
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.TxVerificationFeatureFlag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

interface TxVerificationActionFactory {
  suspend fun create(): Flow<SecurityAction?>
}

@BitkeyInject(AppScope::class)
class TxVerificationActionFactoryImpl(
  private val flag: TxVerificationFeatureFlag,
  private val txVerificationService: TxVerificationService,
) : TxVerificationActionFactory {
  override suspend fun create(): Flow<SecurityAction?> {
    return combine(
      flag.flagValue(),
      txVerificationService.getCurrentThreshold()
    ) { flag, threshold ->
      when {
        !flag.value || !threshold.isOk -> null
        else -> TxVerificationAction(threshold.value)
      }
    }
  }
}
