package build.wallet.statemachine.walletmigration

import build.wallet.statemachine.core.AppSegment
import build.wallet.statemachine.core.childSegment

object PrivateWalletMigrationAppSegment : AppSegment {
  override val id: String = "PrivateWalletMigration"

  /**
   * Sweep
   */
  object Sweep : AppSegment by PrivateWalletMigrationAppSegment.childSegment("Sweep")
}
