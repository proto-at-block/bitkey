package build.wallet.datadog

/**
 * Defines `DDRUMMonitor` contract for platform-specific APIs
 *
 * https://github.com/DataDog/dd-sdk-ios/blob/develop/DatadogObjc/Sources/RUM/RUM%2Bobjc.swift#L439
 */
interface DatadogRumMonitor {
  /**
   * Notifies that the View starts being presented to the user.
   *
   * @param key: a `String` value identifying this View. It must match the `key` passed later to `stopView(key:attributes:)`.
   * @param name: the View name used for RUM Explorer. If not provided, the `key` name will be used.
   * @param attributes: custom attributes to attach to the View.
   */
  fun startView(
    key: String,
    name: String = key,
    attributes: Map<String, String> = emptyMap(),
  )

  /**
   * Notifies that the View stops being presented to the user.
   *
   * @param key:  a `String` value identifying this View. It must match the `key` passed earlier to `startView`
   * @param attributes: custom attributes to attach to the View.
   */
  fun stopView(
    key: String,
    attributes: Map<String, String> = emptyMap(),
  )

  /**
   * Notifies that the Resource starts being loaded from given `url`.
   *
   * @param resourceKey: the key representing the Resource - must be unique among all Resources being currently loaded.
   * @param method: type of resource call
   * @param url: the `URL` for the Resource in `String` form.
   * @param attributes: custom attributes to attach to the Resource.
   */
  fun startResourceLoading(
    resourceKey: String,
    method: String,
    url: String,
    attributes: Map<String, String>,
  )

  /**
   * Notifies that the Resource stops being loaded succesfully.
   *
   * @param resourceKey: the key representing the Resource - must match the one used in `startResourceLoading(...)`
   * @param attributes: custom attributes to attach to the Resource.
   */
  fun stopResourceLoading(
    resourceKey: String,
    kind: ResourceType,
    attributes: Map<String, String>,
  )

  /**
   * Notifies that the Resource stops being loaded with an error.
   *
   * @param resourceKey: the key representing the Resource - must match the one used in `startResourceLoading(...)`
   * @param message: a message explaining the error
   * @param source: the source of the error
   * @param cause: the cause of the error
   * @param attributes: custom attributes to attach to the Resource
   */
  fun stopResourceLoadingError(
    resourceKey: String,
    source: ErrorSource,
    cause: Throwable,
    attributes: Map<String, String>,
  )

  /**
   * Notifies that a User Action happened. This is used to track discrete user actions (e.g.: tap).
   *
   * @param type: the action type
   * @param name: the action identifier
   * @param attributes: additional custom attributes
   */
  fun addUserAction(
    type: ActionType,
    name: String,
    attributes: Map<String, String>,
  )

  /**
   * Notifies that an error occurred in the active View.
   *
   * @param message: a message explaining the error
   * @param source: the source of the error
   * @param attributes: additional custom attributes
   */
  fun addError(
    message: String,
    source: ErrorSource,
    attributes: Map<String, String>,
  )
}

/**
 * Describes the source of a RUM Error
 */
enum class ErrorSource {
  /**Error originating in the Network layer */
  Network,

  /** Error originating in the source code (usually a crash) */
  Source,

  /** Error originated in a console */
  Console,

  /** Error extracted from a logged error */
  Logger,

  /** Error originated in an Agent */
  Agent,

  /** Error originated in a WebView */
  WebView,
}

enum class ActionType {
  Tap,
  Scroll,
  Swipe,
  Click,
  Back,
  Custom,
}

enum class ResourceType {
  Other,
}
