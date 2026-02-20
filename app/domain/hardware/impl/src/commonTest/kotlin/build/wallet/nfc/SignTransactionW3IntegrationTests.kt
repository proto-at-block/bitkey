package build.wallet.nfc

import build.wallet.Progress
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
import build.wallet.nfc.platform.EmulatedPromptOption
import build.wallet.nfc.platform.HardwareInteraction
import build.wallet.sqldelight.inMemorySqlDriver
import com.github.michaelbull.result.Ok
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
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

    w3CommandsFake = BitkeyW3CommandsFake(w1CommandsFake)

    fakeHardwareKeyStore.clear()
    fakeHardwareStatesDao.clear()
  }

  // ========================================================================
  // End-to-End W3 Flow Tests
  // ========================================================================

  context("W3 complete signing flow") {
    test("full two-tap flow with progress tracking") {
      val session = NfcSessionFake.invoke()

      // First tap: initiate signing
      val firstTapInteraction = w3CommandsFake.signTransaction(
        session = session,
        psbt = PsbtMock,
        spendingKeyset = SpendingKeysetMock
      )

      firstTapInteraction.shouldBeInstanceOf<HardwareInteraction.RequiresTransfer<*>>()

      // Transfer phase with progress tracking
      val progressUpdates = mutableListOf<Progress>()
      val confirmationNeeded = (firstTapInteraction as HardwareInteraction.RequiresTransfer)
        .transferAndFetch(session, w3CommandsFake) { progress ->
          progressUpdates.add(progress)
        }

      // Validate progress reached completion
      // Note: BitkeyW3CommandsFake.signTransaction only calls onProgress once with 1.0f
      // (see BitkeyW3CommandsFake.signTransaction implementation). Real W3 hardware would provide multiple
      // progress updates during chunked PSBT transfer, but the fake provides minimal
      // updates for testing purposes since transfers are instantaneous.
      progressUpdates.isNotEmpty().shouldBe(true)
      progressUpdates.last().value.shouldBe(1.0f)
      // Progress should be monotonically increasing
      progressUpdates.zipWithNext().all { (a, b) -> a.value <= b.value }.shouldBe(true)

      // Should now need confirmation
      confirmationNeeded.shouldBeInstanceOf<HardwareInteraction.ConfirmWithEmulatedPrompt<*>>()

      val emulatedPrompt = confirmationNeeded as HardwareInteraction.ConfirmWithEmulatedPrompt
      emulatedPrompt.options.shouldHaveSize(2)

      emulatedPrompt.options.first { it.name == EmulatedPromptOption.APPROVE }.shouldNotBeNull()
      emulatedPrompt.options.first { it.name == EmulatedPromptOption.DENY }.shouldNotBeNull()

      // Note: Existence is already validated by first() above; if the options
      // didn't exist with these names, first() would throw NoSuchElementException
    }

    test("APPROVE flow structure validation") {
      val session = NfcSessionFake.invoke()

      val firstTap = w3CommandsFake.signTransaction(
        session = session,
        psbt = PsbtMock,
        spendingKeyset = SpendingKeysetMock
      ) as HardwareInteraction.RequiresTransfer

      val emulatedPrompt = firstTap.transferAndFetch(session, w3CommandsFake) { }
        as HardwareInteraction.ConfirmWithEmulatedPrompt

      val approveOption = emulatedPrompt.options.first { it.name == EmulatedPromptOption.APPROVE }

      // APPROVE should have a fetchResult callback for second tap, completing the
      // interaction chain from signTransaction through RequiresTransfer to
      // ConfirmWithEmulatedPrompt. Actual execution would require a signing-capable
      // wallet (SpendingWalletFake doesn't support signPsbt), so we validate the
      // structure rather than executing the full flow.
      approveOption.fetchResult.shouldNotBeNull()

      // APPROVE should not have immediate side effects (onSelect is null)
      val hasImmediateSideEffect = approveOption.onSelect != null
      hasImmediateSideEffect.shouldBe(false)
    }

    test("DENY flow validation") {
      val session = NfcSessionFake.invoke()

      val firstTap = w3CommandsFake.signTransaction(
        session = session,
        psbt = PsbtMock,
        spendingKeyset = SpendingKeysetMock
      ) as HardwareInteraction.RequiresTransfer

      val emulatedPrompt = firstTap.transferAndFetch(session, w3CommandsFake) { }
        as HardwareInteraction.ConfirmWithEmulatedPrompt

      val denyOption = emulatedPrompt.options.first { it.name == EmulatedPromptOption.DENY }

      // DENY should throw NfcException.CommandError when fetchResult is called
      shouldThrow<NfcException.CommandError> {
        denyOption.fetchResult(session, w3CommandsFake)
      }
    }
  }

  // ========================================================================
  // W3 vs W1 Behavior Validation
  // ========================================================================

  context("W3 interaction pattern") {
    test("W3 returns RequiresTransfer (not Completed like W1)") {
      val session = NfcSessionFake.invoke()

      val w3Interaction = w3CommandsFake.signTransaction(
        session = session,
        psbt = PsbtMock,
        spendingKeyset = SpendingKeysetMock
      )

      // W3 should return RequiresTransfer, not Completed
      w3Interaction.shouldBeInstanceOf<HardwareInteraction.RequiresTransfer<*>>()
    }
  }
})
