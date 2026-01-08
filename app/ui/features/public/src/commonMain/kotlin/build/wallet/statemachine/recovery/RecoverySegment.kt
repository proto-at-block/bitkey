package build.wallet.statemachine.recovery

import build.wallet.statemachine.core.AppSegment
import build.wallet.statemachine.core.childSegment

/**
 * All Recovery segments.
 */
object RecoverySegment : AppSegment {
  override val id: String = "Recovery"

  /**
   * Recovery via cloud backup flows
   */
  object CloudBackup : AppSegment by RecoverySegment.childSegment("Cloud") {
    /**
     * Cloud backup actions involving a full account
     */
    object FullAccount : AppSegment by CloudBackup.childSegment("FullAccountBackup") {
      /**
       * Sign in to a cloud provider
       */
      object SignIn : AppSegment by FullAccount.childSegment("SignIn")

      /**
       * Creation of a full account cloud backup
       */
      object Creation : AppSegment by FullAccount.childSegment("Creation")

      /**
       * Uploading a full account cloud backup
       */
      object Upload : AppSegment by FullAccount.childSegment("Upload")

      /**
       * Restoration from a full account cloud backup
       */
      object Restoration : AppSegment by FullAccount.childSegment("Restoration")
    }

    /**
     * Cloud backup actions involving a lite account
     */
    object LiteAccount : AppSegment by CloudBackup.childSegment("LiteAccountBackup") {
      /**
       * Creation of a lite account cloud backup
       */
      object Creation : AppSegment by FullAccount.childSegment("Creation")

      /**
       * Uploading a lite account cloud backup
       */
      object Upload : AppSegment by FullAccount.childSegment("Upload")

      /**
       * Restoration from a lite account cloud backup
       */
      object Restoration : AppSegment by FullAccount.childSegment("Restoration")
    }
  }

  /**
   * Recovery via Emergency Exit Kit flows
   */
  object EmergencyExit : AppSegment by RecoverySegment.childSegment("EmergencyExit") {
    /**
     * Creation of an Emergency Exit Kit
     */
    object Creation : AppSegment by EmergencyExit.childSegment("Creation")

    /**
     * Uploading an Emergency Exit Kit
     */
    object Upload : AppSegment by EmergencyExit.childSegment("Upload")
  }

  /**
   * Social Recovery flows
   */
  object SocRec : AppSegment by RecoverySegment.childSegment("SocRec") {
    /**
     * Social recovery on the protected-customer side.
     */
    object ProtectedCustomer : AppSegment by SocRec.childSegment("ProtectedCustomer") {
      /**
       * Invitation creation and acceptance.
       */
      object Setup : AppSegment by ProtectedCustomer.childSegment("Setup")

      /**
       * The actual recovery process for SocRec.
       */
      object Restoration : AppSegment by ProtectedCustomer.childSegment("Restoration")
    }

    object TrustedContact : AppSegment by SocRec.childSegment("TrustedContact") {
      /**
       * Invitation creation and acceptance.
       */
      object Setup : AppSegment by TrustedContact.childSegment("Setup")
    }
  }

  /**
   * Recovery flows with Delay & Notify
   */
  object DelayAndNotify : AppSegment by RecoverySegment.childSegment("DelayAndNotify") {
    /**
     * Recovery flows for lost hardware.
     */
    object LostHardware : AppSegment by DelayAndNotify.childSegment("LostHardware") {
      /**
       * Initiation of DN recovery.
       */
      object Initiation : AppSegment by LostHardware.childSegment("Initiation")

      /**
       * Cancellation of existing DN recovery.
       */
      object Cancellation : AppSegment by LostHardware.childSegment("Cancellation")

      /**
       * Sweep funds for existing DN recovery.
       */
      object Sweep : AppSegment by LostHardware.childSegment("Sweep")

      /**
       * Completion of existing DN recovery.
       */
      object Completion : AppSegment by LostHardware.childSegment("Completion")
    }

    object LostApp : AppSegment by DelayAndNotify.childSegment("LostApp") {
      /**
       * Cancellation of existing DN recovery.
       */
      object Initiation : AppSegment by LostApp.childSegment("Initiation")

      /**
       * Cancellation of existing DN recovery.
       */
      object Cancellation : AppSegment by LostApp.childSegment("Cancellation")

      /**
       * Sweep funds for existing DN recovery.
       */
      object Sweep : AppSegment by LostApp.childSegment("Sweep")

      /**
       * Completion of existing DN recovery.
       */
      object Completion : AppSegment by LostApp.childSegment("Completion")
    }
  }

  /**
   * Perform an additional recovery to transfer funds on an old address
   * after a recovery has already been fully completed.
   */
  object AdditionalSweep : AppSegment by RecoverySegment.childSegment("AdditionalSweep") {
    /**
     * Sweep funds to active wallet.
     */
    object Sweep : AppSegment by AdditionalSweep.childSegment("Sweep")
  }

  /**
   * Keyset repair flows for fixing keyset mismatches after stale cloud backup recovery.
   */
  object KeysetRepair : AppSegment by RecoverySegment.childSegment("KeysetRepair") {
    /**
     * The repair process itself.
     */
    object Repair : AppSegment by KeysetRepair.childSegment("Repair")
  }
}
