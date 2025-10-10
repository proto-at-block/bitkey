package bitkey.data

/**
 * Designates data that is personally identifiable information (PII).
 *
 * Data marked with this annotation MUST NOT be logged.
 *
 * This data is not necessarily secret, and may be shared with Bitkey's
 * servers only as necessary. (ex. Contact phone number)
 *
 * If the information must remain secret to the user, use [PrivateData] instead.
 */
@RequiresOptIn(message = "This data is personally identifiable information (PII) and must not be logged.")
@Retention(AnnotationRetention.BINARY)
@Target(
  AnnotationTarget.FIELD,
  AnnotationTarget.CLASS,
  AnnotationTarget.PROPERTY,
  AnnotationTarget.FUNCTION,
)
annotation class Pii
