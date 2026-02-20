package build.wallet.cloud.store

/**
 * Describes a failure when reading or writing via CloudKit.
 *
 * The [message] comes from the underlying [NSError] when available. If CloudKit doesn't
 * provide a user-facing description, we fall back to a domain/code-based message so logs
 * still carry actionable context. The raw [NSError] is stored in [rectificationData] for
 * callers that want to inspect CloudKit-specific details.
 */
data class CloudKitKeyValueStoreError(
  override val message: String,
  override val cause: Throwable? = null,
  override val rectificationData: Any? = null,
) : CloudError(rectificationData)
