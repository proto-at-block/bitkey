package build.wallet.statemachine.settings.full.device.fingerprints

import build.wallet.firmware.EnrolledFingerprints
import build.wallet.firmware.FingerprintEnrollmentStatus
import build.wallet.firmware.FingerprintHandle
import build.wallet.logging.log
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.NfcCommands

class FingerprintNfcCommandsImpl : FingerprintNfcCommands {
  override suspend fun prepareForFingerprintEnrollment(
    commands: NfcCommands,
    session: NfcSession,
    enrolledFingerprints: EnrolledFingerprints,
    fingerprintToEnroll: FingerprintHandle,
  ): Boolean {
    // Cancel any ongoing enrollment before attempting the new one. This can occur if the user
    // starts an enrollment through the app, does not complete the enrollment, and backs out of the
    // enrollment instructions screen and finally starts another enrollment.
    commands.cancelFingerprintEnrollment(session)

    // Similarly to the above, the user could have actually completed the enrollment, but again
    // backed out of the instructions before confirming the enrollment and getting the opportunity
    // to read the latest fingerprint from the hardware. To handle this case, we first see if there
    // is an existing fingerprint handle, and if so, we delete it before starting the new enrollment.
    val latestEnrolledFingerprints = commands.getEnrolledFingerprints(session)
    if (latestEnrolledFingerprints != enrolledFingerprints) {
      latestEnrolledFingerprints.fingerprintHandles.find { it.index == fingerprintToEnroll.index }
        ?.let { commands.deleteFingerprint(session, it.index) }
    }

    // Finally, start the new enrollment!
    return commands.startFingerprintEnrollment(
      session = session,
      fingerprintHandle = fingerprintToEnroll
    )
  }

  override suspend fun checkEnrollmentStatus(
    commands: NfcCommands,
    session: NfcSession,
    enrolledFingerprints: EnrolledFingerprints,
    fingerprintToEnroll: FingerprintHandle,
  ): EnrollmentStatusResult {
    val enrollmentResult = commands.getFingerprintEnrollmentStatus(
      session = session,
      isEnrollmentContextAware = true
    )

    val latestEnrolledFingerprints = commands.getEnrolledFingerprints(session)

    return when (enrollmentResult.status) {
      FingerprintEnrollmentStatus.COMPLETE -> EnrollmentStatusResult.Complete(
        enrolledFingerprints = latestEnrolledFingerprints
      )
      FingerprintEnrollmentStatus.INCOMPLETE -> EnrollmentStatusResult.Incomplete
      FingerprintEnrollmentStatus.NOT_IN_PROGRESS -> {
        if (latestEnrolledFingerprints.fingerprintHandles.size > enrolledFingerprints.fingerprintHandles.size
        ) {
          // The fingerprint was actually successfully enrolled, but the firmware timed out
          // before the user tapped again. Fake success and do not attempt to restart
          // enrollment.
          EnrollmentStatusResult.Complete(enrolledFingerprints = latestEnrolledFingerprints)
        } else {
          // Otherwise, immediately restart the enrollment
          commands.startFingerprintEnrollment(session, fingerprintToEnroll)
          EnrollmentStatusResult.Incomplete
        }
      }
      FingerprintEnrollmentStatus.UNSPECIFIED -> EnrollmentStatusResult.Unspecified
    }
  }
}
