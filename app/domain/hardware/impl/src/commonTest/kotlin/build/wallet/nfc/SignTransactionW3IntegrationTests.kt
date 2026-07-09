package build.wallet.nfc

import bitkey.account.AccountConfigServiceFake
import bitkey.account.HardwareType
import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilderMock
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitcoin.wallet.SpendingWalletFake
import build.wallet.bitcoin.wallet.SpendingWalletV2ProviderMock
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.encrypt.MessageSignerFake
import build.wallet.encrypt.SignatureUtilsMock
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.Bdk2FeatureFlag
import build.wallet.nfc.platform.ConfirmationHandles
import build.wallet.nfc.platform.ConfirmationHandlesFake
import build.wallet.nfc.platform.ConfirmationResult
import build.wallet.nfc.platform.HardwareInteraction
import build.wallet.nfc.platform.SweepSigningContext
import build.wallet.nfc.platform.SweepXpub
import build.wallet.nfc.platform.W3NfcCommands
import build.wallet.nfc.platform.confirmationResultMapper
import build.wallet.nfc.platform.toSessionFn
import okio.ByteString.Companion.toByteString
import build.wallet.sqldelight.inMemorySqlDriver
import com.github.michaelbull.result.Ok
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import okio.ByteString.Companion.encodeUtf8
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Integration tests validating W3 sign transaction flows end-to-end.
 *
 * These tests exercise the complete W3 signing interaction pattern using
 * BitkeyW3CommandsFake, validating that the two-tap flow, progress tracking,
 * and emulated prompts work correctly together across all layers.
 */
