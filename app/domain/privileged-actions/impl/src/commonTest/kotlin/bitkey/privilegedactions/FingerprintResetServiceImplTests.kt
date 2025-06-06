package bitkey.privilegedactions

import app.cash.turbine.Turbine
import bitkey.f8e.fingerprintreset.FingerprintResetF8eClient
import bitkey.f8e.fingerprintreset.FingerprintResetRequest
import bitkey.f8e.fingerprintreset.FingerprintResetResponse
import bitkey.f8e.privilegedactions.AuthorizationStrategy
import bitkey.f8e.privilegedactions.AuthorizationStrategyType
import bitkey.f8e.privilegedactions.ContinuePrivilegedActionRequest
import bitkey.f8e.privilegedactions.PrivilegedActionInstance
import bitkey.f8e.privilegedactions.PrivilegedActionType
import bitkey.f8e.privilegedactions.PrivilegedActionsF8eClientFake
import build.wallet.account.AccountServiceFake
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.encrypt.Secp256k1PublicKey
import build.wallet.grants.GrantAction
import build.wallet.grants.GrantRequest
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.ktor.utils.io.core.toByteArray
import kotlinx.datetime.Clock
import okio.ByteString.Companion.decodeHex
import kotlin.time.Duration.Companion.hours

class FingerprintResetF8eClientFake(turbine: (String) -> Turbine<Any>) : FingerprintResetF8eClient, PrivilegedActionsF8eClientFake<FingerprintResetRequest, FingerprintResetResponse>(
  turbine
)

class FingerprintResetServiceImplTests : FunSpec({
  val fingerprintResetF8eClient = FingerprintResetF8eClientFake(turbines::create)

  val accountService = AccountServiceFake()

  val service = FingerprintResetServiceImpl(
    privilegedActionF8eClient = fingerprintResetF8eClient,
    accountService = accountService
  )

  beforeTest { accountService.setActiveAccount(FullAccountMock) }

  test("createFingerprintResetPrivilegedAction should create a fingerprint reset request and call createPrivilegedAction") {
    val hwAuthPublicKey = HwAuthPublicKey(pubKey = Secp256k1PublicKey(value = "hwAuthPublicKey-test"))
    val testVersion = 1
    val testDeviceId = "test-device-id"
    val testChallenge = listOf(12, 255, 0, 128)
    val testSignature = "test-signature"
    val grantRequest = GrantRequest(
      version = testVersion.toByte(),
      deviceId = testDeviceId.toByteArray(),
      challenge = testChallenge.map { (it and 0xFF).toByte() }.toByteArray(),
      action = GrantAction.FINGERPRINT_RESET,
      signature = testSignature.toByteArray()
    )
    val expectedInstance = PrivilegedActionInstance(
      id = "test-id",
      privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
      authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
        authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
        delayEndTime = Clock.System.now(),
        cancellationToken = "test-token",
        completionToken = "test-token"
      )
    )

    fingerprintResetF8eClient.createPrivilegedActionResult = Ok(expectedInstance)

    val result = service.createFingerprintResetPrivilegedAction(hwAuthPublicKey, grantRequest)

    result shouldBe Ok(expectedInstance)

    val request = fingerprintResetF8eClient.createPrivilegedActionCalls.awaitItem() as FingerprintResetRequest
    request.version shouldBe testVersion
    request.deviceId shouldBe testDeviceId
    request.challenge shouldBe testChallenge
    request.signature.decodeHex().utf8() shouldBe "test-signature"
    request.hwAuthPublicKey shouldBe hwAuthPublicKey.pubKey.value
  }

  test("getPrivilegedActions should return PrivilegedActions with their statuses") {
    val allInstances = listOf(
      PrivilegedActionInstance(
        id = "test-id",
        privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
        authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
          authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
          delayEndTime = Clock.System.now().minus(10.hours),
          cancellationToken = "test-token",
          completionToken = "test-token"
        )
      ),
      PrivilegedActionInstance(
        id = "test-id",
        privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
        authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
          authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
          delayEndTime = Clock.System.now().plus(10.hours),
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

  test("continueAction should return NotAuthorizedYet when delay period is not complete") {
    val actionInstance = PrivilegedActionInstance(
      id = "test-id",
      privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
      authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
        authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
        delayEndTime = Clock.System.now().plus(10.hours),
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

    val result = service.continueAction(actionInstance)

    result.error shouldBe PrivilegedActionError.NotAuthorized
  }

  test("continueAction should call continuePrivilegedAction when authorized") {
    val actionInstance = PrivilegedActionInstance(
      id = "test-id",
      privilegedActionType = PrivilegedActionType.RESET_FINGERPRINT,
      authorizationStrategy = AuthorizationStrategy.DelayAndNotify(
        authorizationStrategyType = AuthorizationStrategyType.DELAY_AND_NOTIFY,
        delayEndTime = Clock.System.now().minus(10.hours),
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

    val result = service.continueAction(actionInstance)

    val request = fingerprintResetF8eClient.continuePrivilegedActionCalls.awaitItem()

    request.shouldBeTypeOf<ContinuePrivilegedActionRequest>()
    request.privilegedActionInstance.id.shouldBe(actionInstance.id)
    request.privilegedActionInstance.authorizationStrategy.completionToken.shouldBe("test-token")

    val continueRequest = fingerprintResetF8eClient.continuePrivilegedActionResult
    continueRequest?.value shouldBe expectedResponse
  }
})
