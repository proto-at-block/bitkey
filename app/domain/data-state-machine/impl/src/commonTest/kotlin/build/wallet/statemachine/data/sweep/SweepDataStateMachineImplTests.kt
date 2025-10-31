package build.wallet.statemachine.data.sweep

import build.wallet.bdk.bindings.BdkError
import build.wallet.bdk.bindings.BdkError.Generic
import build.wallet.bitcoin.transactions.BitcoinWalletServiceFake
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.bitcoin.wallet.SpendingWalletMock
import build.wallet.bitcoin.wallet.isAppSignedWithKeyset
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.keybox.SoftwareKeybox
import build.wallet.bitkey.spending.PrivateSpendingKeysetMock
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.mobilepay.MobilePaySigningF8eClientMock
import build.wallet.f8e.mobilepay.isServerSignedWithKeyset
import build.wallet.keybox.wallet.AppSpendingWalletProvider
import build.wallet.ktor.result.HttpError.NetworkError
import build.wallet.ktor.result.NetworkingError
import build.wallet.recovery.sweep.Sweep
import build.wallet.recovery.sweep.SweepContext
import build.wallet.recovery.sweep.SweepPsbt
import build.wallet.recovery.sweep.SweepServiceMock
import build.wallet.recovery.sweep.SweepSignaturePlan
import build.wallet.statemachine.core.test
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.data.recovery.sweep.SweepData.*
import build.wallet.statemachine.data.recovery.sweep.SweepDataProps
import build.wallet.statemachine.data.recovery.sweep.SweepDataStateMachineImpl
import build.wallet.time.MinimumLoadingDuration
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import kotlin.time.Duration