class SignTransactionW3IntegrationTests : FunSpec({

  lateinit var w3CommandsFake: BitkeyW3CommandsFake

  beforeTest {
    // Create minimal fake setup for W3 interaction pattern testing
    val sqlDriver = inMemorySqlDriver()
    val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
    val fakeHardwareStatesDao = FakeHardwareStatesDaoImpl(databaseProvider)
    val messageSigner = MessageSignerFake()
    val signatureUtils = SignatureUtilsMock()
    val fakeHardwareKeyStore = FakeHardwareKeyStoreFake()
    val featureFlagDao = FeatureFlagDaoFake()
    val accountConfigService = AccountConfigServiceFake().apply {
      setHardwareType(HardwareType.W3)
    }
    val fakeHardwareSpendingWalletProvider =
      FakeHardwareSpendingWalletProvider(
        spendingWalletProvider = { Ok(SpendingWalletFake()) },
        spendingWalletV2Provider = SpendingWalletV2ProviderMock(),
        bdk2FeatureFlag = Bdk2FeatureFlag(featureFlagDao),
        descriptorBuilder = BitcoinMultiSigDescriptorBuilderMock(),
        fakeHardwareKeyStore = fakeHardwareKeyStore
      )

    val w1CommandsFake =
      BitkeyW1CommandsFake(
        messageSigner = messageSigner,
        signatureUtils = signatureUtils,
        fakeHardwareKeyStore = fakeHardwareKeyStore,
        fakeHardwareSpendingWalletProvider = fakeHardwareSpendingWalletProvider,
        fakeHardwareStatesDao = fakeHardwareStatesDao
      )

    w3CommandsFake = BitkeyW3CommandsFake(
      w1CommandsFake = w1CommandsFake,
      accountConfigService = accountConfigService,
      fakeHardwareKeyStore = fakeHardwareKeyStore,
      fakeHardwareSpendingWalletProvider = fakeHardwareSpendingWalletProvider,
      fakeHardwareStatesDao = fakeHardwareStatesDao,
      messageSigner = messageSigner,
      signatureUtils = signatureUtils
    )

    fakeHardwareKeyStore.clear()
    fakeHardwareStatesDao.clear()

    // Deliver the hardware descriptor so signing tests don't throw DescriptorNotLoaded.
    // Real W3 hardware starts without a descriptor; this mirrors the onboarding step.
    val session = NfcSessionFake.invoke()
    w3CommandsFake.verifyKeysAndBuildDescriptor(
      session = session,
      appSpendingKey = "fake-app-spending-key".encodeUtf8(),
      appSpendingKeyChaincode = "fake-app-chaincode".encodeUtf8(),
      networkMainnet = false,
      appAuthKey = "fake-app-auth-key".encodeUtf8(),
      serverSpendingKey = "fake-server-key".encodeUtf8(),
      serverSpendingKeyChaincode = "fake-server-chaincode".encodeUtf8(),
      wsmSignature = "fake-wsm-signature".encodeUtf8(),
      accountIndex = 0u,
    )
  }

  // ========================================================================
  // End-to-End W3 Flow Tests
  // ========================================================================

  context("W3 complete signing flow") {
    test("signTransaction returns ConfirmWithEmulatedPrompt with APPROVE and DENY options") {
      val session = NfcSessionFake.invoke()

      val interaction = w3CommandsFake.signTransaction(
        session = session,
        psbt = PsbtMock,
        spendingKeyset = SpendingKeysetMock
      )

      // W3 non-PSBT protocol returns ConfirmWithEmulatedPrompt (no transfer phase)
      interaction.shouldBeInstanceOf<HardwareInteraction.ConfirmWithEmulatedPrompt<*>>()

      val emulatedPrompt = interaction as HardwareInteraction.ConfirmWithEmulatedPrompt
      emulatedPrompt.approve.shouldNotBeNull()
      emulatedPrompt.deny.shouldNotBeNull()
    }

    test("APPROVE flow structure validation") {
      val session = NfcSessionFake.invoke()

      val emulatedPrompt = w3CommandsFake.signTransaction(
        session = session,
        psbt = PsbtMock,
        spendingKeyset = SpendingKeysetMock
      ) as HardwareInteraction.ConfirmWithEmulatedPrompt

      // APPROVE should have a fetchResult callback for second tap.
      // Actual execution would require a signing-capable wallet
      // (SpendingWalletFake doesn't support signPsbt), so we validate the
      // structure rather than executing the full flow.
      emulatedPrompt.approve.fetchResult.shouldNotBeNull()

      // APPROVE should not have immediate side effects (onSelect is null)
      val hasImmediateSideEffect = emulatedPrompt.approve.onSelect != null
      hasImmediateSideEffect.shouldBe(false)
    }

    test("DENY flow validation") {
      val session = NfcSessionFake.invoke()

      val emulatedPrompt = w3CommandsFake.signTransaction(
        session = session,
        psbt = PsbtMock,
        spendingKeyset = SpendingKeysetMock
      ) as HardwareInteraction.ConfirmWithEmulatedPrompt

      // DENY should throw NfcException.UserDenied when fetchResult is called
      shouldThrow<NfcException.UserDenied> {
        emulatedPrompt.deny.fetchResult(session, w3CommandsFake)
      }
    }
  }

  // ========================================================================
  // W3 sweep signing flow
  // ========================================================================

  context("W3 sweep signing flow") {
    fun sweepContextFake(oldAccountIndex: UInt = 5u): SweepSigningContext = SweepSigningContext(
      oldAccountIndex = oldAccountIndex,
      oldAppXpub = SweepXpub(
        pubkey = ByteArray(33) { 0x02 }.toByteString(),
        chaincode = ByteArray(32) { 0xab.toByte() }.toByteString()
      ),
      oldServerXpub = SweepXpub(
        pubkey = ByteArray(33) { 0x03 }.toByteString(),
        chaincode = ByteArray(32) { 0xcd.toByte() }.toByteString()
      )
    )

    test("sweepTransaction routes through dedicated prompt and records sweep context") {
      val session = NfcSessionFake.invoke()
      val ctx = sweepContextFake(oldAccountIndex = 7u)

      val interaction = w3CommandsFake.sweepTransaction(
        session = session,
        psbt = PsbtMock,
        spendingKeyset = SpendingKeysetMock,
        sweepContext = ctx
      )

      // Sweep signing returns the same RequiresConfirmation shape as normal
      // signing (same proto response path), so the fake surfaces the same
      // emulated-prompt wrapper.
      interaction.shouldBeInstanceOf<HardwareInteraction.ConfirmWithEmulatedPrompt<*>>()
      w3CommandsFake.lastSweepContext.shouldNotBeNull()
      w3CommandsFake.lastSweepContext?.oldAccountIndex.shouldBe(7u)
    }

    test("signTransaction leaves sweep context untouched (normal path does not touch sweep state)") {
      val session = NfcSessionFake.invoke()

      w3CommandsFake.signTransaction(
        session = session,
        psbt = PsbtMock,
        spendingKeyset = SpendingKeysetMock
      )

      w3CommandsFake.lastSweepContext.shouldBe(null)
    }
  }

  // ========================================================================
  // W3 vs W1 Behavior Validation
  // ========================================================================

  context("W3 interaction pattern") {
    test("W3 returns ConfirmWithEmulatedPrompt (not Completed like W1)") {
      val session = NfcSessionFake.invoke()

      val w3Interaction = w3CommandsFake.signTransaction(
        session = session,
        psbt = PsbtMock,
        spendingKeyset = SpendingKeysetMock
      )

      // W3 uses non-PSBT protocol: sends raw tx fields, requires on-device confirmation
      w3Interaction.shouldBeInstanceOf<HardwareInteraction.ConfirmWithEmulatedPrompt<*>>()
    }
  }

  // ========================================================================
  // RequiresConfirmation data-driven wiring tests
  //
  // These tests exercise the full RequiresConfirmation → toSessionFn() →
  // getConfirmationResult → mapResult chain that real (non-fake) hardware
  // implementations use. A stub NfcCommands returns a canned ConfirmationResult
  // so we can verify the wiring without a physical device.
  // ========================================================================

  context("RequiresConfirmation toSessionFn wiring") {
    // Stub NfcCommands that returns a controlled ConfirmationResult for second tap
    fun makeStubCommands(confirmationResult: ConfirmationResult): W3NfcCommands =
      object : W3NfcCommands by w3CommandsFake {
        override suspend fun getConfirmationResult(
          session: NfcSession,
          handles: ConfirmationHandles,
        ): ConfirmationResult = confirmationResult
      }

    test("mapResult is called with the ConfirmationResult returned by getConfirmationResult") {
      val confirmation = HardwareInteraction.RequiresConfirmation<Boolean>(
        handles = ConfirmationHandlesFake,
        mapResult = confirmationResultMapper<Boolean> { result ->
          when (result) {
            is ConfirmationResult.FwupStart -> HardwareInteraction.Completed(result.success)
            else -> throw NfcException.CommandError(message = "unexpected: ${result::class.simpleName}")
          }
        }
      )
      val session = NfcSessionFake.invoke()
      val stubCommands = makeStubCommands(ConfirmationResult.FwupStart(success = true))

      val result = confirmation.toSessionFn<Boolean>()(session, stubCommands)

      result.shouldBeInstanceOf<HardwareInteraction.Completed<Boolean>>()
      (result as HardwareInteraction.Completed<Boolean>).result.shouldBe(true)
    }

    test("NfcException.ConfirmationPending from mapResult propagates correctly") {
      val confirmation = HardwareInteraction.RequiresConfirmation<Boolean>(
        handles = ConfirmationHandlesFake,
        mapResult = confirmationResultMapper<Boolean> { result ->
          when (result) {
            is ConfirmationResult.Pending -> throw NfcException.ConfirmationPending()
            else -> throw NfcException.CommandError(message = "unexpected: ${result::class.simpleName}")
          }
        }
      )
      val session = NfcSessionFake.invoke()
      val stubCommands = makeStubCommands(ConfirmationResult.Pending)

      shouldThrow<NfcException.ConfirmationPending> {
        confirmation.toSessionFn<Boolean>()(session, stubCommands)
      }
    }

    test("NfcException.UserDenied from mapResult propagates correctly") {
      val confirmation = HardwareInteraction.RequiresConfirmation<Boolean>(
        handles = ConfirmationHandlesFake,
        mapResult = confirmationResultMapper<Boolean> { result ->
          when (result) {
            is ConfirmationResult.Denied -> throw NfcException.UserDenied()
            else -> throw NfcException.CommandError(message = "unexpected: ${result::class.simpleName}")
          }
        }
      )
      val session = NfcSessionFake.invoke()
      val stubCommands = makeStubCommands(ConfirmationResult.Denied)

      shouldThrow<NfcException.UserDenied> {
        confirmation.toSessionFn<Boolean>()(session, stubCommands)
      }
    }

    test("handles are forwarded to getConfirmationResult") {
      val capturedHandles = mutableListOf<ConfirmationHandles>()
      val specificHandles = ConfirmationHandles(
        responseHandle = listOf(0xAB.toUByte()),
        confirmationHandle = listOf(0xCD.toUByte())
      )
      val confirmation = HardwareInteraction.RequiresConfirmation<Boolean>(
        handles = specificHandles,
        mapResult = confirmationResultMapper<Boolean> { _ ->
          HardwareInteraction.Completed(true)
        }
      )
      val session = NfcSessionFake.invoke()
      val capturingCommands = object : W3NfcCommands by w3CommandsFake {
        override suspend fun getConfirmationResult(
          session: NfcSession,
          handles: ConfirmationHandles,
        ): ConfirmationResult {
          capturedHandles += handles
          return ConfirmationResult.FwupStart(success = true)
        }
      }

      confirmation.toSessionFn<Boolean>()(session, capturingCommands)

      capturedHandles.shouldHaveSize(1)
      capturedHandles.first().responseHandle.shouldBe(specificHandles.responseHandle)
      capturedHandles.first().confirmationHandle.shouldBe(specificHandles.confirmationHandle)
    }
  }
})
