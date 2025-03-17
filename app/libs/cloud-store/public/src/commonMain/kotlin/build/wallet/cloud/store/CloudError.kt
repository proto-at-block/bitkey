package build.wallet.cloud.store

/**
 * Describes a failure that can occur when reading and writing from a cloud store.
 */
open class CloudError(
  open val rectificationData: Any? = null,
) : Error()
