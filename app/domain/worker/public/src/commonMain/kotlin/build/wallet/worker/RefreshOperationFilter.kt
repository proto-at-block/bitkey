package build.wallet.worker

/**
 * Strategy for selecting a group of refresh categories.
 */
sealed interface RefreshOperationFilter {
  /**
   * Refresh worker during all screens' refresh operations.
   */
  data object Any : RefreshOperationFilter

  /**
   * Only refresh for the specified screen categories.
   */
  data class Subset(val operations: Set<RefreshOperation>) : RefreshOperationFilter {
    constructor(vararg tags: RefreshOperation) : this(tags.toSet())
  }
}
