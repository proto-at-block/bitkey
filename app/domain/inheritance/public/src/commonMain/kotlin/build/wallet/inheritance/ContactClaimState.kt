package build.wallet.inheritance

import androidx.compose.runtime.Stable
import build.wallet.bitkey.inheritance.InheritanceClaim
import build.wallet.bitkey.inheritance.isActive
import build.wallet.bitkey.inheritance.isApproved
import build.wallet.bitkey.inheritance.isCancelable
import build.wallet.bitkey.inheritance.isCompleted
import build.wallet.bitkey.relationships.ProtectedCustomer
import build.wallet.bitkey.relationships.RecoveryEntity
import build.wallet.bitkey.relationships.TrustedContact
import kotlinx.datetime.Instant

/**
 * Snapshot of joined data between a relationship and all of its claims.
 */
@Stable
sealed interface ContactClaimState {
  /**
   * The benefactor/beneficiary for whom this state represents.
   */
  val relationship: RecoveryEntity

  /**
   * The claims associated with this relationship.
   */
  val claims: List<InheritanceClaim>

  /**
   * The timestamp that this data was created.
   *
   * This allows for calculating the pending claim's complete state without
   * observers like the UI constantly re-rendering. To update the state,
   * a new snapshot can be emitted.
   */
  val timestamp: Instant

  /**
   * Snapshot for the state of of a user's beneficiary.
   */
  @Stable
  data class Beneficiary(
    override val timestamp: Instant,
    override val relationship: TrustedContact,
    override val claims: List<InheritanceClaim>,
    val isInvite: Boolean,
  ) : ContactClaimState

  /**
   * Snapshot for the state of a user's benefactor.
   */
  @Stable
  data class Benefactor(
    override val timestamp: Instant,
    override val relationship: ProtectedCustomer,
    override val claims: List<InheritanceClaim>,
  ) : ContactClaimState
}

/**
 * Whether the contact has any claims that are currently in an active state.
 */
val ContactClaimState.hasActiveClaim get() = claims.any { it.isActive }

/**
 * Whether the contact has any claims that are currently in a state that
 * can be canceled.
 *
 * Note: This is different than [isActive], as not all active states are
 * cancelable (e.g. locked claims)
 */
val ContactClaimState.hasCancelableClaim get() = claims.any { it.isCancelable }

/**
 * Whether the contact has any claims that are currently in a state that
 * can be completed by the beneficiary.
 */
val ContactClaimState.hasApprovedClaim get() = claims.any { it.isApproved(timestamp) }

/**
 * Whether the contact has any claims that have terminated successfully.
 */
val ContactClaimState.hasCompletedClaim get() = claims.any { it.isCompleted }
