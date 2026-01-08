package bitkey.recovery.fundslost

import bitkey.notifications.NotificationChannel
import bitkey.notifications.NotificationsService
import bitkey.notifications.NotificationsService.NotificationStatus
import build.wallet.account.AccountService
import build.wallet.account.getAccountOrNull
import build.wallet.bitkey.account.FullAccount
import build.wallet.cloud.backup.CloudBackupHealthRepository
import build.wallet.cloud.backup.health.AppKeyBackupStatus
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.notifications.NotificationTrigger
import build.wallet.f8e.notifications.NotificationTriggerF8eClient
import build.wallet.feature.flags.AtRiskNotificationsFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.fwup.FirmwareDataService
import build.wallet.logging.logFailure
import build.wallet.recovery.keyset.SpendingKeysetRepairService
import build.wallet.recovery.keyset.SpendingKeysetSyncStatus
import com.github.michaelbull.result.get
import kotlinx.coroutines.flow.*

@BitkeyInject(AppScope::class)
class FundsLostRiskServiceImpl(
  private val cloudBackupHealthRepository: CloudBackupHealthRepository,
  private val firmwareDataService: FirmwareDataService,
  private val notificationsService: NotificationsService,
  private val accountService: AccountService,
  private val notificationTriggerF8eClient: NotificationTriggerF8eClient,
  private val atRiskNotificationsFeatureFlag: AtRiskNotificationsFeatureFlag,
  private val spendingKeysetRepairService: SpendingKeysetRepairService,
) : FundsLostRiskService, FundsLostRiskSyncWorker {
  // default to fully protected until we check the risk level
  private val riskLevelFlow = MutableStateFlow<FundsLostRiskLevel>(
    FundsLostRiskLevel.Protected
  )

  override fun riskLevel(): StateFlow<FundsLostRiskLevel> {
    return riskLevelFlow
  }

  override suspend fun executeWork() {
    combine(
      accountService.activeAccount(),
      cloudBackupHealthRepository.appKeyBackupStatus(),
      firmwareDataService.firmwareData(),
      notificationsService.getCriticalNotificationStatus(),
      spendingKeysetRepairService.syncStatus
    ) { account, appKeyStatus, firmwareData, notificationStatus, keysetSyncStatus ->
      // short circuit if we are not a full account
      if (account !is FullAccount) {
        return@combine FundsLostRiskLevel.Protected
      }

      if (keysetSyncStatus is SpendingKeysetSyncStatus.Mismatch) {
        FundsLostRiskLevel.AtRisk(
          AtRiskCause.ActiveSpendingKeysetMismatch
        )
      } else if (firmwareData.firmwareDeviceInfo == null) {
        FundsLostRiskLevel.AtRisk(
          AtRiskCause.MissingHardware
        )
      } else if (
        appKeyStatus is AppKeyBackupStatus.ProblemWithBackup &&
        appKeyStatus !is AppKeyBackupStatus.ProblemWithBackup.ConnectivityUnavailable
      ) {
        FundsLostRiskLevel.AtRisk(
          AtRiskCause.MissingCloudBackup(appKeyStatus)
        )
      } else if (notificationStatus is NotificationStatus.Missing &&
        notificationStatus.missingChannels.containsAll(listOf(NotificationChannel.Email, NotificationChannel.Sms))
      ) {
        FundsLostRiskLevel.AtRisk(
          AtRiskCause.MissingContactMethod
        )
      } else {
        FundsLostRiskLevel.Protected
      }
    }.distinctUntilChanged()
      .onEach { riskLevel ->
        if (atRiskNotificationsFeatureFlag.isEnabled()) {
          accountService.getAccountOrNull<FullAccount>()
            .get()
            ?.let { account ->
              notificationTriggerF8eClient.triggerNotification(
                f8eEnvironment = account.config.f8eEnvironment,
                accountId = account.accountId,
                triggers = riskLevel.toTriggers()
              ).logFailure { "Unable to update the trigger notifications" }
            }
        }
      }
      .collectLatest { riskLevel ->
        riskLevelFlow.emit(riskLevel)
      }
  }
}

private fun FundsLostRiskLevel.toTriggers(): Set<NotificationTrigger> {
  return when (this) {
    is FundsLostRiskLevel.AtRisk -> setOf(NotificationTrigger.SECURITY_HUB_WALLET_AT_RISK)
    FundsLostRiskLevel.Protected -> emptySet()
  }
}
