package build.wallet.nfc.transaction

interface StartFingerprintEnrollmentTransactionProvider {
  operator fun invoke(
    onSuccess: () -> Unit,
    onCancel: () -> Unit,
    isHardwareFake: Boolean,
  ): NfcTransaction<Boolean>
}
