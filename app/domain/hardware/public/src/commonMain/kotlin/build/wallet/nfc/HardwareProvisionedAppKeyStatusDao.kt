package build.wallet.nfc

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.crypto.PublicKey
import build.wallet.db.DbError
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for managing hardware provisioned app key status.
 * This tracks when app auth keys are provisioned to hardware via NFC.
 */
interface HardwareProvisionedAppKeyStatusDao {
  /**
   * Records that an app auth key was provisioned to hardware.
   *
   * @param hwAuthPubKey The hardware auth public key
   * @param appAuthPubKey The app global auth public key that was provisioned
   */
  suspend fun recordProvisionedKey(
    hwAuthPubKey: HwAuthPublicKey,
    appAuthPubKey: PublicKey<AppGlobalAuthKey>,
  ): Result<Unit, DbError>

  /**
   * Checks if the active keybox has a provisioned key record matching its current keys.
   * This joins against the keybox view to determine if the hardware has been provisioned
   * with the active app auth key.
   *
   * @return true if a matching provisioned key record exists for the active keybox, false otherwise
   */
  suspend fun isKeyProvisionedForActiveAccount(): Result<Boolean, DbError>

  /**
   * Returns a flow that emits when the provisioned key status changes for the active account.
   * This is useful for reactive UI updates when provisioning status changes.
   *
   * @return Flow that emits true when the active account has a provisioned key, false otherwise
   */
  fun isKeyProvisionedForActiveAccountFlow(): Flow<Boolean>

  /**
   * Clears all hardware provisioned app key status records.
   */
  suspend fun clear(): Result<Unit, DbError>
}
