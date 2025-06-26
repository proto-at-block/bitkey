package bitkey.privilegedactions

import bitkey.f8e.fingerprintreset.FingerprintResetRequest
import bitkey.f8e.fingerprintreset.FingerprintResetResponse
import bitkey.f8e.privilegedactions.AuthorizationStrategy
import bitkey.f8e.privilegedactions.AuthorizationStrategyType
import bitkey.f8e.privilegedactions.ContinuePrivilegedActionRequest
import bitkey.f8e.privilegedactions.PrivilegedActionInstance
import bitkey.f8e.privilegedactions.PrivilegedActionType
import build.wallet.account.AccountServiceFake
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.encrypt.Secp256k1PublicKey
import build.wallet.encrypt.SignatureUtilsMock
import build.wallet.grants.GrantAction
import build.wallet.grants.GrantRequest
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.ktor.utils.io.core.toByteArray
import okio.ByteString.Companion.decodeHex
import kotlin.time.Duration.Companion.hours

class FingerprintResetServiceImplTests : FunSpec({
  val fingerprintResetF8eClient = FingerprintResetF8eClientFake(turbines::create)

  val clock = ClockFake()
  val accountService = AccountServiceFake()
  val signatureUtils = SignatureUtilsMock()

  val service = FingerprintResetServiceImpl(
    privilegedActionF8eClient = fingerprintResetF8eClient,
    accountService = accountService,
    signatureUtils = signatureUtils
  )

  beforeTest { accountService.setActiveAccount(FullAccountMock) }

  test("createFingerprintResetPrivilegedAction should create a fingerprint reset request and call createPrivilegedAction") {
    val hwAuthPublicKey = HwAuthPublicKey(pubKey = Secp256k1PublicKey(value = "hw-auth-dpub"))
    val testVersion = 1
    val testDeviceId = byteArrayOf(-76, 53, 34, -1, -2, -20, 80, -61)
    val testChallengeBytes = listOf(12, 255, 0, 128).map { (it and 0xFF).toByte() }.toByteArray()
    val testSignature = "21a1aa12efc8512727856a9ccc428a511cf08b211f26551781ae0a37661de8060c566ded9486500f6927e9c9df620c65653c68316e61930a49ecab31b3bec498"
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
        delayEndTime = clock.now(),
        cancellationToken = "test-token",
        completionToken = "test-token"
      )
    )

    fingerprintResetF8eClient.createPrivilegedActionResult = Ok(expectedInstance)

    val result = service.createFingerprintResetPrivilegedAction(grantRequest)

    result shouldBe Ok(expectedInstance)

    val request = fingerprintResetF8eClient.createPrivilegedActionCalls.awaitItem() as FingerprintResetRequest
    request.version shouldBe testVersion
    request.action shouldBe GrantAction.FINGERPRINT_RESET.value
    request.deviceId shouldBe "tDUi//7sUMM="
    request.challenge shouldBe "DP8AgA=="
    request.signature shouldBe "MEQCICGhqhLvyFEnJ4VqnMxCilEc8IshHyZVF4GuCjdmHegGAiAMVm3tlIZQD2kn6cnfYgxlZTxoMW5hkwpJ7Ksxs77EmA=="
    request.hwAuthPublicKey shouldBe hwAuthPublicKey.pubKey.value
  }

  xtest("getPrivilegedActions should return PrivilegedActions with their statuses") {
    val allInstances = listOf(
      PrivilegedActionInstance(
        id = "test-id",
        privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
        authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
          authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
          delayEndTime = clock.now().minus(10.hours),
          cancellationToken = "test-token",
          completionToken = "test-token"
        )
      ),
      PrivilegedActionInstance(
        id = "test-id",
        privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
        authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
          authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
          delayEndTime = clock.now().plus(10.hours),
          cancellationToken = "test-token",
          completionToken = "test-token"
        )
      )
    )

    fingerprintResetF8eClient.getPrivilegedActionInstancesResult = Ok(allInstances)

    val result = service.getPrivilegedActions()

    fingerprintResetF8eClient.getPrivilegedActionInstancesCalls.awaitItem()
    result.value.size shouldBe 2
    result.value[0].status shouldBe PrivilegedActionStatus.AUTHORIZED
    result.value[1].status shouldBe PrivilegedActionStatus.PENDING
  }

  xtest("continueAction should return NotAuthorizedYet when delay period is not complete") {
    val actionInstance = PrivilegedActionInstance(
      id = "test-id",
      privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
      authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
        authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
        delayEndTime = clock.now().plus(10.hours),
        cancellationToken = "test-token",
        completionToken = "test-token"
      )
    )
    val expectedResponse = FingerprintResetResponse(
      version = 1,
      serializedRequest = "test-serialized-request",
      signature = "test-signature"
    )

    fingerprintResetF8eClient.continuePrivilegedActionResult = Ok(expectedResponse)

    val actualResponse = service.continueAction(actionInstance)

    actualResponse.error shouldBe PrivilegedActionError.NotAuthorized
  }

  test("continueAction should call continuePrivilegedAction when authorized") {
    val actionInstance = PrivilegedActionInstance(
      id = "test-id",
      privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
      authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
        authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
        delayEndTime = clock.now().minus(10.hours),
        cancellationToken = "test-token",
        completionToken = "test-token"
      )
    )
    val expectedResponse = FingerprintResetResponse(
      version = 1,
      serializedRequest = "test-serialized-request",
      signature = "test-signature"
    )

    fingerprintResetF8eClient.continuePrivilegedActionResult = Ok(expectedResponse)

    val actualResponse = service.continueAction(actionInstance)

    val request = fingerprintResetF8eClient.continuePrivilegedActionCalls.awaitItem()

    request.shouldBeTypeOf<ContinuePrivilegedActionRequest>()
    request.privilegedActionInstance.id.shouldBe(actionInstance.id)
    request.privilegedActionInstance.authorizationStrategy.completionToken.shouldBe("test-token")

    actualResponse.value shouldBe expectedResponse
  }

  test("completeFingerprintResetAndGetGrant should return InvalidResponse error for invalid response data") {
    val actionInstance = PrivilegedActionInstance(
      id = "test-id",
      privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
      authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
        authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
        delayEndTime = clock.now().minus(10.hours),
        cancellationToken = "test-token",
        completionToken = "test-token"
      )
    )
    val invalidResponse = FingerprintResetResponse(
      version = 1,
      serializedRequest = "not-valid-base64!",
      signature = "not-valid-hex!"
    )

    fingerprintResetF8eClient.getPrivilegedActionInstancesResult = Ok(listOf(actionInstance))
    fingerprintResetF8eClient.continuePrivilegedActionResult = Ok(invalidResponse)

    val result = service.completeFingerprintResetAndGetGrant("test-id")

    // Consume the expected calls
    fingerprintResetF8eClient.getPrivilegedActionInstancesCalls.awaitItem()
    fingerprintResetF8eClient.continuePrivilegedActionCalls.awaitItem()

    result.isErr shouldBe true
    result.error.shouldBeTypeOf<PrivilegedActionError.InvalidResponse>()
  }
})
