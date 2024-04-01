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
   * Recovery via emergency access flows
   */
  object EmergencyAccess : AppSegment by RecoverySegment.childSegment("EmergencyAccess") {
    /**
     * Creation of an emergency access kit
     */
    object Creation : AppSegment by EmergencyAccess.childSegment("Creation")

    /**
     * Uploading an emergency access kit
     */
    object Upload : AppSegment by EmergencyAccess.childSegment("Upload")
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
       * Completion of existing DN recovery.
       */
      object Completion : AppSegment by LostApp.childSegment("Completion")
    }
  }
}