class SweepDataStateMachineImplTests : FunSpec({
  val sweepService = SweepServiceMock()
  val serverSigner = MobilePaySigningF8eClientMock(turbines::create)
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

      override suspend fun getSpendingWallet(
        keybox: SoftwareKeybox,
      ): Result<SpendingWallet, Throwable> {
        TODO("Software wallet does not support sweeps yet")
      }
    }
  val bitcoinWalletService = BitcoinWalletServiceFake()
  val stateMachine =
    SweepDataStateMachineImpl(
      sweepService,
      serverSigner,
      appSpendingWalletProvider,
      bitcoinWalletService,
      MinimumLoadingDuration(Duration.ZERO)
    )

  afterTest {
    sweepService.reset()
    serverSigner.reset()
    bitcoinWalletService.reset()

    // Remove spending wallet turbines.
    // We reuse the same keyset mocks for each test. The id of those keysets are used as
    // turbine names. We need to clear the turbines to avoid turbine name collisions between
    // tests - turbine names must be unique.
    spendingWallets.values.forEach { wallet ->
      turbines.removeTurbine { name -> name.startsWith(wallet.identifier) }
    }
    spendingWallets.clear()
  }

  fun props(onAttemptSweep: () -> Unit = {}) =
    SweepDataProps(
      hasAttemptedSweep = false,
      onAttemptSweep = onAttemptSweep,
      keybox = KeyboxMock,
      sweepContext = SweepContext.InactiveWallet,
      onSuccess = {}
    )

  test("failed to generate sweep") {
    sweepService.prepareSweepResult = Err(Error())

    stateMachine.test(props()) {
      awaitItem().shouldBeTypeOf<GeneratingPsbtsData>()
      awaitItem().shouldBeTypeOf<GeneratePsbtsFailedData>()
    }
  }

  test("no funds found") {
    sweepService.prepareSweepResult = Ok(null)

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
          SweepSignaturePlan.AppAndServer,
          SpendingKeysetMock,
          "bc1qtest"
        ),
        SweepPsbt(
          PsbtMock.copyId("app-2"),
          SweepSignaturePlan.AppAndServer,
          PrivateSpendingKeysetMock,
          "bc1qtest"
        )
      )
    val totalFeeAmount = PsbtMock.fee + PsbtMock.fee
    val totalTransferAmount = PsbtMock.amountBtc + PsbtMock.amountBtc
    sweepService.prepareSweepResult = Ok(
      Sweep(unsignedPsbts = expectedSweepPsbts.toSet())
    )

    stateMachine.test(props()) {
      awaitItem().shouldBeTypeOf<GeneratingPsbtsData>()

      val psbtsGenerated = awaitItem().shouldBeTypeOf<PsbtsGeneratedData>()
      psbtsGenerated.totalFeeAmount.shouldBe(totalFeeAmount)
      psbtsGenerated.totalTransferAmount.shouldBe(totalTransferAmount)
      psbtsGenerated.destinationAddress.shouldBe("bc1qtest")

      /** User confirms sweep */
      psbtsGenerated.startSweep()

      awaitItem().shouldBeTypeOf<SigningAndBroadcastingSweepsData>()
      val sweepComplete = awaitItem().shouldBeTypeOf<SweepCompleteData>()
      sweepComplete.totalFeeAmount.shouldBe(totalFeeAmount)
      sweepComplete.totalTransferAmount.shouldBe(totalTransferAmount)
      sweepComplete.destinationAddress.shouldBe("bc1qtest")

      val walletForKeyset1 = spendingWallets[SpendingKeysetMock.localId].shouldNotBeNull()
      walletForKeyset1.signPsbtCalls.awaitItem()
        .shouldBeTypeOf<Psbt>()
        .id.shouldBe(expectedSweepPsbts[0].psbt.id)

      val walletForKeyset2 = spendingWallets[PrivateSpendingKeysetMock.localId].shouldNotBeNull()
      walletForKeyset2.signPsbtCalls.awaitItem()
        .shouldBeTypeOf<Psbt>()
        .id.shouldBe(expectedSweepPsbts[1].psbt.id)

      serverSigner.signWithSpecificKeysetCalls.awaitItem()
      serverSigner.signWithSpecificKeysetCalls.awaitItem()

      val psbt1 = bitcoinWalletService.broadcastedPsbts.value.single {
        it.id == expectedSweepPsbts[0].psbt.id
      }
      psbt1.isServerSignedWithKeyset(expectedSweepPsbts[0].sourceKeyset.f8eSpendingKeyset.keysetId)
        .shouldBeTrue()
      psbt1.isAppSignedWithKeyset(expectedSweepPsbts[0].sourceKeyset).shouldBeTrue()

      val psbt2 = bitcoinWalletService.broadcastedPsbts.value.single {
        it.id == expectedSweepPsbts[1].psbt.id
      }
      psbt2.id.shouldBe(expectedSweepPsbts[1].psbt.id)
      psbt2.isServerSignedWithKeyset(expectedSweepPsbts[1].sourceKeyset.f8eSpendingKeyset.keysetId)
        .shouldBeTrue()
      psbt2.isAppSignedWithKeyset(expectedSweepPsbts[1].sourceKeyset).shouldBeTrue()
    }
  }

  test("sign and broadcast - need hardware to sign") {
    val needsHwPsbt = PsbtMock.copyId("needs-hw")
    val needsAppPsbt = PsbtMock.copyId("needs-app")
    val expectedSweepPsbts =
      listOf(
        SweepPsbt(
          needsHwPsbt,
          SweepSignaturePlan.HardwareAndServer,
          SpendingKeysetMock,
          "bc1qtest"
        ),
        SweepPsbt(
          needsAppPsbt,
          SweepSignaturePlan.AppAndServer,
          PrivateSpendingKeysetMock,
          "bc1qtest"
        )
      )
    val totalFeeAmount = needsHwPsbt.fee + needsAppPsbt.fee
    val totalTransferAmount = needsHwPsbt.amountBtc + needsAppPsbt.amountBtc
    sweepService.prepareSweepResult = Ok(
      Sweep(unsignedPsbts = expectedSweepPsbts.toSet())
    )

    stateMachine.testWithVirtualTime(props()) {
      awaitItem().shouldBeTypeOf<GeneratingPsbtsData>()

      val psbtsGeneratedData = awaitItem().shouldBeTypeOf<PsbtsGeneratedData>()
      psbtsGeneratedData.totalFeeAmount.shouldBe(totalFeeAmount)
      psbtsGeneratedData.totalTransferAmount.shouldBe(totalTransferAmount)
      psbtsGeneratedData.destinationAddress.shouldBe("bc1qtest")
      psbtsGeneratedData.startSweep()

      awaitItem().let {
        it.shouldBeTypeOf<AwaitingHardwareSignedSweepsData>()
        it.needsHwSign.shouldContainOnly(expectedSweepPsbts[0])

        // Provide hardware signed PSBTs
        val hwSignedPsbts = setOf(needsHwPsbt.copyBase64("hw-signed"))
        it.addHwSignedSweeps(hwSignedPsbts)
      }

      awaitItem().shouldBeTypeOf<SigningAndBroadcastingSweepsData>()

      val spendingWallet = spendingWallets[PrivateSpendingKeysetMock.localId].shouldNotBeNull()
      spendingWallet.signPsbtCalls.awaitItem()
        .shouldBeTypeOf<Psbt>()
        .id.shouldBe(expectedSweepPsbts[1].psbt.id)

      serverSigner.signWithSpecificKeysetCalls.awaitItem()
      serverSigner.signWithSpecificKeysetCalls.awaitItem()

      val psbt1 = bitcoinWalletService.broadcastedPsbts.value.single {
        it.id == expectedSweepPsbts[0].psbt.id
      }
      psbt1.base64.shouldContain("hw-signed")
      psbt1.isServerSignedWithKeyset(SpendingKeysetMock.f8eSpendingKeyset.keysetId).shouldBeTrue()

      val psbt2 = bitcoinWalletService.broadcastedPsbts.value.single {
        it.id == expectedSweepPsbts[1].psbt.id
      }
      psbt2.isAppSignedWithKeyset(PrivateSpendingKeysetMock).shouldBeTrue()
      psbt2.isServerSignedWithKeyset(PrivateSpendingKeysetMock.f8eSpendingKeyset.keysetId).shouldBeTrue()

      val sweepComplete = awaitItem().shouldBeTypeOf<SweepCompleteData>()
      sweepComplete.totalFeeAmount.shouldBe(totalFeeAmount)
      sweepComplete.totalTransferAmount.shouldBe(totalTransferAmount)
      sweepComplete.destinationAddress.shouldBe("bc1qtest")
    }
  }

  test("failed to server sign") {
    val expectedSweepPsbts = listOf(
      SweepPsbt(PsbtMock, SweepSignaturePlan.AppAndServer, SpendingKeysetMock, "bc1qtest")
    )
    sweepService.prepareSweepResult = Ok(
      Sweep(unsignedPsbts = expectedSweepPsbts.toSet())
    )
    val appSignedPsbtMock = PsbtMock.copyBase64("app-signed")
    serverSigner.signWithSpecificKeysetResult = Err(NetworkError(Error("Dang")))

    stateMachine.testWithVirtualTime(props()) {
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
        .shouldBeTypeOf<Pair<Psbt, *>>().first
        .shouldBeTypeOf<Psbt>()
    }
  }

  test("failed to broadcast") {
    val expectedSweepPsbts = listOf(
      SweepPsbt(PsbtMock, SweepSignaturePlan.AppAndServer, SpendingKeysetMock, "bc1qtest")
    )
    sweepService.prepareSweepResult = Ok(
      Sweep(unsignedPsbts = expectedSweepPsbts.toSet())
    )
    bitcoinWalletService.broadcastError = Generic(cause = Exception("Dang."), message = null)

    stateMachine.testWithVirtualTime(props()) {
      awaitItem().shouldBeTypeOf<GeneratingPsbtsData>()
      awaitItem().shouldBeTypeOf<PsbtsGeneratedData>()
        .startSweep()

      awaitItem().shouldBeTypeOf<SigningAndBroadcastingSweepsData>()

      val walletForKeyset = spendingWallets[SpendingKeysetMock.localId].shouldNotBeNull()
      walletForKeyset.signPsbtCalls.awaitItem().shouldBe(PsbtMock)
      serverSigner.signWithSpecificKeysetCalls.awaitItem()

      val sweepFailedData = awaitItem().shouldBeTypeOf<SweepFailedData>()
      sweepFailedData.cause.shouldBeInstanceOf<BdkError>()
    }
  }

  test("sweep attempted flag shows success even with no funds") {
    sweepService.prepareSweepResult = Ok(null)
    var proceedCount = 0

    stateMachine.test(
      props = SweepDataProps(
        hasAttemptedSweep = true,
        onAttemptSweep = {},
        keybox = KeyboxMock,
        onSuccess = { proceedCount++ }
      )
    ) {
      awaitItem().shouldBeTypeOf<GeneratingPsbtsData>()
      val success = awaitItem().shouldBeTypeOf<SweepCompleteNoData>()
      success.proceed()
    }
    proceedCount.shouldBe(1)
  }

  test("sweep without attempted flag shows normal no funds flow") {
    sweepService.prepareSweepResult = Ok(null)

    // Test normal flow without completed flag
    stateMachine.test(
      SweepDataProps(
        hasAttemptedSweep = false,
        onAttemptSweep = {},
        keybox = KeyboxMock,
        onSuccess = {}
      )
    ) {
      awaitItem().shouldBeTypeOf<GeneratingPsbtsData>()
      awaitItem().shouldBeTypeOf<NoFundsFoundData>()
    }
  }

  test("private wallet migration with no funds auto progresses") {
    sweepService.prepareSweepResult = Ok(null)
    sweepService.sweepRequired.value = true
    var successCount = 0

    stateMachine.test(
      SweepDataProps(
        hasAttemptedSweep = false,
        onAttemptSweep = {},
        keybox = KeyboxMock,
        sweepContext = SweepContext.PrivateWalletMigration,
        onSuccess = { successCount++ }
      )
    ) {
      awaitItem().shouldBeTypeOf<GeneratingPsbtsData>()
      expectNoEvents()
    }

    successCount.shouldBe(1)
    sweepService.sweepRequired.value.shouldBe(false)
  }

  test("recovery progress set immediately when sweep starts") {
    val expectedSweepPsbts = listOf(
      SweepPsbt(PsbtMock, SweepSignaturePlan.AppAndServer, SpendingKeysetMock, "bc1qtest")
    )
    sweepService.prepareSweepResult = Ok(
      Sweep(unsignedPsbts = expectedSweepPsbts.toSet())
    )
    var sweepAttemptCounter = 0

    stateMachine.test(
      props(
        onAttemptSweep = {
          sweepAttemptCounter++
        }
      )
    ) {
      awaitItem().shouldBeTypeOf<GeneratingPsbtsData>()
      val psbtsGenerated = awaitItem().shouldBeTypeOf<PsbtsGeneratedData>()
      psbtsGenerated.startSweep()

      awaitItem().shouldBeTypeOf<SigningAndBroadcastingSweepsData>()

      // Consume remaining calls to avoid test failures
      // The wallet should be created by now since we're in SigningAndBroadcastingSweepsData state
      val walletForKeyset = spendingWallets[SpendingKeysetMock.localId]
      walletForKeyset?.signPsbtCalls?.awaitItem()?.shouldBe(PsbtMock)
      serverSigner.signWithSpecificKeysetCalls.awaitItem()

      awaitItem().shouldBeTypeOf<SweepCompleteData>()
      sweepAttemptCounter.shouldBe(1)
    }
  }

  test("recovery progress set even when sweep fails during signing") {
    val expectedSweepPsbts = listOf(
      SweepPsbt(PsbtMock, SweepSignaturePlan.AppAndServer, SpendingKeysetMock, "bc1qtest")
    )
    sweepService.prepareSweepResult = Ok(
      Sweep(unsignedPsbts = expectedSweepPsbts.toSet())
    )
    serverSigner.signWithSpecificKeysetResult = Err(NetworkError(Error("Network failure")))

    stateMachine.testWithVirtualTime(props()) {
      awaitItem().shouldBeTypeOf<GeneratingPsbtsData>()
      awaitItem().shouldBeTypeOf<PsbtsGeneratedData>()
        .startSweep()

      awaitItem().shouldBeTypeOf<SigningAndBroadcastingSweepsData>()

      // The app signing happens first, so we should have a wallet created
      val walletForKeyset = spendingWallets[SpendingKeysetMock.localId].shouldNotBeNull()
      walletForKeyset.signPsbtCalls.awaitItem().shouldBe(PsbtMock)

      // Server signing should be attempted and fail
      serverSigner.signWithSpecificKeysetCalls.awaitItem()

      awaitItem().shouldBeTypeOf<SweepFailedData>()
    }
  }

  test("private wallet migration - single HW-signed PSBT avoids server") {
    val hwSignedPsbt = PsbtMock.copyId("hw-signed-migration").copyBase64("hw-signed-base64")
    val oldMultisigKeyset = SpendingKeysetMock
    val expectedSweepPsbts =
      listOf(
        SweepPsbt(hwSignedPsbt, SweepSignaturePlan.AppAndHardware, oldMultisigKeyset, "bc1qtest")
      )
    sweepService.prepareSweepResult = Ok(
      Sweep(unsignedPsbts = expectedSweepPsbts.toSet())
    )

    val migrationProps = SweepDataProps(
      hasAttemptedSweep = false,
      onAttemptSweep = {},
      keybox = KeyboxMock,
      sweepContext = SweepContext.PrivateWalletMigration,
      onSuccess = {}
    )

    stateMachine.testWithVirtualTime(migrationProps) {
      awaitItem().shouldBeTypeOf<GeneratingPsbtsData>()

      val psbtsGeneratedData = awaitItem().shouldBeTypeOf<PsbtsGeneratedData>()
      psbtsGeneratedData.startSweep()

      val awaitingHwData = awaitItem().shouldBeTypeOf<AwaitingHardwareSignedSweepsData>()
      awaitingHwData.needsHwSign.shouldContainOnly(expectedSweepPsbts[0])

      // Hardware signs the PSBT
      awaitingHwData.addHwSignedSweeps(setOf(hwSignedPsbt))

      awaitItem().shouldBeTypeOf<SigningAndBroadcastingSweepsData>()

      // Verify app wallet signs the HW-signed PSBT (not server)
      val walletForOldKeyset = spendingWallets[oldMultisigKeyset.localId].shouldNotBeNull()
      walletForOldKeyset.signPsbtCalls.awaitItem()
        .shouldBeTypeOf<Psbt>()
        .base64.shouldContain("hw-signed-base64")

      serverSigner.signWithSpecificKeysetCalls.expectNoEvents()

      // Verify broadcast happens with app-signed PSBT
      val broadcastedPsbt = bitcoinWalletService.broadcastedPsbts.value.single()
      broadcastedPsbt.id.shouldBe(hwSignedPsbt.id)
      broadcastedPsbt.isAppSignedWithKeyset(oldMultisigKeyset).shouldBeTrue()

      awaitItem().shouldBeTypeOf<SweepCompleteData>()
    }
  }

  test("private wallet migration - multiple HW PSBTs all avoid server") {
    val hwPsbt1 = PsbtMock.copyId("hw-1").copyBase64("hw-signed-1")
    val hwPsbt2 = PsbtMock.copyId("hw-2").copyBase64("hw-signed-2")
    val keyset1 = SpendingKeysetMock
    val keyset2 = PrivateSpendingKeysetMock

    val expectedSweepPsbts =
      listOf(
        SweepPsbt(hwPsbt1, SweepSignaturePlan.AppAndHardware, keyset1, "bc1qtest"),
        SweepPsbt(hwPsbt2, SweepSignaturePlan.AppAndHardware, keyset2, "bc1qtest")
      )
    sweepService.prepareSweepResult = Ok(
      Sweep(unsignedPsbts = expectedSweepPsbts.toSet())
    )

    val migrationProps = SweepDataProps(
      hasAttemptedSweep = false,
      onAttemptSweep = {},
      keybox = KeyboxMock,
      sweepContext = SweepContext.PrivateWalletMigration,
      onSuccess = {}
    )

    stateMachine.testWithVirtualTime(migrationProps) {
      awaitItem().shouldBeTypeOf<GeneratingPsbtsData>()

      awaitItem().shouldBeTypeOf<PsbtsGeneratedData>()
        .startSweep()

      val awaitingHwData = awaitItem().shouldBeTypeOf<AwaitingHardwareSignedSweepsData>()
      awaitingHwData.addHwSignedSweeps(setOf(hwPsbt1, hwPsbt2))

      awaitItem().shouldBeTypeOf<SigningAndBroadcastingSweepsData>()

      // Both wallets should sign their respective PSBTs
      val wallet1 = spendingWallets[keyset1.localId].shouldNotBeNull()
      wallet1.signPsbtCalls.awaitItem()
        .shouldBeTypeOf<Psbt>()
        .base64.shouldContain("hw-signed-1")

      val wallet2 = spendingWallets[keyset2.localId].shouldNotBeNull()
      wallet2.signPsbtCalls.awaitItem()
        .shouldBeTypeOf<Psbt>()
        .base64.shouldContain("hw-signed-2")

      serverSigner.signWithSpecificKeysetCalls.expectNoEvents()

      bitcoinWalletService.broadcastedPsbts.value.size.shouldBe(2)

      awaitItem().shouldBeTypeOf<SweepCompleteData>()
    }
  }

  test("private wallet migration - app wallet unavailable fails") {
    val hwSignedPsbt = PsbtMock.copyId("hw-signed").copyBase64("hw-signed")
    val expectedSweepPsbts =
      listOf(
        SweepPsbt(hwSignedPsbt, SweepSignaturePlan.AppAndHardware, SpendingKeysetMock, "bc1qtest")
      )
    sweepService.prepareSweepResult = Ok(
      Sweep(unsignedPsbts = expectedSweepPsbts.toSet())
    )

    // Break the app wallet provider
    val brokenWalletProvider = object : AppSpendingWalletProvider {
      override suspend fun getSpendingWallet(
        keyset: SpendingKeyset,
      ): Result<SpendingWallet, Throwable> {
        return Err(Exception("Wallet unavailable"))
      }

      override suspend fun getSpendingWallet(
        keybox: SoftwareKeybox,
      ): Result<SpendingWallet, Throwable> {
        TODO("Not needed")
      }
    }

    val brokenStateMachine = SweepDataStateMachineImpl(
      sweepService,
      serverSigner,
      brokenWalletProvider,
      bitcoinWalletService,
      MinimumLoadingDuration(Duration.ZERO)
    )

    val migrationProps = SweepDataProps(
      hasAttemptedSweep = false,
      onAttemptSweep = {},
      keybox = KeyboxMock,
      sweepContext = SweepContext.PrivateWalletMigration,
      onSuccess = {}
    )

    brokenStateMachine.testWithVirtualTime(migrationProps) {
      awaitItem().shouldBeTypeOf<GeneratingPsbtsData>()
      awaitItem().shouldBeTypeOf<PsbtsGeneratedData>()
        .startSweep()

      val awaitingHwData = awaitItem().shouldBeTypeOf<AwaitingHardwareSignedSweepsData>()
      awaitingHwData.addHwSignedSweeps(setOf(hwSignedPsbt))

      awaitItem().shouldBeTypeOf<SigningAndBroadcastingSweepsData>()

      val sweepFailedData = awaitItem().shouldBeTypeOf<SweepFailedData>()
      sweepFailedData.cause.message.shouldContain("Wallet unavailable")
    }
  }

  test("private wallet migration - app signing fails") {
    val hwSignedPsbt = PsbtMock.copyId("hw-signed").copyBase64("hw-signed")
    val oldMultisigKeyset = SpendingKeysetMock
    val expectedSweepPsbts =
      listOf(
        SweepPsbt(hwSignedPsbt, SweepSignaturePlan.AppAndHardware, oldMultisigKeyset, "bc1qtest")
      )
    sweepService.prepareSweepResult = Ok(
      Sweep(unsignedPsbts = expectedSweepPsbts.toSet())
    )

    val migrationProps = SweepDataProps(
      hasAttemptedSweep = false,
      onAttemptSweep = {},
      keybox = KeyboxMock,
      sweepContext = SweepContext.PrivateWalletMigration,
      onSuccess = {}
    )

    stateMachine.testWithVirtualTime(migrationProps) {
      awaitItem().shouldBeTypeOf<GeneratingPsbtsData>()

      // Configure wallet to fail on signing before generating PSBTs triggers wallet creation
      val wallet = spendingWallets.getOrPut(oldMultisigKeyset.localId) {
        SpendingWalletMock(turbines::create, oldMultisigKeyset.localId)
      }
      wallet.signPsbtResult = Err(Exception("Signing failed"))

      awaitItem().shouldBeTypeOf<PsbtsGeneratedData>()
        .startSweep()

      val awaitingHwData = awaitItem().shouldBeTypeOf<AwaitingHardwareSignedSweepsData>()
      awaitingHwData.addHwSignedSweeps(setOf(hwSignedPsbt))

      awaitItem().shouldBeTypeOf<SigningAndBroadcastingSweepsData>()

      wallet.signPsbtCalls.awaitItem()

      val sweepFailedData = awaitItem().shouldBeTypeOf<SweepFailedData>()
      sweepFailedData.cause.message.shouldContain("Signing failed")
    }
  }

  test("markSweepHandled called on successful sweep with data") {
    val expectedSweepPsbts = listOf(
      SweepPsbt(
        PsbtMock.copyId("app-1"),
        SweepSignaturePlan.AppAndServer,
        SpendingKeysetMock,
        "bc1qtest"
      )
    )
    sweepService.prepareSweepResult = Ok(
      Sweep(unsignedPsbts = expectedSweepPsbts.toSet())
    )

    // Set sweep required flag to true initially
    sweepService.sweepRequired.value = true

    stateMachine.test(props()) {
      awaitItem().shouldBeTypeOf<GeneratingPsbtsData>()

      awaitItem().shouldBeTypeOf<PsbtsGeneratedData>().startSweep()
      awaitItem().shouldBeTypeOf<SigningAndBroadcastingSweepsData>()
      awaitItem().shouldBeTypeOf<SweepCompleteData>()

      // Consume wallet signing calls
      val walletForKeyset = spendingWallets[SpendingKeysetMock.localId].shouldNotBeNull()
      walletForKeyset.signPsbtCalls.awaitItem()
      serverSigner.signWithSpecificKeysetCalls.awaitItem()

      // Verify sweep flag was cleared
      sweepService.sweepRequired.value.shouldBe(false)
    }
  }

  test("markSweepHandled called on successful sweep without data") {
    sweepService.prepareSweepResult = Ok(null)

    // Set sweep required flag to true initially
    sweepService.sweepRequired.value = true

    stateMachine.test(
      SweepDataProps(
        hasAttemptedSweep = true,
        onAttemptSweep = {},
        keybox = KeyboxMock,
        onSuccess = {}
      )
    ) {
      awaitItem().shouldBeTypeOf<GeneratingPsbtsData>()
      awaitItem().shouldBeTypeOf<SweepCompleteNoData>()

      // Verify sweep flag was cleared
      sweepService.sweepRequired.value.shouldBe(false)
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
