package build.wallet.statemachine.data.recovery.sweep

import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.money.BitcoinMoney
import build.wallet.recovery.sweep.SweepGenerator.SweepGeneratorError
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap

sealed interface SweepData {
  /** Initial state */
  data class GeneratingPsbtsData(
    val recoveredFactor: PhysicalFactor,
  ) : SweepData

  /** Error state */
  data class GeneratePsbtsFailedData(
    val recoveredFactor: PhysicalFactor,
    val error: SweepGeneratorError,
    val retry: () -> Unit,
  ) : SweepData

  /**
   * We didn't find any funds to move, or the amount of funds are lower than the network fees
   * required to move them.
   *
   * @property proceed moves state machine forward by activating
   */
  data class NoFundsFoundData(
    val recoveredFactor: PhysicalFactor,
    val proceed: () -> Unit,
  ) : SweepData

  /**
   * Sweep psbts generated and ready for signing and broadcasting.
   *
   * @property recoveredFactor the factor that was recovered prior to hitting the sweep experience
   * @property totalFeeAmount total fee amount that needs to be paid to blockchain to broadcast
   * all sweep psbts.
   * @property startSweep confirm total fee amount and initiate sweep process. If there are sweep
   * psbts that require hardware signing, should move to [AwaitingHardwareSignedSweepsData],
   * otherwise straight to [SigningAndBroadcastingSweepsData].
   */
  data class PsbtsGeneratedData(
    val recoveredFactor: PhysicalFactor,
    val totalFeeAmount: BitcoinMoney,
    val startSweep: () -> Unit,
  ) : SweepData

  /**
   * Awaiting hardware to sign sweep psbts - [needsHwSign].
   *
   * @property needsHwSign sweep psbts that need to be signed with hardware.
   * @property addHwSignedSweeps - accept hardware signed sweep psbts. Should move
   * to [SigningAndBroadcastingSweepsData].
   */
  data class AwaitingHardwareSignedSweepsData(
    val recoveredFactor: PhysicalFactor,
    val fullAccountConfig: FullAccountConfig,
    val needsHwSign: ImmutableMap<SpendingKeyset, Psbt>,
    val addHwSignedSweeps: (ImmutableList<Psbt>) -> Unit,
  ) : SweepData

  /**
   * Signing sweep psbts with app + f8e and broadcasting sweeps. Should move to [SweepCompleteData]
   * on success.
   */
  data class SigningAndBroadcastingSweepsData(
    val recoveredFactor: PhysicalFactor,
  ) : SweepData

  /**
   * Sweep succeeded.
   *
   * @property proceed moves state machine forward by activating
   */
  data class SweepCompleteData(
    val recoveredFactor: PhysicalFactor,
    val proceed: () -> Unit,
  ) : SweepData

  /** Sweep failed */
  data class SweepFailedData(
    val recoveredFactor: PhysicalFactor,
    val cause: Throwable,
    val retry: () -> Unit,
  ) : SweepData
}
