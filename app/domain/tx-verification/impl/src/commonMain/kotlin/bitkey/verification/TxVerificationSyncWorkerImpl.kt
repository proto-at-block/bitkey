package bitkey.verification

import bitkey.f8e.verify.TxVerifyPolicyF8eClient
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.TxVerificationFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.keybox.KeyboxDao
import build.wallet.logging.logFailure
import build.wallet.logging.logInfo
import build.wallet.worker.BackgroundStrategy
import build.wallet.worker.RetryStrategy
import build.wallet.worker.RunStrategy
import com.github.michaelbull.result.get
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.seconds

@BitkeyInject(AppScope::class)
class TxVerificationSyncWorkerImpl(
  private val dao: TxVerificationDao,
  private val f8eClient: TxVerifyPolicyF8eClient,
  private val keyboxDao: KeyboxDao,
  private val featureFlag: TxVerificationFeatureFlag,
) : TxVerificationSyncWorker {
  override val runStrategy: Set<RunStrategy> = setOf(
    RunStrategy.Startup(),
    RunStrategy.OnEvent(
      observer = keyboxDao.activeKeybox(),
      backgroundStrategy = BackgroundStrategy.Wait
    ),
    RunStrategy.OnEvent(
      observer = featureFlag.flagValue(),
      backgroundStrategy = BackgroundStrategy.Wait
    )
  )
  override val retryStrategy: RetryStrategy = RetryStrategy.Always(
    delay = 30.seconds
  )

  override suspend fun executeWork() {
    if (!featureFlag.isEnabled()) {
      logInfo { "Tx Verification feature flag is disabled. Skipping policy sync." }
      return
    }
    val keybox = keyboxDao.activeKeybox().first()
      .logFailure { "Failed to get keybox for Tx Verification policy sync" }
      .get() ?: return
    val f8eEnvironment = keybox.config.f8eEnvironment
    val fullAccountId = keybox.fullAccountId
    val currentPolicy = dao.getActivePolicy().first()
      .logFailure { "Failed to get local tx policy for sync" }
      .get()

    val newPolicy = f8eClient.getPolicy(
      f8eEnvironment = f8eEnvironment,
      fullAccountId = fullAccountId
    ).get() ?: return

    if (currentPolicy?.threshold != newPolicy) {
      logInfo { "Server verification policy does not match local data. Updating." }
      dao.setActivePolicy(newPolicy)
    }
  }
}
