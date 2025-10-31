package build.wallet.statemachine.data.recovery.sweep

import build.wallet.bitcoin.transactions.Psbt
import build.wallet.money.BitcoinMoney
import build.wallet.recovery.sweep.SweepPsbt

sealed interface SweepData {
  /** Initial state */
  data object GeneratingPsbtsData : SweepData

  /** Error state */
  data class GeneratePsbtsFailedData(
    val error: Error,
    val retry: () -> Unit,
  ) : SweepData

  /**
   * We didn't find any funds to move, or the amount of funds are lower than the network fees
   * required to move them.
   *
   * @property proceed moves state machine forward by activating
   */
  data class NoFundsFoundData(
    val proceed: () -> Unit,
  ) : SweepData

  /**
   * Sweep psbts generated and ready for signing and broadcasting.
   *
   * @property recoveredFactor the factor that was recovered prior to hitting the sweep experience
   * @property totalFeeAmount total fee amount that needs to be paid to blockchain to broadcast
   * all sweep psbts.
   * @property destinationAddress the Bitcoin address where funds are being swept to
   * @property startSweep confirm total fee amount and initiate sweep process. If there are sweep
   * psbts that require hardware signing, should move to [AwaitingHardwareSignedSweepsData],
   * otherwise straight to [SigningAndBroadcastingSweepsData].
   */
  data class PsbtsGeneratedData(
    val totalFeeAmount: BitcoinMoney,
    val totalTransferAmount: BitcoinMoney,
    val destinationAddress: String,
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
    val needsHwSign: Set<SweepPsbt>,
    val addHwSignedSweeps: (Set<Psbt>) -> Unit,
  ) : SweepData

  /**
   * Signing sweep psbts with app + f8e and broadcasting sweeps. Should move to [SweepCompleteData]
   * on success.
   */
  data object SigningAndBroadcastingSweepsData : SweepData

  /**
   * Sweep succeeded.
   *
   * @property proceed moves state machine forward by activating
   */
  data class SweepCompleteData(
    val proceed: () -> Unit,
    val totalFeeAmount: BitcoinMoney,
    val totalTransferAmount: BitcoinMoney,
    val destinationAddress: String,
  ) : SweepData

  /**
   * We have already successfully swept funds last session
   */
  data class SweepCompleteNoData(
    val proceed: () -> Unit,
  ) : SweepData

  /** Sweep failed */
  data class SweepFailedData(
    val cause: Throwable,
    val retry: () -> Unit,
  ) : SweepData
}
