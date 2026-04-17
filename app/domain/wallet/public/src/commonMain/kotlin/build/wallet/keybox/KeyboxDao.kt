package build.wallet.keybox

import build.wallet.bitkey.app.AppAuthPublicKeys
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.keybox.Keybox
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
interface KeyboxDao {
  /**
   * Flow that emits currently active [Keybox].
   */
  fun activeKeybox(): Flow<Result<Keybox?, Error>>

  /**
   * Flow that emits [Keybox] currently being onboarded, if any.
   */
  fun onboardingKeybox(): Flow<Result<Keybox?, Error>>

  /**
   * Returns the currently active [Keybox] or the [Keybox] currently being onboarded, if
   * there is no active keybox.
   */
  suspend fun getActiveOrOnboardingKeybox(): Result<Keybox?, Error>

  /**
   * Persists [Keybox] locally and activates it.
   */
  suspend fun saveKeyboxAsActive(keybox: Keybox): Result<Unit, Error>

  /**
   * Persists [Keybox] locally as the keybox currently being onboarded.
   * Note: does NOT activate it – [activateKeybox] must be called separately.
   */
  suspend fun saveKeyboxAndBeginOnboarding(keybox: Keybox): Result<Unit, Error>

  /**
   * This activates the given keybox and clears out the saved onboarding keybox.
   */
  suspend fun activateNewKeyboxAndCompleteOnboarding(keybox: Keybox): Result<Unit, Error>

  /**
   * Rotates the app auth keys to a new set.
   * When [newHwAuthPublicKey] is provided (W3 upgrade), the active hardware auth key is also
   * updated atomically in the same transaction.
   */
  suspend fun rotateKeyboxAuthKeys(
    keyboxToRotate: Keybox,
    appAuthKeys: AppAuthPublicKeys,
    newHwAuthPublicKey: HwAuthPublicKey? = null,
  ): Result<Keybox, Error>

  /**
   * Updates the app global auth key HW signature for the given keybox.
   * Used by W3 hardware where the signature is obtained after initial account creation
   * via [NfcCommands.verifyKeysAndBuildDescriptor].
   */
  suspend fun updateAppGlobalAuthKeyHwSignature(
    keybox: Keybox,
    signature: AppGlobalAuthKeyHwSignature,
  ): Result<Keybox, Error>

  /**
   * Clear local [Keybox] state (active and inactive keyboxes and keysets).
   */
  suspend fun clear(): Result<Unit, Error>
}
