package build.wallet.inheritance

import build.wallet.bitkey.keybox.Keybox
import build.wallet.coroutines.flow.launchTicker
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.InheritanceFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.keybox.KeyboxDao
import build.wallet.logging.logDebug
import build.wallet.logging.logFailure
import build.wallet.logging.logVerbose
import build.wallet.logging.logWarn
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine

@BitkeyInject(AppScope::class)
class InheritanceMaterialSyncWorkerImpl(
  private val inheritanceService: InheritanceService,
  private val inheritanceRelationshipsProvider: InheritanceRelationshipsProvider,
  private val keyboxDao: KeyboxDao,
  private val featureFlag: InheritanceFeatureFlag,
  private val inheritanceSyncFrequency: InheritanceSyncFrequency,
) : InheritanceMaterialSyncWorker {
  override suspend fun executeWork() {
    combine(
      inheritanceRelationshipsProvider.endorsedInheritanceContacts,
      keyboxDao.activeKeybox(),
      featureFlag.flagValue()
    ) { _, keybox, flag ->
      flag to keybox
    }.collectLatest { (flag, keyboxResult) ->
      syncInheritanceMaterial(flag, keyboxResult)
    }
  }

  private suspend fun syncInheritanceMaterial(
    flagState: FeatureFlagValue.BooleanFlag,
    keyboxResult: Result<Keybox?, Error>,
  ) {
    if (!flagState.isEnabled()) {
      logDebug { "Skipping Inheritance Material Sync: Feature Disabled" }
      return
    }
    if (keyboxResult.isErr) {
      logWarn(throwable = keyboxResult.error) {
        "Skipping Inheritance Material Sync: Keybox Error"
      }
      return
    }
    val keybox = keyboxResult.get() ?: run {
      logDebug { "Skipping Inheritance Material Sync: No Keybox" }
      return
    }

    coroutineScope {
      launchTicker(inheritanceSyncFrequency.value) {
        inheritanceService.syncInheritanceMaterial(keybox)
          .logFailure { "Failed to sync inheritance material" }
          .onSuccess {
            logVerbose { "Inheritance Material Synced" }
            return@launchTicker
          }
      }
    }
  }
}
