package build.wallet.statemachine.settings.full.device.fingerprints

import build.wallet.coroutines.turbine.turbines
import build.wallet.firmware.EnrolledFingerprints
import build.wallet.firmware.FingerprintEnrollmentStatus
import build.wallet.firmware.FingerprintHandle
import build.wallet.nfc.NfcCommandsMock
import build.wallet.nfc.NfcSessionFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class FingerprintNfcCommandsImplTests : FunSpec({
  val fingerprintNfcCommands = FingerprintNfcCommandsImpl()
  val nfcCommandsMock = NfcCommandsMock(turbine = turbines::create)
  val existingFingerprint = FingerprintHandle(index = 0, label = "Right thumb")
  val fingerprintToEnroll = FingerprintHandle(index = 1, label = "Left thumb")
  val existingEnrolledFingerprints = EnrolledFingerprints(
    fingerprintHandles = listOf(existingFingerprint)
  )
  val updatedEnrolledFingerprints = EnrolledFingerprints(
    fingerprintHandles = listOf(
      existingFingerprint,
      fingerprintToEnroll
    )
  )

  beforeTest {
    nfcCommandsMock.reset()
  }

  test("prepareForFingerprintEnrollment deletes enrollment if exists") {
    val fwEnrolledFingerprints = existingEnrolledFingerprints
      .copy(
        fingerprintHandles = existingEnrolledFingerprints.fingerprintHandles + fingerprintToEnroll.copy(
          label = "Right Pinky"
        )
      )
    nfcCommandsMock.setEnrolledFingerprints(fwEnrolledFingerprints)

    fingerprintNfcCommands.prepareForFingerprintEnrollment(
      commands = nfcCommandsMock,
      session = NfcSessionFake(),
      enrolledFingerprints = existingEnrolledFingerprints,
      fingerprintToEnroll = fingerprintToEnroll
    )

    nfcCommandsMock.cancelFingerprintEnrollmentCalls.awaitItem().shouldBe(Unit)
    nfcCommandsMock.getEnrolledFingerprintsCalls.awaitItem().shouldBe(fwEnrolledFingerprints)
    nfcCommandsMock.deleteFingerprintCalls.awaitItem().shouldBe(1)
    nfcCommandsMock.startFingerprintEnrollmentCalls.awaitItem().shouldBe(fingerprintToEnroll)
  }

  test("prepareForFingerprintEnrollment only deletes enrollment if index is same") {
    val fwEnrolledFingerprints = existingEnrolledFingerprints
      .copy(
        fingerprintHandles = existingEnrolledFingerprints.fingerprintHandles + fingerprintToEnroll.copy(
          index = 2,
          label = "Right Pinky"
        )
      )
    nfcCommandsMock.setEnrolledFingerprints(fwEnrolledFingerprints)

    fingerprintNfcCommands.prepareForFingerprintEnrollment(
      commands = nfcCommandsMock,
      session = NfcSessionFake(),
      enrolledFingerprints = existingEnrolledFingerprints,
      fingerprintToEnroll = fingerprintToEnroll
    )

    nfcCommandsMock.cancelFingerprintEnrollmentCalls.awaitItem().shouldBe(Unit)
    nfcCommandsMock.getEnrolledFingerprintsCalls.awaitItem().shouldBe(fwEnrolledFingerprints)
    nfcCommandsMock.startFingerprintEnrollmentCalls.awaitItem().shouldBe(fingerprintToEnroll)
  }

  test("prepareForFingerprintEnrollment starts a new enrollment") {
    fingerprintNfcCommands.prepareForFingerprintEnrollment(
      commands = nfcCommandsMock,
      session = NfcSessionFake(),
      enrolledFingerprints = existingEnrolledFingerprints,
      fingerprintToEnroll = fingerprintToEnroll
    )

    nfcCommandsMock.cancelFingerprintEnrollmentCalls.awaitItem().shouldBe(Unit)
    nfcCommandsMock.getEnrolledFingerprintsCalls.awaitItem()
    nfcCommandsMock.startFingerprintEnrollmentCalls.awaitItem().shouldBe(fingerprintToEnroll)
  }

  test("checkEnrollmentStatus complete returns latest fingerprints") {
    nfcCommandsMock.setEnrolledFingerprints(updatedEnrolledFingerprints)
    nfcCommandsMock.setEnrollmentStatus(FingerprintEnrollmentStatus.COMPLETE)

    val enrollmentStatusResult = fingerprintNfcCommands.checkEnrollmentStatus(
      commands = nfcCommandsMock,
      session = NfcSessionFake(),
      enrolledFingerprints = existingEnrolledFingerprints,
      fingerprintToEnroll = fingerprintToEnroll
    )

    nfcCommandsMock.getEnrolledFingerprintsCalls.awaitItem()
      .shouldBe(updatedEnrolledFingerprints)
    enrollmentStatusResult.shouldBeInstanceOf<EnrollmentStatusResult.Complete>()
      .enrolledFingerprints.shouldBe(updatedEnrolledFingerprints)
  }

  test("checkEnrollmentStatus incomplete returns incomplete status") {
    nfcCommandsMock.setEnrollmentStatus(FingerprintEnrollmentStatus.INCOMPLETE)

    val enrollmentStatusResult = fingerprintNfcCommands.checkEnrollmentStatus(
      commands = nfcCommandsMock,
      session = NfcSessionFake(),
      enrolledFingerprints = existingEnrolledFingerprints,
      fingerprintToEnroll = fingerprintToEnroll
    )

    nfcCommandsMock.getEnrolledFingerprintsCalls.awaitItem()
    enrollmentStatusResult.shouldBeInstanceOf<EnrollmentStatusResult.Incomplete>()
  }

  test("checkEnrollmentStatus not in progress with added fingerprint fakes success") {
    nfcCommandsMock.setEnrollmentStatus(FingerprintEnrollmentStatus.NOT_IN_PROGRESS)
    nfcCommandsMock.setEnrolledFingerprints(updatedEnrolledFingerprints)

    val enrollmentStatusResult = fingerprintNfcCommands.checkEnrollmentStatus(
      commands = nfcCommandsMock,
      session = NfcSessionFake(),
      enrolledFingerprints = existingEnrolledFingerprints,
      fingerprintToEnroll = fingerprintToEnroll
    )

    nfcCommandsMock.getEnrolledFingerprintsCalls.awaitItem()
      .shouldBe(updatedEnrolledFingerprints)
    enrollmentStatusResult.shouldBeInstanceOf<EnrollmentStatusResult.Complete>()
      .enrolledFingerprints.shouldBe(updatedEnrolledFingerprints)
  }

  test("checkEnrollmentStatus not in progress starts a new enrollment") {
    nfcCommandsMock.setEnrollmentStatus(FingerprintEnrollmentStatus.NOT_IN_PROGRESS)

    val enrollmentStatusResult = fingerprintNfcCommands.checkEnrollmentStatus(
      commands = nfcCommandsMock,
      session = NfcSessionFake(),
      enrolledFingerprints = existingEnrolledFingerprints,
      fingerprintToEnroll = fingerprintToEnroll
    )

    nfcCommandsMock.getEnrolledFingerprintsCalls.awaitItem()
    nfcCommandsMock.startFingerprintEnrollmentCalls.awaitItem().shouldBe(fingerprintToEnroll)
    enrollmentStatusResult.shouldBeInstanceOf<EnrollmentStatusResult.Incomplete>()
  }
})
