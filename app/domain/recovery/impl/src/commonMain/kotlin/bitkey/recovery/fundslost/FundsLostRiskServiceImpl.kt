package bitkey.recovery.fundslost

import bitkey.notifications.NotificationChannel
import bitkey.notifications.NotificationsService
import bitkey.notifications.NotificationsService.NotificationStatus
import build.wallet.account.AccountService
import build.wallet.bitkey.account.FullAccount
import build.wallet.cloud.backup.CloudBackupHealthRepository
import build.wallet.cloud.backup.health.EekBackupStatus
import build.wallet.cloud.backup.health.MobileKeyBackupStatus
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.fwup.FirmwareDataService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine

@BitkeyInject(AppScope::class)
class FundsLostRiskServiceImpl(
  private val cloudBackupHealthRepository: CloudBackupHealthRepository,
  private val firmwareDataService: FirmwareDataService,
  private val notificationsService: NotificationsService,
  private val accountService: AccountService,
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
      cloudBackupHealthRepository.mobileKeyBackupStatus(),
      cloudBackupHealthRepository.eekBackupStatus(),
      firmwareDataService.firmwareData(),
      notificationsService.getCriticalNotificationStatus()
    ) { account, mobileKeyStatus, eekBackupStatus, firmwareData, notificationStatus ->
      // short circuit if we are not a full account
      if (account !is FullAccount) {
        return@combine FundsLostRiskLevel.Protected
      }

      if (firmwareData.firmwareDeviceInfo == null) {
        FundsLostRiskLevel.AtRisk(
          AtRiskCause.MissingHardware
        )
      } else if (mobileKeyStatus is MobileKeyBackupStatus.ProblemWithBackup) {
        FundsLostRiskLevel.AtRisk(
          AtRiskCause.MissingCloudBackup(mobileKeyStatus)
        )
      } else if (eekBackupStatus is EekBackupStatus.ProblemWithBackup) {
        FundsLostRiskLevel.AtRisk(
          AtRiskCause.MissingEek(eekBackupStatus)
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
    }.collectLatest { riskLevel ->
      riskLevelFlow.emit(riskLevel)
    }
  }
}
