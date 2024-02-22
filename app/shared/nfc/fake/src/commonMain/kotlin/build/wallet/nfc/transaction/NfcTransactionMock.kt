package build.wallet.nfc.transaction

import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.NfcCommands

class NfcTransactionMock<T : Any>(
  private val value: T,
  onSuccess: (T) -> Unit = {},
  onCancel: () -> Unit = {},
) : NfcTransaction<T> {
  override val isHardwareFake = true
  override val needsAuthentication = true
  override val shouldLock = true

  override suspend fun session(
    session: NfcSession,
    commands: NfcCommands,
  ) = value

  private val onCancelCallback = onCancel

  override fun onCancel() = onCancelCallback()

  private val onSuccessCallback = onSuccess

  override suspend fun onSuccess(response: T) = onSuccessCallback(response)
}
