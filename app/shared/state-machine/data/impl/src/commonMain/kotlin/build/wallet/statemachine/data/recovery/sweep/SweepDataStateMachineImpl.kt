package build.wallet.statemachine.data.recovery.sweep

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.bitcoin.blockchain.BitcoinBlockchain
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.transactions.Transaction
import build.wallet.bitcoin.transactions.TransactionRepository
import build.wallet.bitcoin.transactions.toDuration
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.f8e.mobilepay.MobilePaySigningService
import build.wallet.keybox.wallet.AppSpendingWalletProvider
import build.wallet.ktor.result.NetworkingError
import build.wallet.mapUnit
import build.wallet.money.BitcoinMoney
import build.wallet.money.exchange.ExchangeRate
import build.wallet.money.exchange.ExchangeRateSyncer
import build.wallet.recovery.sweep.SweepGenerator
import build.wallet.recovery.sweep.SweepGenerator.SweepGeneratorError
import build.wallet.recovery.sweep.SweepPsbt
import build.wallet.statemachine.data.recovery.sweep.SweepData.AwaitingHardwareSignedSweepsData
import build.wallet.statemachine.data.recovery.sweep.SweepData.GeneratePsbtsFailedData
import build.wallet.statemachine.data.recovery.sweep.SweepData.GeneratingPsbtsData
import build.wallet.statemachine.data.recovery.sweep.SweepData.NoFundsFoundData
import build.wallet.statemachine.data.recovery.sweep.SweepData.PsbtsGeneratedData
import build.wallet.statemachine.data.recovery.sweep.SweepData.SigningAndBroadcastingSweepsData
import build.wallet.statemachine.data.recovery.sweep.SweepData.SweepCompleteData
import build.wallet.statemachine.data.recovery.sweep.SweepData.SweepFailedData
import build.wallet.statemachine.data.recovery.sweep.SweepDataStateMachineImpl.State.AwaitingHardwareSignedSweepsState
import build.wallet.statemachine.data.recovery.sweep.SweepDataStateMachineImpl.State.GeneratePsbtsFailedState
import build.wallet.statemachine.data.recovery.sweep.SweepDataStateMachineImpl.State.GeneratingPsbtsState
import build.wallet.statemachine.data.recovery.sweep.SweepDataStateMachineImpl.State.NoFundsFoundState
import build.wallet.statemachine.data.recovery.sweep.SweepDataStateMachineImpl.State.PsbtsGeneratedState
import build.wallet.statemachine.data.recovery.sweep.SweepDataStateMachineImpl.State.SignAndBroadcastState
import build.wallet.statemachine.data.recovery.sweep.SweepDataStateMachineImpl.State.SweepFailedState
import build.wallet.statemachine.data.recovery.sweep.SweepDataStateMachineImpl.State.SweepSuccessState
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapAll
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

