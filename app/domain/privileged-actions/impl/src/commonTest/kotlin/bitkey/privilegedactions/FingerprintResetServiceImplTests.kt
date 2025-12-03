package bitkey.privilegedactions

import app.cash.turbine.test
import bitkey.f8e.fingerprintreset.FingerprintResetRequest
import bitkey.f8e.fingerprintreset.FingerprintResetResponse
import bitkey.f8e.privilegedactions.*
import bitkey.f8e.privilegedactions.AuthorizationStrategy
import bitkey.f8e.privilegedactions.AuthorizationStrategyType
import bitkey.f8e.privilegedactions.PrivilegedActionInstance
import bitkey.f8e.privilegedactions.PrivilegedActionType
import build.wallet.account.AccountServiceFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.createBackgroundScope
import build.wallet.coroutines.turbine.awaitUntilNotNull
import build.wallet.db.DbError
import build.wallet.encrypt.SignatureUtilsMock
import build.wallet.firmware.HardwareUnlockInfoServiceFake
import build.wallet.firmware.UnlockInfo
import build.wallet.firmware.UnlockMethod
import build.wallet.grants.Grant
import build.wallet.grants.GrantAction
import build.wallet.grants.GrantRequest
import build.wallet.grants.GrantTestHelpers
import build.wallet.ktor.result.EmptyResponseBody
import build.wallet.testing.shouldBeErrOfType
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.ktor.util.encodeBase64
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okio.ByteString.Companion.decodeHex
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class FingerprintResetServiceImplTests : FunSpec({
  val clock = ClockFake()

  val fingerprintResetF8eClient = FingerprintResetF8eClientFake(
    clock
  )

  val accountService = AccountServiceFake()
  val signatureUtils = SignatureUtilsMock()
  val grantDao = GrantDaoFake(clock)
  val hardwareUnlockInfoService = HardwareUnlockInfoServiceFake()
  val messageSigner = build.wallet.auth.AppAuthKeyMessageSignerMock()

  lateinit var service: FingerprintResetServiceImpl

  val mockDerSignature =
    "3045022100b6e8f2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2022003f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2f2"

  fun createMockGrant(signatureOffset: Int = 0) =
    Grant(
      version = 1,
      serializedRequest = GrantTestHelpers.createMockSerializedGrantRequest(GrantAction.FINGERPRINT_RESET),
      appSignature = ByteArray(64) { (it + signatureOffset).toByte() },
      wsmSignature = ByteArray(64) { (it + signatureOffset + 100).toByte() }
    )

  beforeEach {
    accountService.setActiveAccount(FullAccountMock)
    grantDao.reset()
    fingerprintResetF8eClient.reset()
    hardwareUnlockInfoService.clear()
    messageSigner.reset()
    service = FingerprintResetServiceImpl(
      privilegedActionF8eClient = fingerprintResetF8eClient,
      accountService = accountService,
      signatureUtils = signatureUtils,
      clock = clock,
      grantDao = grantDao,
      hardwareUnlockInfoService = hardwareUnlockInfoService,
      messageSigner = messageSigner
    )
  }

  test("createFingerprintResetPrivilegedAction should create a fingerprint reset request and call createPrivilegedAction") {
    val testVersion = 1
    val testDeviceId = byteArrayOf(-76, 53, 34, -1, -2, -20, 80, -61)
    val testChallengeBytes = ByteArray(16) { (it and 0xFF).toByte() }
    val testSignature =
      "21a1aa12efc8512727856a9ccc428a511cf08b211f26551781ae0a37661de8060c566ded9486500f6927e9c9df620c65653c68316e61930a49ecab31b3bec498" // 128 hex chars = 64 bytes
    val grantRequest = GrantRequest(
      version = testVersion.toByte(),
      deviceId = testDeviceId,
      challenge = testChallengeBytes,
      action = GrantAction.FINGERPRINT_RESET,
      signature = testSignature.decodeHex().toByteArray()
    )
    val expectedInstance = PrivilegedActionInstance(
      id = "test-id",
      privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
      authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
        authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
        delayStartTime = clock.now(),
        delayEndTime = clock.now(),
        cancellationToken = "test-token",
        completionToken = "test-token"
      )
    )

    messageSigner.result = Ok("test-app-signature")
    fingerprintResetF8eClient.createPrivilegedActionResult = Ok(expectedInstance)

    val result = service.createFingerprintResetPrivilegedAction(grantRequest)

    result shouldBe Ok(expectedInstance)

    fingerprintResetF8eClient.createPrivilegedActionCalls.size shouldBe 1
    fingerprintResetF8eClient.createPrivilegedActionCalls[0].apply {
      shouldBeTypeOf<FingerprintResetRequest>()
      version shouldBe testVersion
      action shouldBe GrantAction.FINGERPRINT_RESET.value
      deviceId shouldBe "tDUi//7sUMM="
      signature shouldBe "abababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababababab"
      challenge shouldBe "AAECAwQFBgcICQoLDA0ODw=="
      hwAuthPublicKey shouldBe FullAccountMock.keybox.activeHwKeyBundle.authKey.pubKey.value
      appSignature shouldBe "test-app-signature"
    }
  }

  test("getPrivilegedActions should return PrivilegedActions with their statuses") {
    val allInstances = listOf(
      PrivilegedActionInstance(
        id = "test-id-1",
        privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
        authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
          authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
          delayStartTime = clock.now().minus(5.days),
          delayEndTime = clock.now().minus(10.hours),
          cancellationToken = "test-token",
          completionToken = "test-token"
        )
      ),
      PrivilegedActionInstance(
        id = "test-id-2",
        privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
        authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
          authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
          delayStartTime = clock.now(),
          delayEndTime = clock.now().plus(10.hours),
          cancellationToken = "test-token",
          completionToken = "test-token"
        )
      )
    )

    fingerprintResetF8eClient.getPrivilegedActionInstancesResult = Ok(allInstances)

    val result = service.getPrivilegedActions()

    fingerprintResetF8eClient.getPrivilegedActionInstancesCalls.size shouldBe 1

    result.value.size shouldBe 2
    result.value[0].status shouldBe PrivilegedActionStatus.AUTHORIZED
    result.value[1].status shouldBe PrivilegedActionStatus.PENDING
  }

  test("continueAction should return NotAuthorizedYet when delay period is not complete") {
    val actionInstance = PrivilegedActionInstance(
      id = "test-id",
      privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
      authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
        authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
        delayStartTime = clock.now(),
        delayEndTime = clock.now().plus(10.hours),
        cancellationToken = "test-token",
        completionToken = "test-token"
      )
    )

    val result = service.continueAction(actionInstance)

    result.error shouldBe PrivilegedActionError.NotAuthorized
  }

  test("continueAction should call continuePrivilegedAction when authorized") {
    val actionInstance = PrivilegedActionInstance(
      id = "test-id",
      privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
      authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
        authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
        delayStartTime = clock.now().minus(5.days),
        delayEndTime = clock.now().minus(10.hours),
        cancellationToken = "test-token",
        completionToken = "test-token"
      )
    )
    val expectedResponse = FingerprintResetResponse(
      version = 1,
      serializedRequest = "test-serialized-request",
      appSignature = "test-app-signature",
      wsmSignature = "test-wsm-signature"
    )

    fingerprintResetF8eClient.continuePrivilegedActionResult = Ok(expectedResponse)

    val result = service.continueAction(actionInstance)

    fingerprintResetF8eClient.continuePrivilegedActionCalls.size shouldBe 1
    fingerprintResetF8eClient.continuePrivilegedActionCalls[0].apply {
      shouldBeTypeOf<ContinuePrivilegedActionRequest>()
      privilegedActionInstance.id shouldBe actionInstance.id
      privilegedActionInstance.authorizationStrategy.completionToken shouldBe "test-token"
    }

    result.value shouldBe expectedResponse
  }

  test("completeFingerprintResetAndGetGrant should return InvalidResponse error for invalid response data") {
    val actionInstance = PrivilegedActionInstance(
      id = "test-id",
      privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
      authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
        authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
        delayStartTime = clock.now().minus(5.days),
        delayEndTime = clock.now().minus(10.hours),
        cancellationToken = "test-token",
        completionToken = "completion-token"
      )
    )
    val invalidResponse = FingerprintResetResponse(
      version = 1,
      serializedRequest = "not-valid-base64!",
      appSignature = "not-valid-hex!",
      wsmSignature = "not-valid-hex!"
    )

    fingerprintResetF8eClient.getPrivilegedActionInstancesResult = Ok(listOf(actionInstance))
    fingerprintResetF8eClient.continuePrivilegedActionResult = Ok(invalidResponse)

    val result = service.completeFingerprintResetAndGetGrant(
      "test-id",
      "completion-token"
    )

    fingerprintResetF8eClient.continuePrivilegedActionCalls.size shouldBe 1

    result.isErr shouldBe true
    result.error.shouldBeTypeOf<PrivilegedActionError.InvalidResponse>()
  }

  test("completeFingerprintResetAndGetGrant persists grant successfully") {
    val actionInstance = PrivilegedActionInstance(
      id = "test-id",
      privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
      authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
        delayStartTime = clock.now().minus(5.days),
        delayEndTime = clock.now().minus(10.hours),
        completionToken = "completion-token",
        cancellationToken = "cancellation-token",
        authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY
      )
    )

    val grantRequestBytes = GrantTestHelpers.createMockSerializedGrantRequest(
      GrantAction.FINGERPRINT_RESET
    )
    val response = FingerprintResetResponse(
      version = 1,
      serializedRequest = grantRequestBytes.encodeBase64(),
      appSignature = mockDerSignature,
      wsmSignature = mockDerSignature
    )

    fingerprintResetF8eClient.getPrivilegedActionInstancesResult = Ok(listOf(actionInstance))
    fingerprintResetF8eClient.continuePrivilegedActionResult = Ok(response)

    grantDao.getGrantByAction(GrantAction.FINGERPRINT_RESET) shouldBe Ok(null)

    val result = service.completeFingerprintResetAndGetGrant(
      "test-id",
      "completion-token"
    )

    fingerprintResetF8eClient.continuePrivilegedActionCalls.size shouldBe 1
    result.isOk shouldBe true

    val persistedGrant = grantDao.getGrantByAction(GrantAction.FINGERPRINT_RESET)
    persistedGrant.isOk shouldBe true
    persistedGrant.value shouldBe result.value
  }

  test("completeFingerprintResetAndGetGrant continues flow when grant persistence fails") {
    val actionInstance = PrivilegedActionInstance(
      id = "test-id",
      privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
      authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
        delayStartTime = clock.now().minus(5.days),
        delayEndTime = clock.now().minus(10.hours),
        completionToken = "completion-token",
        cancellationToken = "cancellation-token",
        authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY
      )
    )

    val grantRequestBytes = GrantTestHelpers.createMockSerializedGrantRequest(
      GrantAction.FINGERPRINT_RESET
    )
    val response = FingerprintResetResponse(
      version = 1,
      serializedRequest = grantRequestBytes.encodeBase64(),
      appSignature = mockDerSignature,
      wsmSignature = mockDerSignature
    )

    fingerprintResetF8eClient.getPrivilegedActionInstancesResult = Ok(listOf(actionInstance))
    fingerprintResetF8eClient.continuePrivilegedActionResult = Ok(response)

    // Make the grant DAO fail on persistence
    grantDao.shouldFailOnSave = true

    val result = service.completeFingerprintResetAndGetGrant(
      "test-id",
      "completion-token"
    )

    fingerprintResetF8eClient.continuePrivilegedActionCalls.size shouldBe 1

    // Should still succeed even if grant persistence fails
    result.isOk shouldBe true

    // Verify grant was not persisted due to failure
    val persistedGrant = grantDao.getGrantByAction(GrantAction.FINGERPRINT_RESET)
    persistedGrant shouldBe Ok(null)
  }

  test("cancelFingerprintReset deletes grant from database without server call when grant exists") {
    val grant = createMockGrant()
    grantDao.saveGrant(grant) shouldBe Ok(Unit)
    grantDao.getGrantByAction(GrantAction.FINGERPRINT_RESET) shouldBe Ok(grant)

    val result = service.cancelFingerprintReset("cancellation-token")

    result shouldBe Ok(Unit)

    val persistedGrant = grantDao.getGrantByAction(GrantAction.FINGERPRINT_RESET)
    persistedGrant shouldBe Ok(null)

    service.fingerprintResetAction.value shouldBe null

    fingerprintResetF8eClient.cancelFingerprintResetCalls.size shouldBe 0
  }

  test("cancelFingerprintReset makes server call when no grant exists") {
    grantDao.getGrantByAction(GrantAction.FINGERPRINT_RESET) shouldBe Ok(null)

    fingerprintResetF8eClient.cancelFingerprintResetResult = Ok(EmptyResponseBody)

    val result = service.cancelFingerprintReset("cancellation-token")

    fingerprintResetF8eClient.cancelFingerprintResetCalls.size shouldBe 1

    result shouldBe Ok(Unit)

    val persistedGrant = grantDao.getGrantByAction(GrantAction.FINGERPRINT_RESET)
    persistedGrant shouldBe Ok(null)

    service.fingerprintResetAction.value shouldBe null
  }

  test("getPendingFingerprintResetGrant returns null when no grant exists in cache or database") {
    grantDao.getGrantByAction(GrantAction.FINGERPRINT_RESET) shouldBe Ok(null)

    val result = service.getPendingFingerprintResetGrant()

    result shouldBe Ok(null)
  }

  test("getPendingFingerprintResetGrant returns grant from database and updates cache when cache is empty") {
    val grant = createMockGrant()
    grantDao.saveGrant(grant) shouldBe Ok(Unit)

    service = FingerprintResetServiceImpl(
      privilegedActionF8eClient = fingerprintResetF8eClient,
      accountService = accountService,
      signatureUtils = signatureUtils,
      clock = clock,
      grantDao = grantDao,
      hardwareUnlockInfoService = hardwareUnlockInfoService,
      messageSigner = messageSigner
    )

    service.getPendingFingerprintResetGrant() shouldBe Ok(grant)
  }

  test("getPendingFingerprintResetGrant returns latest grant from database") {
    val firstGrant = createMockGrant()
    grantDao.saveGrant(firstGrant) shouldBe Ok(Unit)

    service.getPendingFingerprintResetGrant() shouldBe Ok(firstGrant)

    val secondGrant = createMockGrant(signatureOffset = 100).copy()
    grantDao.saveGrant(secondGrant) shouldBe Ok(Unit)

    val result = service.getPendingFingerprintResetGrant()
    result shouldBe Ok(secondGrant)
  }

  test("pendingFingerprintResetGrant flow emits current grant from database") {
    val grant = createMockGrant()
    grantDao.saveGrant(grant) shouldBe Ok(Unit)

    val newService = FingerprintResetServiceImpl(
      privilegedActionF8eClient = fingerprintResetF8eClient,
      accountService = accountService,
      signatureUtils = signatureUtils,
      clock = clock,
      grantDao = grantDao,
      hardwareUnlockInfoService = hardwareUnlockInfoService,
      messageSigner = messageSigner
    )

    newService.getPendingFingerprintResetGrant() shouldBe Ok(grant)
  }

  test("getPendingFingerprintResetGrant cache is updated after successful database calls") {
    grantDao.reset()

    val freshService = FingerprintResetServiceImpl(
      privilegedActionF8eClient = fingerprintResetF8eClient,
      accountService = accountService,
      signatureUtils = signatureUtils,
      clock = clock,
      grantDao = grantDao,
      hardwareUnlockInfoService = hardwareUnlockInfoService,
      messageSigner = messageSigner
    )

    // Verify no grant initially
    freshService.getPendingFingerprintResetGrant() shouldBe Ok(null)

    val grant = createMockGrant()
    grantDao.saveGrant(grant) shouldBe Ok(Unit)

    // Verify the grant is now available in the service's state
    freshService.getPendingFingerprintResetGrant() shouldBe Ok(grant)
  }

  test("grant delivery status lifecycle") {
    // No grant initially - should return false
    service.isGrantDelivered() shouldBe false

    // Grant exists but not delivered - should return false
    val grant = createMockGrant()
    grantDao.saveGrant(grant) shouldBe Ok(Unit)
    service.isGrantDelivered() shouldBe false

    // Mark as delivered - should succeed and return true afterward
    val result = service.markGrantAsDelivered()
    result shouldBe Ok(Unit)
    service.isGrantDelivered() shouldBe true
  }

  test("markGrantAsDelivered fails when no grant exists") {
    grantDao.getGrantByAction(GrantAction.FINGERPRINT_RESET) shouldBe Ok(null)

    val result = service.markGrantAsDelivered()

    result.shouldBeErrOfType<DbError>()
  }

  test("clearEnrolledFingerprints succeeds") {
    hardwareUnlockInfoService.replaceAllUnlockInfo(
      listOf(
        UnlockInfo(unlockMethod = UnlockMethod.BIOMETRICS, fingerprintIdx = 0),
        UnlockInfo(unlockMethod = UnlockMethod.BIOMETRICS, fingerprintIdx = 1),
        UnlockInfo(unlockMethod = UnlockMethod.BIOMETRICS, fingerprintIdx = 2)
      )
    )

    val result = service.clearEnrolledFingerprints()

    result shouldBe Ok(Unit)

    hardwareUnlockInfoService.countUnlockInfo(UnlockMethod.BIOMETRICS).first() shouldBe 0
  }

  test("executeWork warms privileged action cache") {
    val actionInstance = PrivilegedActionInstance(
      id = "test-id-1",
      privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
      authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
        authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
        delayStartTime = clock.now().minus(5.days),
        delayEndTime = clock.now().minus(10.hours),
        cancellationToken = "test-token",
        completionToken = "test-token"
      )
    )

    fingerprintResetF8eClient.getPrivilegedActionInstancesResult = Ok(listOf(actionInstance))

    createBackgroundScope().launch {
      service.executeWork()
    }

    service.fingerprintResetAction.test {
      awaitUntilNotNull().shouldBe(actionInstance)
    }
  }
})
