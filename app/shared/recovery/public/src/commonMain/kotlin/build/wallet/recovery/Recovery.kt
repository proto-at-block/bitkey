package build.wallet.recovery

import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.app.AppRecoveryAuthPublicKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.f8e.recovery.ServerRecovery

/**
 * Successful result type of a sync. It describes distinct customer-focused use-cases about
 * the state of recovery as viewed from the local device.
 */
sealed interface Recovery {
  /**
   * Indicates that we're currently loading the data required to determine the active recovery
   */
  data object Loading : Recovery

  /**
   * Indicates that we're attempting to recover and the server still recognizes our attempt
   * as the active one.
   */
  sealed interface StillRecovering : Recovery {
    /**
     * F8e account that was used to initiate recovery with.
     */
    val fullAccountId: FullAccountId

    /**
     * Customer's physical factor that is being recovered.
     */
    val factorToRecover: PhysicalFactor

    val appSpendingKey: AppSpendingPublicKey
    val appGlobalAuthKey: AppGlobalAuthPublicKey
    val appRecoveryAuthKey: AppRecoveryAuthPublicKey?
    val hardwareSpendingKey: HwSpendingPublicKey
    val hardwareAuthKey: HwAuthPublicKey

    /**
     * A local recovery that has yet to complete on the server so its success depends
     * on server cooperation. The server could stop cooperating at any point meaning the
     * local attempt will fail.
     */
    sealed interface ServerDependentRecovery : StillRecovering {
      /**
       * Given that a [ServerDependentRecovery] will not move forward without an accompanying
       * [ServerRecovery], we store the [ServerRecovery] alongside it. This allows the server
       * to be the source of truth for parts of the recovery such as delay start and end time.
       */
      val serverRecovery: ServerRecovery

      /**
       * Indicates that we have successfully initiated recovery.
       */
      data class InitiatedRecovery(
        override val fullAccountId: FullAccountId,
        override val appSpendingKey: AppSpendingPublicKey,
        override val appGlobalAuthKey: AppGlobalAuthPublicKey,
        override val appRecoveryAuthKey: AppRecoveryAuthPublicKey?,
        override val hardwareSpendingKey: HwSpendingPublicKey,
        override val hardwareAuthKey: HwAuthPublicKey,
        override val factorToRecover: PhysicalFactor,
        override val serverRecovery: ServerRecovery,
      ) : ServerDependentRecovery
    }

    /**
     * A local recovery that has completed on the server so its success no longer depends
     * on server cooperation.
     */
    sealed interface ServerIndependentRecovery : StillRecovering {
      /**
       * Indicates that we had initiated server recovery, but the server recovery object no
       * longer exists. This could be that the recovery was canceled, or that we had a race
       * or app crash between completing the recovery and saving the completion state locally.
       */
      data class MaybeNoLongerRecovering(
        override val fullAccountId: FullAccountId,
        override val appSpendingKey: AppSpendingPublicKey,
        override val appGlobalAuthKey: AppGlobalAuthPublicKey,
        override val appRecoveryAuthKey: AppRecoveryAuthPublicKey?,
        override val hardwareSpendingKey: HwSpendingPublicKey,
        override val hardwareAuthKey: HwAuthPublicKey,
        override val factorToRecover: PhysicalFactor,
        val sealedCsek: SealedCsek,
      ) : ServerIndependentRecovery

      /**
       * Indicates that we have successfully rotated authentication keys with hardware.
       */
      data class RotatedAuthKeys(
        override val fullAccountId: FullAccountId,
        override val appSpendingKey: AppSpendingPublicKey,
        override val appGlobalAuthKey: AppGlobalAuthPublicKey,
        override val appRecoveryAuthKey: AppRecoveryAuthPublicKey?,
        override val hardwareSpendingKey: HwSpendingPublicKey,
        override val hardwareAuthKey: HwAuthPublicKey,
        override val factorToRecover: PhysicalFactor,
        val sealedCsek: SealedCsek,
      ) : ServerIndependentRecovery

      /**
       * Indicates that we have successfully created new spending keys with f8e.
       */
      data class CreatedSpendingKeys(
        val f8eSpendingKeyset: F8eSpendingKeyset,
        override val fullAccountId: FullAccountId,
        override val appSpendingKey: AppSpendingPublicKey,
        override val appGlobalAuthKey: AppGlobalAuthPublicKey,
        override val appRecoveryAuthKey: AppRecoveryAuthPublicKey?,
        override val hardwareSpendingKey: HwSpendingPublicKey,
        override val hardwareAuthKey: HwAuthPublicKey,
        override val factorToRecover: PhysicalFactor,
        val sealedCsek: SealedCsek,
      ) : ServerIndependentRecovery

      /**
       * Indicates that we have successfully backed up the keys to the cloud.
       */
      data class BackedUpToCloud(
        val f8eSpendingKeyset: F8eSpendingKeyset,
        override val fullAccountId: FullAccountId,
        override val appSpendingKey: AppSpendingPublicKey,
        override val appGlobalAuthKey: AppGlobalAuthPublicKey,
        override val appRecoveryAuthKey: AppRecoveryAuthPublicKey?,
        override val hardwareSpendingKey: HwSpendingPublicKey,
        override val hardwareAuthKey: HwAuthPublicKey,
        override val factorToRecover: PhysicalFactor,
      ) : ServerIndependentRecovery
    }
  }

  /**
   * Indicates that we were attempting to recover but the server no longer recognizes ours
   * as the active attempt. It could mean that it's been replaced by another or that ours
   * was simply canceled.
   */
  data class NoLongerRecovering(
    /**
     The lost factor of the server recovery that canceled the local recovery.
     */
    val cancelingRecoveryLostFactor: PhysicalFactor,
  ) : Recovery

  /**
   * Indicates there is no active recovery on the server and our device was not attempting one.
   * This is the normal state of the world when not recovering.
   */
  data object NoActiveRecovery : Recovery

  /**
   * Indicates that someone else is trying to recover the account we're signed into.
   */
  data class SomeoneElseIsRecovering(
    /**
     The lost factor of the server recovery attempt.
     */
    val cancelingRecoveryLostFactor: PhysicalFactor,
  ) : Recovery
}
