package build.wallet.statemachine.data.recovery.sweep

import androidx.compose.runtime.*
import build.wallet.bitcoin.transactions.BitcoinWalletService
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.Companion.sweepPriority
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.mobilepay.MobilePaySigningF8eClient
import build.wallet.keybox.wallet.AppSpendingWalletProvider
import build.wallet.logging.logFailure
import build.wallet.mapUnit
import build.wallet.recovery.sweep.Sweep
import build.wallet.recovery.sweep.SweepContext
import build.wallet.recovery.sweep.SweepPsbt
import build.wallet.recovery.sweep.SweepService
import build.wallet.statemachine.data.recovery.sweep.SweepData.*
import build.wallet.statemachine.data.recovery.sweep.SweepDataStateMachineImpl.State.*
import build.wallet.time.MinimumLoadingDuration
import build.wallet.time.withMinimumDelay
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding

@BitkeyInject(AppScope::class)
class SweepDataStateMachineImpl(
  private val sweepService: SweepService,
  private val mobilePaySigningF8eClient: MobilePaySigningF8eClient,
  private val appSpendingWalletProvider: AppSpendingWalletProvider,
  private val bitcoinWalletService: BitcoinWalletService,
  private val minimumLoadingDuration: MinimumLoadingDuration,
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
     * Awaiting hardware to sign sweep PSBTs that require hardware signing to be broadcasted.
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
      val sweep: Sweep,
    ) : State

    /**
     * Failed to generate sweep psbts.
     */
    data class GeneratePsbtsFailedState(
      val error: Error,
    ) : State

    /**
     * Successfully broadcast sweep psbts.
     */
    data class SweepSuccessState(
      val sweep: Sweep,
    ) : State

    data object SweepSuccessNoDataState : State

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
          withMinimumDelay(minimumLoadingDuration.value) {
            sweepService.prepareSweep(props.keybox)
          }
            .onSuccess { sweep ->
              when (sweep) {
                // If SweepService determines we have no funds remaining to sweep,
                // either we have already successfully swept funds last session (in which case,
                // hasAttemptedSweep will be true), or our previous wallet had no funds to begin
                // with (hasAttemptedSweep == false).
                null -> {
                  sweepService.markSweepHandled()
                  if (props.hasAttemptedSweep) {
                    sweepState = SweepSuccessNoDataState
                  } else {
                    // We don't show the NoFundsFoundScreen during the migration
                    if (props.sweepContext is SweepContext.PrivateWalletMigration) {
                      props.onSuccess()
                    } else {
                      sweepState = NoFundsFoundState
                    }
                  }
                }
                else -> sweepState = PsbtsGeneratedState(sweep)
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
          destinationAddress = state.sweep.destinationAddress,
          startSweep = {
            sweepState =
              if (state.sweep.psbtsRequiringHwSign.isEmpty()) {
                // no psbts that need to be signed with hardware, all psbts are signable with app + f8e,
                // ready to broadcast after signing
                SignAndBroadcastState(state.sweep.unsignedPsbts, state.sweep)
              } else {
                AwaitingHardwareSignedSweepsState(sweep = state.sweep)
              }
          }
        )

      is AwaitingHardwareSignedSweepsState ->
        AwaitingHardwareSignedSweepsData(
          needsHwSign = state.sweep.psbtsRequiringHwSign,
          addHwSignedSweeps = { hwSignedPsbts ->
            val mergedPsbts = mergeHwSignedPsbts(state.sweep.unsignedPsbts, hwSignedPsbts)
            sweepState = SignAndBroadcastState(mergedPsbts, state.sweep)
          },
          cancelHwSign = {
            // Go back to PsbtsGeneratedState to allow the user to retry
            sweepState = PsbtsGeneratedState(state.sweep)
          }
        )

      /** Asynchronously signing transactions using app + server, and then broadcasting */
      is SignAndBroadcastState -> {
        LaunchedEffect("sign-and-broadcast") {
          props.onAttemptSweep()
          signAndBroadcastPsbts(props, state)
            .onSuccess {
              sweepService.markSweepHandled()
              sweepState = SweepSuccessState(state.sweep)
            }
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
          totalFeeAmount = state.sweep.totalFeeAmount,
          totalTransferAmount = state.sweep.totalTransferAmount,
          destinationAddress = state.sweep.destinationAddress,
          proceed = props.onSuccess
        )

      is SweepSuccessNoDataState ->
        SweepCompleteNoData(props.onSuccess)

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
      // Apply app signature if required
      val appSignedPsbt = if (sweepPsbt.signaturePlan.requiresAppSignature) {
        val wallet = appSpendingWalletProvider.getSpendingWallet(sweepPsbt.sourceKeyset)
          .logFailure { "Failed to get spending wallet for source keyset ${sweepPsbt.sourceKeyset.localId}" }
          .bind()

        wallet.signPsbt(sweepPsbt.psbt)
          .logFailure { "Failed to add app signature to PSBT" }
          .bind()
      } else {
        sweepPsbt.psbt
      }

      // Apply server signature if required
      val fullySignedPsbt = if (sweepPsbt.signaturePlan.requiresServerSignature) {
        mobilePaySigningF8eClient.signWithSpecificKeyset(
          f8eEnvironment = props.keybox.config.f8eEnvironment,
          fullAccountId = props.keybox.fullAccountId,
          keysetId = sweepPsbt.sourceKeyset.f8eSpendingKeyset.keysetId,
          psbt = appSignedPsbt
        )
          .logFailure { "Failed to get server signature" }
          .bind()
      } else {
        appSignedPsbt
      }

      // Broadcast the fully signed PSBT
      bitcoinWalletService
        .broadcast(
          psbt = fullySignedPsbt,
          estimatedTransactionPriority = sweepPriority()
        )
        .logFailure { "Error broadcasting sweep transaction." }
        .bind()
    }
}