class SweepDataStateMachineImpl(
  private val bitcoinBlockchain: BitcoinBlockchain,
  private val sweepGenerator: SweepGenerator,
  private val mobilePaySigningService: MobilePaySigningService,
  private val appSpendingWalletProvider: AppSpendingWalletProvider,
  private val exchangeRateSyncer: ExchangeRateSyncer,
  private val transactionRepository: TransactionRepository,
) : SweepDataStateMachine {
  private sealed interface State {
    data object GeneratingPsbtsState : State

    /**
     * @property allPsbts - all sweep psbts that need to be signed (with app + hardware/f8e) and
     * broadcasted to move recovered funds.
     */
    data class PsbtsGeneratedState(
      val allPsbts: ImmutableList<SweepPsbt>,
    ) : State {
      init {
        require(allPsbts.isNotEmpty())
      }

      /**
       * Sweep psbts that need to be signed with hardware, if any.
       */
      val needsHwSign: ImmutableMap<SpendingKeyset, Psbt> =
        allPsbts
          .filter { it.signingFactor == Hardware }
          .associate { it.keyset to it.psbt }
          .toImmutableMap()
    }

    /**
     * We didn't find any funds to move, or the amount of funds are lower than the network fees
     * required to move them.
     */
    data object NoFundsFoundState : State

    /**
     * Awaiting for hardware to sign sweep psbts that require hardware signing to be broadcasted.
     */
    data class AwaitingHardwareSignedSweepsState(
      val allPsbts: ImmutableList<SweepPsbt>,
      val needsHwSign: ImmutableMap<SpendingKeyset, Psbt>,
    ) : State

    /**
     * Signing sweep psbts with app + f8e/hardware and broadcasting.
     */
    data class SignAndBroadcastState(
      val allPsbts: ImmutableList<SweepPsbt>,
    ) : State

    /**
     * Failed to generate sweep psbts.
     */
    data class GeneratePsbtsFailedState(
      val error: SweepGeneratorError,
    ) : State

    /**
     * Successfully braodcasted sweep psbts.
     */
    data class SweepSuccessState(
      val factor: PhysicalFactor,
    ) : State

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
          sweepGenerator.generateSweep(props.keybox)
            .onSuccess { psbts ->
              sweepState =
                if (psbts.isEmpty()) {
                  NoFundsFoundState
                } else {
                  PsbtsGeneratedState(psbts.toImmutableList())
                }
            }
            .onFailure { err -> sweepState = GeneratePsbtsFailedState(err) }
        }
        GeneratingPsbtsData(props.recoveredFactor)
      }

      /** Error state: PSBT generation failed */
      is GeneratePsbtsFailedState ->
        GeneratePsbtsFailedData(props.recoveredFactor, state.error) {
          sweepState = GeneratingPsbtsState
        }

      /** Transactions have been generated, waiting on user confirmation */
      is PsbtsGeneratedState ->
        PsbtsGeneratedData(
          recoveredFactor = props.recoveredFactor,
          totalFeeAmount = calculateTotalFee(state.allPsbts),
          startSweep = {
            sweepState =
              if (state.needsHwSign.isEmpty()) {
                // no psbts that need to be signed with hardware, all psbts are signable with app + f8e,
                // ready to broadcast after signing
                SignAndBroadcastState(state.allPsbts)
              } else {
                AwaitingHardwareSignedSweepsState(
                  allPsbts = state.allPsbts,
                  needsHwSign = state.needsHwSign
                )
              }
          }
        )

      is AwaitingHardwareSignedSweepsState ->
        AwaitingHardwareSignedSweepsData(
          recoveredFactor = props.recoveredFactor,
          fullAccountConfig = props.keybox.config,
          needsHwSign = state.needsHwSign,
          addHwSignedSweeps = { hwSignedPsbts ->
            val mergedPsbts = mergeHwSignedPsbts(state.allPsbts, hwSignedPsbts)
            sweepState = SignAndBroadcastState(mergedPsbts.toImmutableList())
          }
        )

      /** Asynchronously signing transactions using app + server, and then broadcasting */
      is SignAndBroadcastState -> {
        LaunchedEffect("sign-and-broadcast") {
          val exchangeRates = exchangeRateSyncer.exchangeRates.value.toImmutableList()
          signAndBroadcastPsbts(props, state, exchangeRates)
            .onSuccess { sweepState = SweepSuccessState(props.recoveredFactor) }
            .onFailure { sweepState = SweepFailedState(it) }
        }
        SigningAndBroadcastingSweepsData(props.recoveredFactor)
      }

      is NoFundsFoundState ->
        NoFundsFoundData(
          recoveredFactor = props.recoveredFactor,
          proceed = props.onSuccess
        )

      /** Terminal state: Sweep succeeded */
      is SweepSuccessState ->
        SweepCompleteData(
          recoveredFactor = props.recoveredFactor,
          proceed = props.onSuccess
        )
      /** Terminal state: Sweep failed */
      is SweepFailedState ->
        SweepFailedData(
          recoveredFactor = props.recoveredFactor,
          cause = state.cause,
          retry = {
            sweepState = GeneratingPsbtsState
          }
        )
    }
  }

  private fun mergeHwSignedPsbts(
    allPsbts: List<SweepPsbt>,
    hwSignedPsbts: List<Psbt>,
  ): List<SweepPsbt> {
    val indexedHwSignedPsbts: Map<String, Psbt> =
      hwSignedPsbts.associateBy { it.id }
    return allPsbts.map { sweep ->
      indexedHwSignedPsbts[sweep.psbt.id]?.let {
        sweep.copy(psbt = it)
      } ?: sweep
    }
  }

  private fun calculateTotalFee(psbts: List<SweepPsbt>): BitcoinMoney {
    return psbts
      .map { it.psbt.fee }
      .reduce { acc, fee -> acc.plus(fee) }
  }

  private suspend fun signAndBroadcastPsbts(
    props: SweepDataProps,
    state: SignAndBroadcastState,
    exchangeRates: ImmutableList<ExchangeRate>,
  ): Result<Unit, Throwable> =
    Ok(state.allPsbts)
      .mapAll { signAndBroadcastPsbt(props, it, exchangeRates) }
      .mapUnit()

  private suspend fun signAndBroadcastPsbt(
    props: SweepDataProps,
    sweepPsbt: SweepPsbt,
    exchangeRates: ImmutableList<ExchangeRate>,
  ): Result<Unit, Throwable> =
    binding {
      val appSignPsbt = appSignPsbt(sweepPsbt).bind()
      val signedPsbt = serverSignPsbt(props, appSignPsbt).bind()
      bitcoinBlockchain.broadcast(signedPsbt)
        .onSuccess {
          // When we successfully broadcast the transaction, store the transaction details and
          // exchange rate.
          transactionRepository.setTransaction(
            Transaction(
              transactionDetail = it,
              exchangeRates = exchangeRates,
              estimatedConfirmationTime =
                it.broadcastTime.plus(
                  EstimatedTransactionPriority.sweepPriority().toDuration()
                )
            )
          )
        }.bind()
    }

  private suspend fun appSignPsbt(sweep: SweepPsbt): Result<SweepPsbt, Throwable> =
    binding {
      if (sweep.signingFactor == App) {
        val wallet = appSpendingWalletProvider.getSpendingWallet(sweep.keyset).bind()

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
    mobilePaySigningService.signWithSpecificKeyset(
      f8eEnvironment = props.keybox.config.f8eEnvironment,
      fullAccountId = props.keybox.fullAccountId,
      keysetId = sweep.keyset.f8eSpendingKeyset.keysetId,
      psbt = sweep.psbt
    )
}
