package build.wallet.nfc.transaction

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.crypto.PublicKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.nfc.HardwareProvisionedAppKeyStatusDao
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.NfcCommands
import com.github.michaelbull.result.getOrThrow
import okio.ByteString.Companion.decodeHex

@BitkeyInject(AppScope::class)
class ProvisionAppAuthKeyTransactionProviderImpl(
  private val hardwareProvisionedAppKeyStatusDao: HardwareProvisionedAppKeyStatusDao,
) : ProvisionAppAuthKeyTransactionProvider {
  override operator fun invoke(
    appGlobalAuthPublicKey: PublicKey<AppGlobalAuthKey>,
    onSuccess: () -> Unit,
    onCancel: () -> Unit,
  ) = object : NfcTransaction<Unit> {
    private lateinit var hwAuthKey: HwAuthPublicKey

    override val needsAuthentication = true
    override val shouldLock = true

    override suspend fun session(
      session: NfcSession,
      commands: NfcCommands,
    ) {
      // Get the hardware auth key before provisioning
      hwAuthKey = commands.getAuthenticationKey(session)

      // Provision the app auth key to the hardware
      commands.provisionAppAuthKey(session, appGlobalAuthPublicKey.value.decodeHex())
    }

    override fun onCancel() = onCancel()

    override suspend fun onSuccess(response: Unit) {
      // Record the provisioned key in the database
      hardwareProvisionedAppKeyStatusDao.recordProvisionedKey(
        hwAuthPubKey = hwAuthKey,
        appAuthPubKey = appGlobalAuthPublicKey
      ).getOrThrow()

      onSuccess()
    }
  }
}
