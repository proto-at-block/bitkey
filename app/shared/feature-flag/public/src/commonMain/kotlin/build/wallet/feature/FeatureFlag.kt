package build.wallet.feature

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
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
}
