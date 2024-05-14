package build.wallet.keybox

import build.wallet.bitkey.app.AppAuthPublicKeys
import build.wallet.bitkey.keybox.Keybox
import build.wallet.db.DbError
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * TODO(W-1531): make dao calls to return Result with failure types.
 */
@Suppress("TooManyFunctions")
interface KeyboxDao {
  /**
   * Flow that emits currently active [Keybox].
   */
  fun activeKeybox(): Flow<Result<Keybox?, DbError>>

  /**
   * Flow that emits [Keybox] currently being onboarded, if any.
   */
  fun onboardingKeybox(): Flow<Result<Keybox?, DbError>>

  /**
   * Returns the currently active [Keybox] or the [Keybox] currently being onboarded, if
   * there is no active keybox.
   */
  suspend fun getActiveOrOnboardingKeybox(): Result<Keybox?, DbError>

  /**
   * Persists [Keybox] locally and activates it.
   */
  suspend fun saveKeyboxAsActive(keybox: Keybox): Result<Unit, DbError>

  /**
   * Persists [Keybox] locally as the keybox currently being onboarded.
   * Note: does NOT activate it – [activateKeybox] must be called separately.
   */
  suspend fun saveKeyboxAndBeginOnboarding(keybox: Keybox): Result<Unit, DbError>

  /**
   * This activates the given keybox and clears out the saved onboarding keybox.
   */
  suspend fun activateNewKeyboxAndCompleteOnboarding(keybox: Keybox): Result<Unit, DbError>

  /**
   * Rotates the app auth keys to a new set.
   */
  suspend fun rotateKeyboxAuthKeys(
    keyboxToRotate: Keybox,
    appAuthKeys: AppAuthPublicKeys,
  ): Result<Keybox, DbError>

  /**
   * Clear local [Keybox] state (active and inactive keyboxes and keysets).
   */
  suspend fun clear(): Result<Unit, DbError>
}
