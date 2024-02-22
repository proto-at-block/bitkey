package build.wallet.statemachine.data.lightning

/**
 * Describes data of the Lightning (LDK) node. Currently app-scoped but in the future might need to
 * be scoped to active Keybox.
 */
sealed interface LightningNodeData {
  /**
   * Indicates that Lightning node is disabled and is not running.
   */
  data object LightningNodeDisabledData : LightningNodeData

  /**
   * Indicates that Lightning node is enabled and is running.
   */
  data object LightningNodeRunningData : LightningNodeData
}
