package build.wallet.statemachine.data.sweep

import build.wallet.bdk.bindings.BdkError
import build.wallet.bdk.bindings.BdkError.Generic
import build.wallet.bitcoin.blockchain.BitcoinBlockchainMock
import build.wallet.bitcoin.transactions.OutgoingTransactionDetailRepositoryMock
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.bitcoin.wallet.SpendingWalletMock
import build.wallet.bitcoin.wallet.isAppSignedWithKeyset
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.bitkey.spending.SpendingKeysetMock2
import build.wallet.compose.collections.immutableListOf
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.mobilepay.MobilePaySigningF8eClientMock
import build.wallet.f8e.mobilepay.isServerSignedWithKeyset
import build.wallet.keybox.wallet.AppSpendingWalletProvider
import build.wallet.ktor.result.HttpError.NetworkError
import build.wallet.ktor.result.NetworkingError
import build.wallet.money.exchange.ExchangeRateSyncerMock
import build.wallet.recovery.sweep.SweepGenerator.SweepGeneratorError.BdkFailedToCreatePsbt
import build.wallet.recovery.sweep.SweepGeneratorMock
import build.wallet.recovery.sweep.SweepPsbt
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.sweep.SweepData.AwaitingHardwareSignedSweepsData
import build.wallet.statemachine.data.recovery.sweep.SweepData.GeneratePsbtsFailedData
import build.wallet.statemachine.data.recovery.sweep.SweepData.GeneratingPsbtsData
import build.wallet.statemachine.data.recovery.sweep.SweepData.NoFundsFoundData
import build.wallet.statemachine.data.recovery.sweep.SweepData.PsbtsGeneratedData
import build.wallet.statemachine.data.recovery.sweep.SweepData.SigningAndBroadcastingSweepsData
import build.wallet.statemachine.data.recovery.sweep.SweepData.SweepCompleteData
import build.wallet.statemachine.data.recovery.sweep.SweepData.SweepFailedData
import build.wallet.statemachine.data.recovery.sweep.SweepDataProps
import build.wallet.statemachine.data.recovery.sweep.SweepDataStateMachineImpl
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf

