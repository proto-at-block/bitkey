package build.wallet.keybox

import build.wallet.bitkey.app.AppAuthPublicKeys
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
   */
  suspend fun rotateKeyboxAuthKeys(
    keyboxToRotate: Keybox,
    appAuthKeys: AppAuthPublicKeys,
  ): Result<Keybox, Error>

  /**
   * Clear local [Keybox] state (active and inactive keyboxes and keysets).
   */
  suspend fun clear(): Result<Unit, Error>
}
