package build.wallet.worker

/**
 * Defines a particular domain segment's refresh operation.
 *
 * This class functions as a tag/identifier for refresh jobs. No parameters
 * are needed, simply use as a static global tag to identify workers.
 *
 * ex:
 *
 *     val TransactionActivityOperations = RefreshOperation()
 *
 * Executing refreshes by specifying the instance:
 *
 *     refreshExecutor.runRefreshOperation(TransactionActivityOperations)
 */
class RefreshOperation(val name: String)
