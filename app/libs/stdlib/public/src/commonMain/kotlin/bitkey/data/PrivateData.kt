package bitkey.data

/**
 * Designates data that is highly sensitive.
 *
 * Data marked with this annotation MUST NOT be logged and remain local
 * to the device. This data cannot be sent to the server, unless encrypted
 * with a key that is only known to the user.
 */
@RequiresOptIn(message = "This data is highly sensitive and MUST not be logged and remain local to the device.")
@Retention(AnnotationRetention.BINARY)
@Target(
  AnnotationTarget.FIELD,
  AnnotationTarget.CLASS,
  AnnotationTarget.PROPERTY,
  AnnotationTarget.FUNCTION
)
annotation class PrivateData
