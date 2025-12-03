package build.wallet.statemachine.settings.full.device.fingerprints

import bitkey.privilegedactions.FingerprintResetF8eClientFake
import bitkey.privilegedactions.FingerprintResetServiceFake
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.coroutines.turbine.turbines
import build.wallet.db.DbQueryError
import build.wallet.firmware.EnrolledFingerprints
import build.wallet.firmware.EnrolledFingerprints.Companion.FIRST_FINGERPRINT_INDEX
import build.wallet.firmware.FingerprintHandle
import build.wallet.grants.Grant
import build.wallet.nfc.NfcCommandsMock
import build.wallet.nfc.NfcSessionFake
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FingerprintResetGrantProvisionResult
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class FingerprintResetGrantNfcHandlerTests : FunSpec({
  val clock = ClockFake()
  val fingerprintResetServiceFake = FingerprintResetServiceFake(
    privilegedActionF8eClient = FingerprintResetF8eClientFake(clock),
    clock = clock
  )
  val nfcCommandsMock = NfcCommandsMock(turbine = turbines::create)
  val nfcSessionFake = NfcSessionFake()

  val handler = FingerprintResetGrantNfcHandler(fingerprintResetServiceFake)

  val testGrant = Grant(
    version = 1,
    serializedRequest = byteArrayOf(1, 2, 3),
    appSignature = byteArrayOf(4, 5, 6),
    wsmSignature = byteArrayOf(7, 8, 9)
  )

  val testEventTrackerContext = NfcEventTrackerScreenIdContext.ENROLLING_NEW_FINGERPRINT

  beforeTest {
    fingerprintResetServiceFake.reset()
    nfcCommandsMock.reset()
  }

  context("createGrantProvisionProps") {
    test("should create props with correct configuration") {
      val props = handler.createGrantProvisionProps(
        grant = testGrant,
        onSuccess = { },
        eventTrackerContext = testEventTrackerContext
      )

      props.needsAuthentication shouldBe false
      props.shouldLock shouldBe false
      props.eventTrackerContext shouldBe testEventTrackerContext
      props.hardwareVerification shouldBe NfcSessionUIStateMachineProps.HardwareVerification.NotRequired
    }

    test("should provide grant successfully when grant not yet delivered") {
      nfcCommandsMock.setProvideGrantResult(true)
      nfcCommandsMock.setStartFingerprintEnrollmentResult(true)
      fingerprintResetServiceFake.isGrantDelivered = false
      fingerprintResetServiceFake.markGrantAsDeliveredResult = Ok(Unit)
      fingerprintResetServiceFake.clearEnrolledFingerprintsResult = Ok(Unit)

      val props = handler.createGrantProvisionProps(
        grant = testGrant,
        onSuccess = { },
        eventTrackerContext = testEventTrackerContext
      )

      val result = props.session(nfcSessionFake, nfcCommandsMock)

      result shouldBe FingerprintResetGrantProvisionResult.ProvideGrantSuccess
      nfcCommandsMock.provideGrantCalls.awaitItem() shouldBe testGrant
      fingerprintResetServiceFake.markGrantAsDeliveredCalls.size shouldBe 1
      fingerprintResetServiceFake.clearEnrolledFingerprintsCalls.size shouldBe 1
      nfcCommandsMock.startFingerprintEnrollmentCalls.awaitItem() shouldBe FingerprintHandle(FIRST_FINGERPRINT_INDEX, "")
    }

    test("should return FingerprintResetComplete when grant delivery fails but was previously delivered") {
      val enrolledFingerprints = EnrolledFingerprints(
        fingerprintHandles = listOf(
          FingerprintHandle(index = 0, label = "Primary"),
          FingerprintHandle(index = 1, label = "Secondary"),
          FingerprintHandle(index = 2, label = "Tertiary")
        )
      )

      nfcCommandsMock.setProvideGrantResult(false)
      fingerprintResetServiceFake.markGrantAsDelivered()
      nfcCommandsMock.setEnrolledFingerprints(enrolledFingerprints)
      nfcCommandsMock.setDeleteFingerprintResult(true)
      fingerprintResetServiceFake.deleteFingerprintResetGrantResult = Ok(Unit)

      val props = handler.createGrantProvisionProps(
        grant = testGrant,
        onSuccess = { },
        eventTrackerContext = testEventTrackerContext
      )

      val result = props.session(nfcSessionFake, nfcCommandsMock)

      result.shouldBeInstanceOf<FingerprintResetGrantProvisionResult.FingerprintResetComplete>()
      val completeResult = result as FingerprintResetGrantProvisionResult.FingerprintResetComplete

      // Should only have the first fingerprint (index 0)
      completeResult.enrolledFingerprints.fingerprintHandles.size shouldBe 1
      completeResult.enrolledFingerprints.fingerprintHandles[0].index shouldBe 0
      completeResult.enrolledFingerprints.fingerprintHandles[0].label shouldBe "Primary"

      // Should have consumed the provideGrant and getEnrolledFingerprints calls
      nfcCommandsMock.provideGrantCalls.awaitItem() shouldBe testGrant
      nfcCommandsMock.getEnrolledFingerprintsCalls.awaitItem() shouldBe enrolledFingerprints

      // Should have deleted fingerprints at indices 1 and 2
      nfcCommandsMock.deleteFingerprintCalls.awaitItem() shouldBe 1
      nfcCommandsMock.deleteFingerprintCalls.awaitItem() shouldBe 2

      fingerprintResetServiceFake.deleteFingerprintResetGrantCalls.size shouldBe 1
    }

    test("should return ProvideGrantFailed when grant delivery fails and was not previously delivered") {
      nfcCommandsMock.setProvideGrantResult(false)
      fingerprintResetServiceFake.isGrantDelivered = false

      val props = handler.createGrantProvisionProps(
        grant = testGrant,
        onSuccess = { },
        eventTrackerContext = testEventTrackerContext
      )

      val result = props.session(nfcSessionFake, nfcCommandsMock)

      result shouldBe FingerprintResetGrantProvisionResult.ProvideGrantFailed
      nfcCommandsMock.provideGrantCalls.awaitItem() shouldBe testGrant
    }
  }

  context("beginFingerprintEnrollment") {
    test("should successfully begin fingerprint enrollment") {
      fingerprintResetServiceFake.markGrantAsDeliveredResult = Ok(Unit)
      fingerprintResetServiceFake.clearEnrolledFingerprintsResult = Ok(Unit)
      nfcCommandsMock.setStartFingerprintEnrollmentResult(true)

      val result = handler.beginFingerprintEnrollment(nfcSessionFake, nfcCommandsMock)

      result shouldBe FingerprintResetGrantProvisionResult.ProvideGrantSuccess
      fingerprintResetServiceFake.markGrantAsDeliveredCalls.size shouldBe 1
      fingerprintResetServiceFake.clearEnrolledFingerprintsCalls.size shouldBe 1
      nfcCommandsMock.startFingerprintEnrollmentCalls.awaitItem() shouldBe FingerprintHandle(FIRST_FINGERPRINT_INDEX, "")
    }

    test("should return ProvideGrantFailed when markGrantAsDelivered fails") {
      fingerprintResetServiceFake.markGrantAsDeliveredResult = Err(DbQueryError(RuntimeException("Test error")))

      val result = handler.beginFingerprintEnrollment(nfcSessionFake, nfcCommandsMock)

      result shouldBe FingerprintResetGrantProvisionResult.ProvideGrantFailed
      fingerprintResetServiceFake.markGrantAsDeliveredCalls.size shouldBe 1
    }

    test("should return ProvideGrantFailed when clearEnrolledFingerprints fails") {
      fingerprintResetServiceFake.markGrantAsDeliveredResult = Ok(Unit)
      fingerprintResetServiceFake.clearEnrolledFingerprintsResult = Err(DbQueryError(Exception("Clear failed")))

      val result = handler.beginFingerprintEnrollment(nfcSessionFake, nfcCommandsMock)

      result shouldBe FingerprintResetGrantProvisionResult.ProvideGrantFailed
      fingerprintResetServiceFake.markGrantAsDeliveredCalls.size shouldBe 1
      fingerprintResetServiceFake.clearEnrolledFingerprintsCalls.size shouldBe 1
    }

    test("should return ProvideGrantFailed when startFingerprintEnrollment fails") {
      fingerprintResetServiceFake.markGrantAsDeliveredResult = Ok(Unit)
      fingerprintResetServiceFake.clearEnrolledFingerprintsResult = Ok(Unit)
      nfcCommandsMock.setStartFingerprintEnrollmentResult(false)

      val result = handler.beginFingerprintEnrollment(nfcSessionFake, nfcCommandsMock)

      result shouldBe FingerprintResetGrantProvisionResult.ProvideGrantFailed
      fingerprintResetServiceFake.markGrantAsDeliveredCalls.size shouldBe 1
      fingerprintResetServiceFake.clearEnrolledFingerprintsCalls.size shouldBe 1
      nfcCommandsMock.startFingerprintEnrollmentCalls.awaitItem() shouldBe FingerprintHandle(FIRST_FINGERPRINT_INDEX, "")
    }
  }
})
