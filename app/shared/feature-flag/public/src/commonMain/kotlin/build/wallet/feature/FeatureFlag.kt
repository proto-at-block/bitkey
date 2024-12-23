package build.wallet.feature

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getOrElse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.reflect.KClass

/**
 * The base feature flag class that specific feature flags inherit.
 *
 * It provides common functionality for all flags, including caching the flag value and
 * managing initializing from and updating to a persistent store.
 */
open class FeatureFlag<T : FeatureFlagValue>(
  /** A unique programmatic identifier for the flag. */
  val identifier: String,
  /** A human-readable title for the flag. */
  val title: String,
  /** A human-readable description for the flag. */
  val description: String,
  /** The default value for the flag before it is initialized from the persistent store.  */
  val defaultFlagValue: T,
  /** The persistent store to read and write values to and from. */
  private val featureFlagDao: FeatureFlagDao,
  /** The type of this flag. */
  private val type: KClass<T>,
) {
  /**
   * The flow of the feature flag value.
   * Starts as [defaultFlagValue] until [initializeFromDao] is called.
   */
  private val valueFlow = MutableStateFlow(value = defaultFlagValue)

  /** The value that callers should read from. */
  fun flagValue(): StateFlow<T> {
    return valueFlow
  }

  /**
   * The value that callers should write to.
   * Writes to this value also get written to the persistent store.
   */
  suspend fun setFlagValue(value: T) {
    valueFlow.emit(value)
    persistFlagValue(value)
  }

  /**
   * Initializes this flag from the persistent store.
   * Should be called at app launch.
   */
  suspend fun initializeFromDao() {
    val persistedValue =
      featureFlagDao.getFlag(
        featureFlagId = identifier,
        kClass = type
      ).get() ?: defaultFlagValue

    valueFlow.emit(persistedValue)
  }

  /**
   * Allows individual flags to check that the app / account is
   * in the correct state to allow setting the flag value.
   * Returns an error message if not.
   */
  @Suppress("FunctionOnlyReturningConstant")
  open suspend fun canSetValue(value: T): Result<Unit, String> {
    return Ok(Unit)
  }

  private suspend fun persistFlagValue(flagValue: FeatureFlagValue) {
    featureFlagDao.setFlag(
      flagValue = flagValue,
      featureFlagId = identifier
    )
  }

  /**
   * Returns whether this flag has been overridden. An override is when a customer goes to the
   * Debug menu and manually changes a feature flag value.
   */
  suspend fun isOverridden(): Boolean =
    featureFlagDao
      .getFlagOverridden(identifier)
      .getOrElse { false }

  /**
   * Sets whether this flag has been overridden. This should only be called via the Debug screen.
   */
  suspend fun setOverridden(overridden: Boolean) =
    featureFlagDao
      .setFlagOverridden(identifier, overridden)

  /**
   * A convenience function for setting the flag value and overridden state at the same time.
   * Should only be called via the Debug screen.
   */
  suspend fun setFlagValue(
    value: T,
    overridden: Boolean,
  ) {
    setFlagValue(value)
    setOverridden(overridden)
    onFlagChanged(value)
  }

  /**
   * Reset the flag state to the default value and clear overrides. For use in the debug menu.
   */
  suspend fun reset() {
    setFlagValue(
      value = defaultFlagValue,
      overridden = false
    )
  }

  open fun onFlagChanged(newValue: T) = Unit
}
