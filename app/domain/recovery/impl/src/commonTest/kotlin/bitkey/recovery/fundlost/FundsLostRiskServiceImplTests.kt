package bitkey.recovery.fundlost

import app.cash.turbine.test
import bitkey.notifications.NotificationChannel
import bitkey.notifications.NotificationsService
import bitkey.notifications.NotificationsService.NotificationStatus.Enabled
import bitkey.notifications.NotificationsServiceMock
import bitkey.recovery.fundslost.AtRiskCause
import bitkey.recovery.fundslost.FundsLostRiskLevel
import bitkey.recovery.fundslost.FundsLostRiskServiceImpl
import build.wallet.account.AccountServiceFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.cloud.backup.health.CloudBackupHealthRepositoryMock
import build.wallet.cloud.backup.health.EekBackupStatus
import build.wallet.cloud.backup.health.MobileKeyBackupStatus
import build.wallet.cloud.backup.health.MobileKeyBackupStatus.Healthy
import build.wallet.coroutines.createBackgroundScope
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.coroutines.turbine.turbines
import build.wallet.fwup.FirmwareData
import build.wallet.fwup.FirmwareData.FirmwareUpdateState.UpToDate
import build.wallet.fwup.FirmwareDataServiceFake
import build.wallet.fwup.FirmwareDataUpToDateMock
import build.wallet.time.ClockFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch

class FundsLostRiskServiceImplTests : FunSpec({
  val cloudBackupHealthRepository = CloudBackupHealthRepositoryMock(turbines::create)
  val firmwareDataService = FirmwareDataServiceFake()
  val notificationsService = NotificationsServiceMock()
  val accountService = AccountServiceFake()

  fun service() =
    FundsLostRiskServiceImpl(
      cloudBackupHealthRepository = cloudBackupHealthRepository,
      firmwareDataService = firmwareDataService,
      notificationsService = notificationsService,
      accountService = accountService
    )

  beforeTest {
    accountService.setActiveAccount(FullAccountMock)
  }

  test("protected when all factors are healthy") {
    cloudBackupHealthRepository.mobileKeyBackupStatus.value =
      Healthy(ClockFake().now)
    cloudBackupHealthRepository.eekBackupStatus.value =
      EekBackupStatus.Healthy(ClockFake().now)
    firmwareDataService.firmwareData.value = FirmwareDataUpToDateMock
    notificationsService.criticalNotificationsStatus.value = Enabled

    val service = service()

    createBackgroundScope().launch {
      service.executeWork()
    }

    service.riskLevel().test {
      awaitItem().shouldBe(FundsLostRiskLevel.Protected)
    }
  }

  test("at risk when cloud back up is not healthy") {
    cloudBackupHealthRepository.mobileKeyBackupStatus.value =
      MobileKeyBackupStatus.ProblemWithBackup.BackupMissing
    cloudBackupHealthRepository.eekBackupStatus.value =
      EekBackupStatus.Healthy(ClockFake().now)
    firmwareDataService.firmwareData.value = FirmwareDataUpToDateMock
    notificationsService.criticalNotificationsStatus.value = Enabled

    val service = service()

    createBackgroundScope().launch {
      service.executeWork()
    }

    service.riskLevel().test {
      awaitUntil(
        FundsLostRiskLevel.AtRisk(
          AtRiskCause.MissingCloudBackup(
            MobileKeyBackupStatus.ProblemWithBackup.BackupMissing
          )
        )
      )
    }
  }

  test("at risk when hardware is missing") {
    cloudBackupHealthRepository.mobileKeyBackupStatus.value =
      Healthy(ClockFake().now)
    cloudBackupHealthRepository.eekBackupStatus.value =
      EekBackupStatus.Healthy(ClockFake().now)
    firmwareDataService.firmwareData.value = FirmwareData(
      firmwareUpdateState = UpToDate,
      firmwareDeviceInfo = null
    )
    notificationsService.criticalNotificationsStatus.value = Enabled

    val service = service()

    createBackgroundScope().launch {
      service.executeWork()
    }

    service.riskLevel().test {
      awaitUntil(FundsLostRiskLevel.AtRisk(AtRiskCause.MissingHardware))
    }
  }

  test("at risk when sms and email are missing") {
    cloudBackupHealthRepository.mobileKeyBackupStatus.value =
      Healthy(ClockFake().now)
    cloudBackupHealthRepository.eekBackupStatus.value =
      EekBackupStatus.Healthy(ClockFake().now)
    firmwareDataService.firmwareData.value = FirmwareDataUpToDateMock
    notificationsService.criticalNotificationsStatus.value =
      NotificationsService.NotificationStatus.Missing(
        missingChannels = setOf(
          NotificationChannel.Sms,
          NotificationChannel.Email
        )
      )

    val service = service()

    createBackgroundScope().launch {
      service.executeWork()
    }

    service.riskLevel().test {
      awaitUntil(FundsLostRiskLevel.AtRisk(AtRiskCause.MissingContactMethod))
    }
  }

  test("protected when not a full account") {
    accountService.setActiveAccount(LiteAccountMock)

    val service = service()

    createBackgroundScope().launch {
      service.executeWork()
    }

    service.riskLevel().test {
      awaitItem().shouldBe(FundsLostRiskLevel.Protected)
    }
  }
})
