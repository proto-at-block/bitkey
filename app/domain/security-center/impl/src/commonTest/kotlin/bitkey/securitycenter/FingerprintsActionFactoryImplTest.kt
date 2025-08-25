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
import build.wallet.firmware.FirmwareDeviceInfoDaoMock
import build.wallet.firmware.FirmwareDeviceInfoMock
import build.wallet.firmware.HardwareUnlockInfoServiceFake
import build.wallet.firmware.UnlockInfo
import build.wallet.firmware.UnlockMethod
import build.wallet.grants.Grant
import build.wallet.home.GettingStartedTask
import build.wallet.home.GettingStartedTaskDaoMock
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
  val gettingStartedTaskDao = GettingStartedTaskDaoMock(turbines::create)
  val hardwareUnlockInfoService = HardwareUnlockInfoServiceFake()
  val firmwareDeviceInfoDao = FirmwareDeviceInfoDaoMock(turbines::create)
  val accountService = AccountServiceFake()
  val fingerprintResetF8eClient = FingerprintResetF8eClientFake(clock)
  val fingerprintResetService = FingerprintResetServiceFake(
    fingerprintResetF8eClient,
    accountService,
    clock
  )

  val factory = FingerprintsActionFactoryImpl(
    gettingStartedTaskDao = gettingStartedTaskDao,
    hardwareUnlockInfoService = hardwareUnlockInfoService,
    firmwareDeviceInfoDao = firmwareDeviceInfoDao,
    fingerprintResetService = fingerprintResetService,
    clock = clock
  )

  beforeTest {
    accountService.setActiveAccount(FullAccountMock)
    gettingStartedTaskDao.reset()
    hardwareUnlockInfoService.clear()
    firmwareDeviceInfoDao.reset()
    fingerprintResetService.reset()
  }

  test("should emit FingerprintsAction with fingerprint reset ready via grant") {
    // Setup: Pending grant exists
    val mockGrant = Grant(
      version = 1,
      serializedRequest = byteArrayOf(1, 2, 3),
      signature = byteArrayOf(4, 5, 6)
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
        SecurityActionRecommendation.COMPLETE_FINGERPRINT_RESET
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
        SecurityActionRecommendation.COMPLETE_FINGERPRINT_RESET
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
      action.getRecommendations().shouldBeEmpty()
      action.state() shouldBe SecurityActionState.Secure
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
      action1.getRecommendations().shouldBeEmpty()

      // Advance time past the delay end time
      clock.advanceBy(10.milliseconds)

      // Second emission: delay complete, should recommend fingerprint reset
      val action2 = awaitItem()
      action2.getRecommendations().shouldContainExactly(
        SecurityActionRecommendation.COMPLETE_FINGERPRINT_RESET
      )
    }
  }

  test("should react to changes in fingerprint count") {
    hardwareUnlockInfoService.replaceAllUnlockInfo(emptyList())
    firmwareDeviceInfoDao.setDeviceInfo(FirmwareDeviceInfoMock)

    factory.create().test {
      // Initial state: no fingerprints, no recommendations (has firmware info)
      val action1 = awaitItem()
      action1.getRecommendations().shouldBeEmpty()

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
      signature = byteArrayOf(4, 5, 6)
    )
    fingerprintResetService.completeFingerprintResetAndGetGrantResult = Ok(mockGrant)
    fingerprintResetService.completeFingerprintResetAndGetGrant("test-id", "test-token")

    hardwareUnlockInfoService.replaceAllUnlockInfo(emptyList())
    // No firmware device info set (inactive hardware)
    gettingStartedTaskDao.addTasks(
      listOf(
        GettingStartedTask(
          id = GettingStartedTask.TaskId.AddAdditionalFingerprint,
          state = GettingStartedTask.TaskState.Incomplete
        )
      )
    )

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
})
