package build.wallet.nfc.transaction

class StartFingerprintEnrollmentTransactionProviderFake :
  StartFingerprintEnrollmentTransactionProvider {
  override fun invoke(
    onSuccess: () -> Unit,
    onCancel: () -> Unit,
    isHardwareFake: Boolean,
  ) = NfcTransactionMock(
    value = true,
    onSuccess = {
      onSuccess()
    },
    onCancel = onCancel
  )
}