class SweepDataStateMachineImplTests : FunSpec({
  val sweepGenerator = SweepGeneratorMock()
  val serverSigner = MobilePaySigningF8eClientMock(turbines::create)
  val bitcoinBlockchain = BitcoinBlockchainMock(turbines::create)
  val spendingWallets = mutableMapOf<String, SpendingWalletMock>()
  val appSpendingWalletProvider =
    object : AppSpendingWalletProvider {
      override suspend fun getSpendingWallet(
        keyset: SpendingKeyset,
      ): Result<SpendingWallet, Throwable> {
        val wallet =
          spendingWallets.getOrPut(keyset.localId) {
            SpendingWalletMock(turbines::create, keyset.localId)
          }
        return Ok(wallet)
      }
    }
  val exchangeRateSyncer = ExchangeRateSyncerMock(turbines::create)
  val transactionRepository = OutgoingTransactionDetailRepositoryMock(turbines::create)
  val stateMachine =
    SweepDataStateMachineImpl(
      bitcoinBlockchain,
      sweepGenerator,
      serverSigner,
      appSpendingWalletProvider,
      exchangeRateSyncer,
      transactionRepository
    )

  afterTest {
    sweepGenerator.reset()
    serverSigner.reset()
    bitcoinBlockchain.reset()

    // Remove spending wallet turbines.
    // We reuse the same keyset mocks for each test. The id of those keysets are used as
    // turbine names. We need to clear the turbines to avoid turbine name collisions between
    // tests - turbine names must be unique.
    spendingWallets.values.forEach { wallet ->
      turbines.removeTurbine { name -> name.startsWith(wallet.identifier) }
    }
    spendingWallets.clear()
  }

  fun props() = SweepDataProps(KeyboxMock, {})

  test("failed to generate sweep") {
    sweepGenerator.generateSweepResult =
      Err(BdkFailedToCreatePsbt(Generic(Exception("Dang."), null), SpendingKeysetMock))

    stateMachine.test(props()) {
      awaitItem().shouldBeTypeOf<GeneratingPsbtsData>()
      awaitItem().shouldBeTypeOf<GeneratePsbtsFailedData>()
    }
  }

  test("no funds found") {
    stateMachine.test(props()) {
      awaitItem().shouldBeTypeOf<GeneratingPsbtsData>()

      awaitItem().shouldBeTypeOf<NoFundsFoundData>()
    }
  }

  test("sign and broadcast - need only app + server to sign") {
    val expectedSweepPsbts =
      listOf(
        SweepPsbt(
          PsbtMock.copyId("app-1"),
          App,
          SpendingKeysetMock
        ),
        SweepPsbt(
          PsbtMock.copyId("app-2"),
          App,
          SpendingKeysetMock2
        )
      )
    val totalFeeAmount =
      PsbtMock.fee + PsbtMock.fee
    sweepGenerator.generateSweepResult = Ok(expectedSweepPsbts)

    stateMachine.test(props()) {
      awaitItem().shouldBeTypeOf<GeneratingPsbtsData>()

      val psbtsGenerated = awaitItem().shouldBeTypeOf<PsbtsGeneratedData>()
      psbtsGenerated.totalFeeAmount.shouldBe(totalFeeAmount)

      /** User confirms sweep */
      psbtsGenerated.startSweep()

      awaitItem().shouldBeTypeOf<SigningAndBroadcastingSweepsData>()
      awaitItem().shouldBeTypeOf<SweepCompleteData>()

      val walletForKeyset1 = spendingWallets[SpendingKeysetMock.localId].shouldNotBeNull()
      walletForKeyset1.signPsbtCalls.awaitItem()
        .shouldBeTypeOf<Psbt>()
        .id.shouldBe(expectedSweepPsbts[0].psbt.id)

      val walletForKeyset2 = spendingWallets[SpendingKeysetMock2.localId].shouldNotBeNull()
      walletForKeyset2.signPsbtCalls.awaitItem()
        .shouldBeTypeOf<Psbt>()
        .id.shouldBe(expectedSweepPsbts[1].psbt.id)

      serverSigner.signWithSpecificKeysetCalls.awaitItem()
      serverSigner.signWithSpecificKeysetCalls.awaitItem()

      val psbt1 = bitcoinBlockchain.broadcastCalls.awaitItem().shouldBeTypeOf<Psbt>()
      psbt1.id.shouldBe(expectedSweepPsbts[0].psbt.id)
      psbt1.isServerSignedWithKeyset(expectedSweepPsbts[0].keyset.f8eSpendingKeyset.keysetId)
        .shouldBeTrue()
      psbt1.isAppSignedWithKeyset(expectedSweepPsbts[0].keyset).shouldBeTrue()

      // We persist this transaction into the database
      transactionRepository.setTransactionCalls.awaitItem()

      val psbt2 = bitcoinBlockchain.broadcastCalls.awaitItem().shouldBeTypeOf<Psbt>()
      psbt2.id.shouldBe(expectedSweepPsbts[1].psbt.id)
      psbt2.isServerSignedWithKeyset(expectedSweepPsbts[1].keyset.f8eSpendingKeyset.keysetId)
        .shouldBeTrue()
      psbt2.isAppSignedWithKeyset(expectedSweepPsbts[1].keyset).shouldBeTrue()

      // We persist this transaction into the database
      transactionRepository.setTransactionCalls.awaitItem()
    }
  }

  test("sign and broadcast - need hardware to sign") {
    val needsHwPsbt = PsbtMock.copyId("needs-hw")
    val needsAppPsbt = PsbtMock.copyId("needs-app")
    val expectedSweepPsbts =
      listOf(
        SweepPsbt(needsHwPsbt, Hardware, SpendingKeysetMock),
        SweepPsbt(needsAppPsbt, App, SpendingKeysetMock2)
      )
    val totalFeeAmount = needsHwPsbt.fee + needsAppPsbt.fee
    sweepGenerator.generateSweepResult = Ok(expectedSweepPsbts)

    stateMachine.test(props()) {
      awaitItem().shouldBeTypeOf<GeneratingPsbtsData>()

      val psbtsGeneratedData = awaitItem().shouldBeTypeOf<PsbtsGeneratedData>()
      psbtsGeneratedData.totalFeeAmount.shouldBe(totalFeeAmount)
      psbtsGeneratedData.startSweep()

      awaitItem().let {
        it.shouldBeTypeOf<AwaitingHardwareSignedSweepsData>()
        it.needsHwSign.shouldBe(
          mapOf(
            expectedSweepPsbts[0].keyset to expectedSweepPsbts[0].psbt
          )
        )

        // Provide hardware signed PSBTs
        val hwSignedPsbts =
          immutableListOf(
            needsHwPsbt.copyBase64("hw-signed")
          )
        it.addHwSignedSweeps(hwSignedPsbts)
      }

      awaitItem().shouldBeTypeOf<SigningAndBroadcastingSweepsData>()

      val spendingWallet = spendingWallets[SpendingKeysetMock2.localId].shouldNotBeNull()
      spendingWallet.signPsbtCalls.awaitItem()
        .shouldBeTypeOf<Psbt>()
        .id.shouldBe(expectedSweepPsbts[1].psbt.id)

      serverSigner.signWithSpecificKeysetCalls.awaitItem()
      serverSigner.signWithSpecificKeysetCalls.awaitItem()

      val psbt1 = bitcoinBlockchain.broadcastCalls.awaitItem().shouldBeTypeOf<Psbt>()
      psbt1.base64.shouldContain("hw-signed")
      psbt1.isServerSignedWithKeyset(SpendingKeysetMock.f8eSpendingKeyset.keysetId).shouldBeTrue()

      // We persist this transaction into the database
      transactionRepository.setTransactionCalls.awaitItem()

      val psbt2 = bitcoinBlockchain.broadcastCalls.awaitItem().shouldBeTypeOf<Psbt>()
      psbt2.isAppSignedWithKeyset(SpendingKeysetMock2).shouldBeTrue()
      psbt2.isServerSignedWithKeyset(SpendingKeysetMock2.f8eSpendingKeyset.keysetId).shouldBeTrue()

      // We persist this transaction into the database
      transactionRepository.setTransactionCalls.awaitItem()

      awaitItem().shouldBeTypeOf<SweepCompleteData>()
    }
  }

  test("failed to server sign") {
    val expectedSweepPsbts =
      listOf(
        SweepPsbt(PsbtMock, App, SpendingKeysetMock)
      )
    sweepGenerator.generateSweepResult = Ok(expectedSweepPsbts)
    val appSignedPsbtMock = PsbtMock.copyBase64("app-signed")
    serverSigner.signWithSpecificKeysetResult = Err(NetworkError(Error("Dang")))

    stateMachine.test(props()) {
      awaitItem().shouldBeTypeOf<GeneratingPsbtsData>()
      awaitItem().shouldBeTypeOf<PsbtsGeneratedData>()
        .startSweep()

      val walletForKeyset = spendingWallets[SpendingKeysetMock.localId].shouldNotBeNull()
      walletForKeyset.signPsbtResult = Ok(appSignedPsbtMock)

      awaitItem().shouldBeTypeOf<SigningAndBroadcastingSweepsData>()
      val sweepFailedData = awaitItem().shouldBeTypeOf<SweepFailedData>()
      sweepFailedData.cause.shouldBeInstanceOf<NetworkingError>()

      walletForKeyset.signPsbtCalls.awaitItem().shouldBe(PsbtMock)
      serverSigner.signWithSpecificKeysetCalls.awaitItem()
        .shouldBeTypeOf<Psbt>()
    }
  }

  test("failed to broadcast") {
    val expectedSweepPsbts =
      listOf(
        SweepPsbt(PsbtMock, App, SpendingKeysetMock)
      )
    sweepGenerator.generateSweepResult = Ok(expectedSweepPsbts)
    bitcoinBlockchain.broadcastResult = Err(Generic(Exception("Dang."), null))

    stateMachine.test(props()) {
      awaitItem().shouldBeTypeOf<GeneratingPsbtsData>()
      awaitItem().shouldBeTypeOf<PsbtsGeneratedData>()
        .startSweep()

      awaitItem().shouldBeTypeOf<SigningAndBroadcastingSweepsData>()

      val walletForKeyset = spendingWallets[SpendingKeysetMock.localId].shouldNotBeNull()
      walletForKeyset.signPsbtCalls.awaitItem().shouldBe(PsbtMock)
      serverSigner.signWithSpecificKeysetCalls.awaitItem()
      bitcoinBlockchain.broadcastCalls.awaitItem()

      val sweepFailedData = awaitItem().shouldBeTypeOf<SweepFailedData>()
      sweepFailedData.cause.shouldBeInstanceOf<BdkError>()
    }
  }
})

private fun Psbt.copyId(id: String) =
  Psbt(
    id = id,
    base64 = this.base64,
    fee = this.fee,
    baseSize = this.baseSize,
    numOfInputs = this.numOfInputs,
    amountSats = this.amountSats
  )

private fun Psbt.copyBase64(base64: String) =
  Psbt(
    id = this.id,
    base64 = base64,
    fee = this.fee,
    baseSize = this.baseSize,
    numOfInputs = this.numOfInputs,
    amountSats = this.amountSats
  )
