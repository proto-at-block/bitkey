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
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.cloud.backup.health.AppKeyBackupStatus
import build.wallet.cloud.backup.health.AppKeyBackupStatus.Healthy
import build.wallet.cloud.backup.health.CloudBackupHealthRepositoryMock
import build.wallet.cloud.backup.health.EekBackupStatus
import build.wallet.coroutines.createBackgroundScope
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.notifications.NotificationTrigger
import build.wallet.f8e.notifications.NotificationTriggerF8eClientFake
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.AtRiskNotificationsFeatureFlag
import build.wallet.fwup.FirmwareData
import build.wallet.fwup.FirmwareData.FirmwareUpdateState.UpToDate
import build.wallet.fwup.FirmwareDataServiceFake
import build.wallet.fwup.FirmwareDataUpToDateMock
import build.wallet.recovery.keyset.SpendingKeysetRepairServiceFake
import build.wallet.recovery.keyset.SpendingKeysetSyncStatus
import build.wallet.time.ClockFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch

class FundsLostRiskServiceImplTests : FunSpec({
  val cloudBackupHealthRepository = CloudBackupHealthRepositoryMock(turbines::create)
  val firmwareDataService = FirmwareDataServiceFake()
  val notificationsService = NotificationsServiceMock()
  val accountService = AccountServiceFake()
  val notificationTriggerF8eClient = NotificationTriggerF8eClientFake()
  val atRiskNotificationsFeatureFlag = AtRiskNotificationsFeatureFlag(
    featureFlagDao = FeatureFlagDaoFake()
  )
  val keysetRepairService = SpendingKeysetRepairServiceFake()

  fun service() =
    FundsLostRiskServiceImpl(
      cloudBackupHealthRepository = cloudBackupHealthRepository,
      firmwareDataService = firmwareDataService,
      notificationsService = notificationsService,
      accountService = accountService,
      notificationTriggerF8eClient = notificationTriggerF8eClient,
      atRiskNotificationsFeatureFlag = atRiskNotificationsFeatureFlag,
      spendingKeysetRepairService = keysetRepairService
    )

  beforeTest {
    accountService.setActiveAccount(FullAccountMock)
    cloudBackupHealthRepository.reset()
    firmwareDataService.reset()
    notificationsService.reset()
    keysetRepairService.reset()
  }

  test("protected when all factors are healthy") {
    cloudBackupHealthRepository.appKeyBackupStatus.value =
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
    cloudBackupHealthRepository.appKeyBackupStatus.value =
      AppKeyBackupStatus.ProblemWithBackup.BackupMissing
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
            AppKeyBackupStatus.ProblemWithBackup.BackupMissing
          )
        )
      )
    }
  }

  test("at risk when hardware is missing") {
    cloudBackupHealthRepository.appKeyBackupStatus.value =
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

  test("at risk when keybox holds a placeholder hw signature") {
    cloudBackupHealthRepository.appKeyBackupStatus.value =
      Healthy(ClockFake().now)
    cloudBackupHealthRepository.eekBackupStatus.value =
      EekBackupStatus.Healthy(ClockFake().now)
    firmwareDataService.firmwareData.value = FirmwareDataUpToDateMock
    notificationsService.criticalNotificationsStatus.value = Enabled
    accountService.setActiveAccount(
      FullAccountMock.copy(
        keybox = FullAccountMock.keybox.copy(
          appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature(
            AppGlobalAuthKeyHwSignature.W3_ONBOARDING_PLACEHOLDER
          )
        )
      )
    )

    val service = service()

    createBackgroundScope().launch {
      service.executeWork()
    }

    service.riskLevel().test {
      awaitUntil(FundsLostRiskLevel.AtRisk(AtRiskCause.UnverifiedHardwareSignature))
    }
  }

  test("orphaned key recovery sentinel does not trigger hardware signature risk") {
    cloudBackupHealthRepository.appKeyBackupStatus.value =
      Healthy(ClockFake().now)
    cloudBackupHealthRepository.eekBackupStatus.value =
      EekBackupStatus.Healthy(ClockFake().now)
    firmwareDataService.firmwareData.value = FirmwareDataUpToDateMock
    notificationsService.criticalNotificationsStatus.value = Enabled
    // The W3 repair flow cannot repair this sentinel.
    accountService.setActiveAccount(
      FullAccountMock.copy(
        keybox = FullAccountMock.keybox.copy(
          appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature(
            AppGlobalAuthKeyHwSignature.ORPHANED_KEY_RECOVERY_SENTINEL
          )
        )
      )
    )

    val service = service()

    createBackgroundScope().launch {
      service.executeWork()
    }

    service.riskLevel().test {
      awaitItem().shouldBe(FundsLostRiskLevel.Protected)
    }
  }

  test("at risk when keyset mismatch is detected") {
    cloudBackupHealthRepository.appKeyBackupStatus.value =
      Healthy(ClockFake().now)
    cloudBackupHealthRepository.eekBackupStatus.value =
      EekBackupStatus.Healthy(ClockFake().now)
    firmwareDataService.firmwareData.value = FirmwareDataUpToDateMock
    notificationsService.criticalNotificationsStatus.value = Enabled
    keysetRepairService.setStatus(
      SpendingKeysetSyncStatus.Mismatch(
        localActiveKeysetId = "local-keyset-id",
        serverActiveKeysetId = "server-keyset-id"
      )
    )

    val service = service()

    createBackgroundScope().launch {
      service.executeWork()
    }

    service.riskLevel().test {
      awaitUntil(FundsLostRiskLevel.AtRisk(AtRiskCause.ActiveSpendingKeysetMismatch))
    }
  }

  test("at risk when incomplete private wallet keybox is detected") {
    cloudBackupHealthRepository.appKeyBackupStatus.value =
      Healthy(ClockFake().now)
    cloudBackupHealthRepository.eekBackupStatus.value =
      EekBackupStatus.Healthy(ClockFake().now)
    firmwareDataService.firmwareData.value = FirmwareDataUpToDateMock
    notificationsService.criticalNotificationsStatus.value = Enabled
    keysetRepairService.setStatus(
      SpendingKeysetSyncStatus.IncompletePrivateWallet(
        activeKeysetId = "active-keyset-id"
      )
    )

    val service = service()

    createBackgroundScope().launch {
      service.executeWork()
    }

    service.riskLevel().test {
      awaitUntil(FundsLostRiskLevel.AtRisk(AtRiskCause.ActiveSpendingKeysetMismatch))
    }
  }

  test("at risk when incomplete keyset list is detected") {
    cloudBackupHealthRepository.appKeyBackupStatus.value =
      Healthy(ClockFake().now)
    cloudBackupHealthRepository.eekBackupStatus.value =
      EekBackupStatus.Healthy(ClockFake().now)
    firmwareDataService.firmwareData.value = FirmwareDataUpToDateMock
    notificationsService.criticalNotificationsStatus.value = Enabled
    keysetRepairService.setStatus(
      SpendingKeysetSyncStatus.IncompleteKeysetList(
        activeKeysetId = "active-keyset-id",
        missingKeysetIds = setOf("missing-keyset-id")
      )
    )

    val service = service()

    createBackgroundScope().launch {
      service.executeWork()
    }

    service.riskLevel().test {
      awaitUntil(FundsLostRiskLevel.AtRisk(AtRiskCause.ActiveSpendingKeysetMismatch))
    }
  }

  test("not at risk when incomplete keyset list is unrecoverable") {
    // Deliberate: AtRisk drives a banner whose only remediation is the repair flow, which cannot
    // resolve these keysets. Showing it would produce a warning the customer can never clear.
    cloudBackupHealthRepository.appKeyBackupStatus.value =
      Healthy(ClockFake().now)
    cloudBackupHealthRepository.eekBackupStatus.value =
      EekBackupStatus.Healthy(ClockFake().now)
    firmwareDataService.firmwareData.value = FirmwareDataUpToDateMock
    notificationsService.criticalNotificationsStatus.value = Enabled
    keysetRepairService.setStatus(
      SpendingKeysetSyncStatus.IncompleteKeysetListUnrecoverable(
        activeKeysetId = "active-keyset-id",
        missingKeysetIds = setOf("unrecoverable-keyset-id")
      )
    )

    val service = service()

    createBackgroundScope().launch {
      service.executeWork()
    }

    service.riskLevel().test {
      awaitUntil(FundsLostRiskLevel.Protected)
    }
  }

  test("at risk when sms and email are missing") {
    cloudBackupHealthRepository.appKeyBackupStatus.value =
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

  test("protected when cloud is a connectivity error") {
    cloudBackupHealthRepository.appKeyBackupStatus.value = AppKeyBackupStatus.ProblemWithBackup.ConnectivityUnavailable

    val service = service()

    createBackgroundScope().launch {
      service.executeWork()
    }

    service.riskLevel().test {
      awaitItem().shouldBe(FundsLostRiskLevel.Protected)
    }
  }

  test("at risk notification are triggered when flag is enabled") {
    atRiskNotificationsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    cloudBackupHealthRepository.appKeyBackupStatus.value =
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

    notificationTriggerF8eClient.triggers
      .shouldNotBeNull()
      .shouldContainExactly(NotificationTrigger.SECURITY_HUB_WALLET_AT_RISK)
  }
})
