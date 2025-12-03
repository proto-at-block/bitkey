package bitkey.securitycenter

import app.cash.turbine.test
import bitkey.f8e.privilegedactions.AuthorizationStrategy
import bitkey.f8e.privilegedactions.AuthorizationStrategyType
import bitkey.f8e.privilegedactions.PrivilegedActionInstance
import bitkey.f8e.privilegedactions.PrivilegedActionType
import bitkey.privilegedactions.FingerprintResetF8eClientFake
import bitkey.privilegedactions.FingerprintResetServiceFake
import build.wallet.account.AccountServiceFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.FingerprintResetFeatureFlag
import build.wallet.feature.flags.FingerprintResetMinFirmwareVersionFeatureFlag
import build.wallet.firmware.*
import build.wallet.grants.Grant
import build.wallet.nfc.HardwareProvisionedAppKeyStatusDaoFake
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

class FingerprintsActionFactoryImplTest : FunSpec({
  val clock = ClockFake()
  val hardwareUnlockInfoService = HardwareUnlockInfoServiceFake()
  val firmwareDeviceInfoDao = FirmwareDeviceInfoDaoMock(turbines::create)
  val accountService = AccountServiceFake()
  val fingerprintResetF8eClient = FingerprintResetF8eClientFake(clock)
  val fingerprintResetService = FingerprintResetServiceFake(
    fingerprintResetF8eClient,
    accountService,
    clock
  )
  val hardwareProvisionedAppKeyStatusDao = HardwareProvisionedAppKeyStatusDaoFake()
  val featureFlagDao = FeatureFlagDaoFake()
  val fingerprintResetFeatureFlag = FingerprintResetFeatureFlag(featureFlagDao)
  val fingerprintResetMinFirmwareVersionFeatureFlag =
    FingerprintResetMinFirmwareVersionFeatureFlag(featureFlagDao)

  val factory = FingerprintsActionFactoryImpl(
    hardwareUnlockInfoService = hardwareUnlockInfoService,
    firmwareDeviceInfoDao = firmwareDeviceInfoDao,
    fingerprintResetService = fingerprintResetService,
    hardwareProvisionedAppKeyStatusDao = hardwareProvisionedAppKeyStatusDao,
    fingerprintResetMinFirmwareVersionFeatureFlag = fingerprintResetMinFirmwareVersionFeatureFlag,
    fingerprintResetFeatureFlag = fingerprintResetFeatureFlag,
    clock = clock
  )

  beforeTest {
    accountService.setActiveAccount(FullAccountMock)
    hardwareUnlockInfoService.clear()
    firmwareDeviceInfoDao.reset()
    fingerprintResetService.reset()
    hardwareProvisionedAppKeyStatusDao.reset()
    featureFlagDao.reset()
    // Set default feature flag values
    fingerprintResetFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    fingerprintResetMinFirmwareVersionFeatureFlag.setFlagValue(FeatureFlagValue.StringFlag("1.0.98"))
    // By default, set up the app key as provisioned so tests don't get the provisioning recommendation
    // unless they specifically test for it
    hardwareProvisionedAppKeyStatusDao.activeAccountKeys =
      FullAccountMock.keybox.activeHwKeyBundle.authKey to FullAccountMock.keybox.activeAppKeyBundle.authKey
    hardwareProvisionedAppKeyStatusDao.recordProvisionedKey(
      hwAuthPubKey = FullAccountMock.keybox.activeHwKeyBundle.authKey,
      appAuthPubKey = FullAccountMock.keybox.activeAppKeyBundle.authKey
    )
  }

  test("should emit FingerprintsAction with fingerprint reset ready via grant") {
    // Setup: Pending grant exists
    val mockGrant = Grant(
      version = 1,
      serializedRequest = byteArrayOf(1, 2, 3),
      appSignature = byteArrayOf(4, 5, 6),
      wsmSignature = byteArrayOf(7, 8, 9)
    )
    fingerprintResetService.completeFingerprintResetAndGetGrantResult = Ok(mockGrant)

    // Simulate having a grant in the service
    fingerprintResetService.completeFingerprintResetAndGetGrant("test-id", "test-token")

    hardwareUnlockInfoService.replaceAllUnlockInfo(emptyList())
    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock)

    factory.create().test {
      val action = awaitItem()
      action.shouldBeInstanceOf<FingerprintsAction>()

      action.getRecommendations().shouldContainExactly(
        SecurityActionRecommendation.COMPLETE_FINGERPRINT_RESET,
        SecurityActionRecommendation.ADD_FINGERPRINTS
      )
      action.state() shouldBe SecurityActionState.HasRecommendationActions
    }
  }

  test("should emit FingerprintsAction with fingerprint reset ready via completed delay") {
    // Setup: Server action with completed delay period
    val completedAction = PrivilegedActionInstance(
      id = "test-action-id",
      privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
      authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
        authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
        delayStartTime = clock.now().minus(8.days),
        delayEndTime = clock.now().minus(1.days), // Delay completed
        cancellationToken = "cancel-token",
        completionToken = "completion-token"
      )
    )
    fingerprintResetService.getLatestFingerprintResetActionResult = Ok(completedAction)
    fingerprintResetService.getLatestFingerprintResetAction()

    hardwareUnlockInfoService.replaceAllUnlockInfo(emptyList())
    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock)

    factory.create().test {
      val action = awaitItem()
      action.shouldBeInstanceOf<FingerprintsAction>()

      action.getRecommendations().shouldContainExactly(
        SecurityActionRecommendation.COMPLETE_FINGERPRINT_RESET,
        SecurityActionRecommendation.ADD_FINGERPRINTS
      )
      action.state() shouldBe SecurityActionState.HasRecommendationActions
    }
  }

  test("should emit FingerprintsAction with fingerprint reset not ready during delay") {
    // Setup: Server action with delay still in progress
    val pendingAction = PrivilegedActionInstance(
      id = "test-action-id",
      privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
      authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
        authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
        delayStartTime = clock.now().minus(1.days),
        delayEndTime = clock.now().plus(6.days), // Delay still in progress
        cancellationToken = "cancel-token",
        completionToken = "completion-token"
      )
    )
    fingerprintResetService.getLatestFingerprintResetActionResult = Ok(pendingAction)
    fingerprintResetService.getLatestFingerprintResetAction()

    hardwareUnlockInfoService.replaceAllUnlockInfo(emptyList())
    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock)

    factory.create().test {
      val action = awaitItem()
      action.shouldBeInstanceOf<FingerprintsAction>()

      // Should not include fingerprint reset recommendation since delay is not complete
      action.getRecommendations().shouldContainExactly(
        SecurityActionRecommendation.ADD_FINGERPRINTS
      )
      action.state() shouldBe SecurityActionState.HasRecommendationActions
    }
  }

  test("fingerprint reset flow should transition from not ready to ready") {
    // Setup: Server action with delay that will complete during test
    val delayDuration = 100.milliseconds
    val pendingAction = PrivilegedActionInstance(
      id = "test-action-id",
      privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
      authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
        authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
        delayStartTime = clock.now().minus(1.days),
        delayEndTime = clock.now().plus(delayDuration),
        cancellationToken = "cancel-token",
        completionToken = "completion-token"
      )
    )
    fingerprintResetService.getLatestFingerprintResetActionResult = Ok(pendingAction)
    fingerprintResetService.getLatestFingerprintResetAction()

    hardwareUnlockInfoService.replaceAllUnlockInfo(emptyList())
    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock)

    factory.create().test {
      // First emission: delay not complete
      val action1 = awaitItem()
      action1.getRecommendations().shouldContainExactly(
        SecurityActionRecommendation.ADD_FINGERPRINTS
      )

      // Advance time past the delay end time
      clock.advanceBy(10.milliseconds)

      // Second emission: delay complete, should recommend both fingerprint reset and add
      val action2 = awaitItem()
      action2.getRecommendations().shouldContainExactly(
        SecurityActionRecommendation.COMPLETE_FINGERPRINT_RESET,
        SecurityActionRecommendation.ADD_FINGERPRINTS
      )
    }
  }

  test("should react to changes in fingerprint count") {
    hardwareUnlockInfoService.replaceAllUnlockInfo(emptyList())
    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock)

    factory.create().test {
      // Initial state: no fingerprints, should recommend ADD_FINGERPRINTS (has firmware info)
      val action1 = awaitItem()
      action1.getRecommendations().shouldContainExactly(
        SecurityActionRecommendation.ADD_FINGERPRINTS
      )

      // Add one fingerprint
      hardwareUnlockInfoService.replaceAllUnlockInfo(
        listOf(UnlockInfo(unlockMethod = UnlockMethod.BIOMETRICS, fingerprintIdx = 1))
      )

      val action2 = awaitItem()
      action2.getRecommendations().shouldContainExactly(
        SecurityActionRecommendation.ADD_FINGERPRINTS
      )

      // Add second fingerprint
      hardwareUnlockInfoService.replaceAllUnlockInfo(
        listOf(
          UnlockInfo(unlockMethod = UnlockMethod.BIOMETRICS, fingerprintIdx = 1),
          UnlockInfo(unlockMethod = UnlockMethod.BIOMETRICS, fingerprintIdx = 2)
        )
      )

      val action3 = awaitItem()
      action3.getRecommendations().shouldBeEmpty()
    }
  }

  test("should not count non-biometric unlock methods") {
    // Setup: Mix of biometric and non-biometric unlock methods
    hardwareUnlockInfoService.replaceAllUnlockInfo(
      listOf(
        UnlockInfo(unlockMethod = UnlockMethod.BIOMETRICS, fingerprintIdx = 1),
        UnlockInfo(unlockMethod = UnlockMethod.UNLOCK_SECRET, fingerprintIdx = null)
      )
    )
    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock)

    factory.create().test {
      val action = awaitItem()
      action.shouldBeInstanceOf<FingerprintsAction>()

      // Should only count the one biometric unlock method
      action.getRecommendations().shouldContainExactly(
        SecurityActionRecommendation.ADD_FINGERPRINTS
      )
    }
  }

  test("should integrate data from multiple services correctly") {
    // Setup: Test integration of getting started tasks, firmware info, and fingerprint reset
    val mockGrant = Grant(
      version = 1,
      serializedRequest = byteArrayOf(1, 2, 3),
      appSignature = byteArrayOf(4, 5, 6),
      wsmSignature = byteArrayOf(7, 8, 9)
    )
    fingerprintResetService.completeFingerprintResetAndGetGrantResult = Ok(mockGrant)
    fingerprintResetService.completeFingerprintResetAndGetGrant("test-id", "test-token")

    hardwareUnlockInfoService.replaceAllUnlockInfo(emptyList())

    factory.create().test {
      val action = awaitItem()
      action.shouldBeInstanceOf<FingerprintsAction>()

      // Should include both recommendations: reset and add fingerprints
      action.getRecommendations().shouldContainExactly(
        SecurityActionRecommendation.COMPLETE_FINGERPRINT_RESET,
        SecurityActionRecommendation.ADD_FINGERPRINTS
      )
      action.state() shouldBe SecurityActionState.HasRecommendationActions
    }
  }

  test("should handle transitioning from no device info to having device info") {
    hardwareUnlockInfoService.replaceAllUnlockInfo(
      listOf(
        UnlockInfo(unlockMethod = UnlockMethod.BIOMETRICS, fingerprintIdx = 1),
        UnlockInfo(unlockMethod = UnlockMethod.BIOMETRICS, fingerprintIdx = 2)
      )
    )

    factory.create().test {
      awaitItem().getRecommendations().shouldContainExactly(SecurityActionRecommendation.ADD_FINGERPRINTS)

      firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock)

      awaitItem().getRecommendations().shouldBeEmpty()
    }
  }

  test("should recommend provisioning app key when not provisioned") {
    // Setup active account with keys but no provisioned status
    // Reset the DAO to clear the default provisioning from beforeTest
    hardwareProvisionedAppKeyStatusDao.reset()

    hardwareUnlockInfoService.replaceAllUnlockInfo(
      listOf(
        UnlockInfo(unlockMethod = UnlockMethod.BIOMETRICS, fingerprintIdx = 1),
        UnlockInfo(unlockMethod = UnlockMethod.BIOMETRICS, fingerprintIdx = 2)
      )
    )
    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock)

    factory.create().test {
      val action = awaitItem()
      action.shouldBeInstanceOf<FingerprintsAction>()

      // Should recommend provisioning since app key is not provisioned
      action.getRecommendations().shouldContainExactly(
        SecurityActionRecommendation.PROVISION_APP_KEY_TO_HARDWARE
      )
    }
  }

  test("should not recommend provisioning app key when already provisioned") {
    // Setup active account with provisioned keys (already done in beforeTest)
    hardwareUnlockInfoService.replaceAllUnlockInfo(
      listOf(
        UnlockInfo(unlockMethod = UnlockMethod.BIOMETRICS, fingerprintIdx = 1),
        UnlockInfo(unlockMethod = UnlockMethod.BIOMETRICS, fingerprintIdx = 2)
      )
    )
    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock)

    factory.create().test {
      val action = awaitItem()
      action.shouldBeInstanceOf<FingerprintsAction>()

      // Should not recommend provisioning since app key is already provisioned
      action.getRecommendations().shouldBeEmpty()
    }
  }

  test("should not recommend provisioning when fingerprint reset feature flag is disabled") {
    // Disable fingerprint reset feature flag
    fingerprintResetFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))

    hardwareUnlockInfoService.replaceAllUnlockInfo(
      listOf(
        UnlockInfo(unlockMethod = UnlockMethod.BIOMETRICS, fingerprintIdx = 1),
        UnlockInfo(unlockMethod = UnlockMethod.BIOMETRICS, fingerprintIdx = 2)
      )
    )
    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock)

    factory.create().test {
      val action = awaitItem()
      action.shouldBeInstanceOf<FingerprintsAction>()

      // Should not recommend provisioning since feature flag is disabled
      action.getRecommendations().shouldBeEmpty()
    }
  }

  test("should not recommend provisioning when firmware version is below minimum") {
    // Set minimum firmware version higher than device's version
    fingerprintResetMinFirmwareVersionFeatureFlag.setFlagValue(FeatureFlagValue.StringFlag("2.0.0"))

    hardwareUnlockInfoService.replaceAllUnlockInfo(
      listOf(
        UnlockInfo(unlockMethod = UnlockMethod.BIOMETRICS, fingerprintIdx = 1),
        UnlockInfo(unlockMethod = UnlockMethod.BIOMETRICS, fingerprintIdx = 2)
      )
    )
    // FirmwareDeviceInfoMock has version "1.2.3"
    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock)

    factory.create().test {
      val action = awaitItem()

      // Should not recommend provisioning since firmware is below minimum version
      action.getRecommendations().shouldBeEmpty()
    }
  }

  test("should recommend provisioning when firmware version meets minimum") {
    // Set minimum firmware version at or below device's version
    fingerprintResetMinFirmwareVersionFeatureFlag.setFlagValue(FeatureFlagValue.StringFlag("1.0.98"))

    // Reset the DAO to clear the default provisioning from beforeTest
    hardwareProvisionedAppKeyStatusDao.reset()

    hardwareUnlockInfoService.replaceAllUnlockInfo(
      listOf(
        UnlockInfo(unlockMethod = UnlockMethod.BIOMETRICS, fingerprintIdx = 1),
        UnlockInfo(unlockMethod = UnlockMethod.BIOMETRICS, fingerprintIdx = 2)
      )
    )
    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock)

    factory.create().test {
      val action = awaitItem()
      action.shouldBeInstanceOf<FingerprintsAction>()

      // Should recommend provisioning since firmware meets minimum and not provisioned
      action.getRecommendations().shouldContainExactly(
        SecurityActionRecommendation.PROVISION_APP_KEY_TO_HARDWARE
      )
    }
  }

  test("should not recommend provisioning when firmware device info is null") {
    fingerprintResetFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    fingerprintResetMinFirmwareVersionFeatureFlag.setFlagValue(FeatureFlagValue.StringFlag("1.0.0"))

    hardwareUnlockInfoService.replaceAllUnlockInfo(
      listOf(
        UnlockInfo(unlockMethod = UnlockMethod.BIOMETRICS, fingerprintIdx = 1),
        UnlockInfo(unlockMethod = UnlockMethod.BIOMETRICS, fingerprintIdx = 2)
      )
    )
    // No firmware device info set

    factory.create().test {
      val action = awaitItem()
      action.shouldBeInstanceOf<FingerprintsAction>()

      // Should not recommend provisioning since we can't verify firmware version
      // But should recommend adding fingerprints since no firmware info
      action.getRecommendations().shouldContainExactly(
        SecurityActionRecommendation.ADD_FINGERPRINTS
      )
    }
  }
})
