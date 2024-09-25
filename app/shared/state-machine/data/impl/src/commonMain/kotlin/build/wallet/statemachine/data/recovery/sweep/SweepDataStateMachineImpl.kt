@file:OptIn(ExperimentalObjCRefinement::class)

package build.wallet.statemachine.data.recovery.sweep

import androidx.compose.runtime.*
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.Companion.sweepPriority
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.transactions.TransactionsService
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.f8e.mobilepay.MobilePaySigningF8eClient
import build.wallet.keybox.wallet.AppSpendingWalletProvider
import build.wallet.ktor.result.NetworkingError
import build.wallet.logging.logFailure
import build.wallet.mapUnit
import build.wallet.recovery.sweep.Sweep
import build.wallet.recovery.sweep.SweepPsbt
import build.wallet.recovery.sweep.SweepService
import build.wallet.statemachine.data.recovery.sweep.SweepData.*
import build.wallet.statemachine.data.recovery.sweep.SweepDataStateMachineImpl.State.*
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
class SweepDataStateMachineImpl(
  private val sweepService: SweepService,
  private val mobilePaySigningF8eClient: MobilePaySigningF8eClient,
  private val appSpendingWalletProvider: AppSpendingWalletProvider,
  private val transactionsService: TransactionsService,
) : SweepDataStateMachine {
  private sealed interface State {
    data object GeneratingPsbtsState : State

    data class PsbtsGeneratedState(
      val sweep: Sweep,
    ) : State

    /**
     * We didn't find any funds to move, or the amount of funds are lower than the network fees
     * required to move them.
     */
    data object NoFundsFoundState : State

    /**
     * Awaiting for hardware to sign sweep psbts that require hardware signing to be broadcasted.
     */
    data class AwaitingHardwareSignedSweepsState(
      val sweep: Sweep,
    ) : State {
      init {
        val hasPsbtsRequiringHwSign = sweep.psbtsRequiringHwSign.isNotEmpty()
        require(hasPsbtsRequiringHwSign) {
          "needsHwSign must not be empty"
        }
      }
    }

    /**
     * Signing sweep psbts with app + f8e/hardware and broadcasting.
     */
    data class SignAndBroadcastState(
      val allPsbts: Set<SweepPsbt>,
    ) : State

    /**
     * Failed to generate sweep psbts.
     */
    data class GeneratePsbtsFailedState(
      val error: Error,
    ) : State

    /**
     * Successfully braodcasted sweep psbts.
     */
    data object SweepSuccessState : State

    /**
     * Failed to sign or broadcast sweep psbts.
     */
    data class SweepFailedState(
      val cause: Throwable,
    ) : State
  }

  @Composable
  override fun model(props: SweepDataProps): SweepData {
    var sweepState: State by remember { mutableStateOf(GeneratingPsbtsState) }

    return when (val state = sweepState) {
      /** Initial state */
      GeneratingPsbtsState -> {
        LaunchedEffect("generate-psbts") {
          sweepService.prepareSweep(props.keybox)
            .onSuccess { sweep ->
              sweepState = when (sweep) {
                null -> NoFundsFoundState
                else -> PsbtsGeneratedState(sweep)
              }
            }
            .onFailure { err -> sweepState = GeneratePsbtsFailedState(err) }
        }
        GeneratingPsbtsData
      }

      /** Error state: PSBT generation failed */
      is GeneratePsbtsFailedState ->
        GeneratePsbtsFailedData(state.error) {
          sweepState = GeneratingPsbtsState
        }

      /** Transactions have been generated, waiting on user confirmation */
      is PsbtsGeneratedState ->
        PsbtsGeneratedData(
          totalFeeAmount = state.sweep.totalFeeAmount,
          totalTransferAmount = state.sweep.totalTransferAmount,
          startSweep = {
            sweepState =
              if (state.sweep.psbtsRequiringHwSign.isEmpty()) {
                // no psbts that need to be signed with hardware, all psbts are signable with app + f8e,
                // ready to broadcast after signing
                SignAndBroadcastState(state.sweep.unsignedPsbts)
              } else {
                AwaitingHardwareSignedSweepsState(sweep = state.sweep)
              }
          }
        )

      is AwaitingHardwareSignedSweepsState ->
        AwaitingHardwareSignedSweepsData(
          fullAccountConfig = props.keybox.config,
          needsHwSign = state.sweep.psbtsRequiringHwSign,
          addHwSignedSweeps = { hwSignedPsbts ->
            val mergedPsbts = mergeHwSignedPsbts(state.sweep.unsignedPsbts, hwSignedPsbts)
            sweepState = SignAndBroadcastState(mergedPsbts)
          }
        )

      /** Asynchronously signing transactions using app + server, and then broadcasting */
      is SignAndBroadcastState -> {
        LaunchedEffect("sign-and-broadcast") {
          signAndBroadcastPsbts(props, state)
            .onSuccess { sweepState = SweepSuccessState }
            .onFailure { sweepState = SweepFailedState(it) }
        }
        SigningAndBroadcastingSweepsData
      }

      is NoFundsFoundState ->
        NoFundsFoundData(
          proceed = props.onSuccess
        )

      /** Terminal state: Sweep succeeded */
      is SweepSuccessState ->
        SweepCompleteData(
          proceed = props.onSuccess
        )
      /** Terminal state: Sweep failed */
      is SweepFailedState ->
        SweepFailedData(
          cause = state.cause,
          retry = {
            sweepState = GeneratingPsbtsState
          }
        )
    }
  }

  private fun mergeHwSignedPsbts(
    allPsbts: Set<SweepPsbt>,
    hwSignedPsbts: Set<Psbt>,
  ): Set<SweepPsbt> {
    val indexedHwSignedPsbts: Map<String, Psbt> = hwSignedPsbts.associateBy { it.id }
    return allPsbts
      .map { sweep ->
        indexedHwSignedPsbts[sweep.psbt.id]
          ?.let { sweep.copy(psbt = it) } ?: sweep
      }
      .toSet()
  }

  private suspend fun signAndBroadcastPsbts(
    props: SweepDataProps,
    state: SignAndBroadcastState,
  ): Result<Unit, Throwable> =
    Ok(state.allPsbts)
      .mapAll { signAndBroadcastPsbt(props, it) }
      .mapUnit()

  private suspend fun signAndBroadcastPsbt(
    props: SweepDataProps,
    sweepPsbt: SweepPsbt,
  ): Result<Unit, Throwable> =
    coroutineBinding {
      val appSignPsbt = appSignPsbt(sweepPsbt).bind()
      val signedPsbt = serverSignPsbt(props, appSignPsbt).bind()
      transactionsService
        .broadcast(
          psbt = signedPsbt,
          estimatedTransactionPriority = sweepPriority()
        )
        .logFailure { "Error broadcasting sweep transaction." }
        .bind()
    }

  private suspend fun appSignPsbt(sweep: SweepPsbt): Result<SweepPsbt, Throwable> =
    coroutineBinding {
      if (sweep.signingFactor == App) {
        val wallet = appSpendingWalletProvider.getSpendingWallet(sweep.sourceKeyset).bind()

        wallet.signPsbt(sweep.psbt)
          .map { appSignedPsbt -> sweep.copy(psbt = appSignedPsbt) }
          .bind()
      } else {
        sweep
      }
    }

  private suspend fun serverSignPsbt(
    props: SweepDataProps,
    sweep: SweepPsbt,
  ): Result<Psbt, NetworkingError> =
    mobilePaySigningF8eClient.signWithSpecificKeyset(
      f8eEnvironment = props.keybox.config.f8eEnvironment,
      fullAccountId = props.keybox.fullAccountId,
      keysetId = sweep.sourceKeyset.f8eSpendingKeyset.keysetId,
      psbt = sweep.psbt
    )
}
