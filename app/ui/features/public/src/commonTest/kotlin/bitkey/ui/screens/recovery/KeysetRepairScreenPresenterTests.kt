package bitkey.ui.screens.recovery

import bitkey.ui.framework.test
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.cloud.backup.csek.SealedSsekFake
import build.wallet.cloud.backup.csek.SsekDaoFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.nfc.NfcCommandsMock
import build.wallet.recovery.keyset.KeysetRepairError
import build.wallet.recovery.keyset.KeysetRepairState
import build.wallet.recovery.keyset.PrivateKeysetInfo
import build.wallet.recovery.keyset.SpendingKeysetRepairServiceFake
import build.wallet.recovery.keyset.SpendingKeysetRepairServiceFake.Companion.FakeCachedData
import build.wallet.recovery.sweep.Sweep
import build.wallet.recovery.sweep.SweepPsbt
import build.wallet.recovery.sweep.SweepServiceMock
import build.wallet.recovery.sweep.SweepSignaturePlan
import build.wallet.statemachine.BodyModelMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.auth.RefreshAuthTokensProps
import build.wallet.statemachine.auth.RefreshAuthTokensUiStateMachine
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineFake
import build.wallet.statemachine.recovery.sweep.SweepUiProps
import build.wallet.statemachine.recovery.sweep.SweepUiStateMachine
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitUntilBody
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class KeysetRepairScreenPresenterTests : FunSpec({
  val spendingKeysetRepairService = SpendingKeysetRepairServiceFake()
  val sweepService = SweepServiceMock()
  val ssekDao = SsekDaoFake()
  val nfcCommandsMock = NfcCommandsMock(turbines::create)

  val sweepUiStateMachine = object : SweepUiStateMachine,
    ScreenStateMachineMock<SweepUiProps>("sweep-ui") {}

  val refreshAuthTokensUiStateMachine = object : RefreshAuthTokensUiStateMachine,
    ScreenStateMachineMock<RefreshAuthTokensProps>("refresh-auth-tokens") {}

  val presenter = SpendingKeysetRepairScreenPresenter(
    spendingKeysetRepairService = spendingKeysetRepairService,
    nfcSessionUIStateMachine = NfcSessionUIStateMachineFake(
      nfcCommands = nfcCommandsMock
    ),
    sweepUiStateMachine = sweepUiStateMachine,
    sweepService = sweepService,
    ssekDao = ssekDao,
    refreshAuthTokensUiStateMachine = refreshAuthTokensUiStateMachine
  )

  val screen = KeysetRepairScreen(
    account = FullAccountMock
  )

  beforeTest {
    spendingKeysetRepairService.reset()
    sweepService.reset()
    ssekDao.reset()
  }

  test("shows loading screen when checking private keysets") {
    // Set up checkPrivateKeysets to return None (no unsealing needed)
    spendingKeysetRepairService.checkPrivateKeysetsResult =
      Ok(PrivateKeysetInfo.None(FakeCachedData))

    presenter.test(screen) { _ ->
      // Initial loading state while checking keysets
      awaitBody<LoadingSuccessBodyModel> {
        message.shouldBe("Checking wallet data...")
      }

      // Then shows explanation screen
      awaitUntilBody<ExplanationFormBodyModel> {
        header.shouldNotBeNull().apply {
          headline.shouldBe("Wallet repair needed")
          sublineModel.shouldNotBeNull().string.shouldNotContain("Bitkey device")
        }
      }
    }
  }

  test("shows explanation screen with hardware message when SSEK unsealing needed") {
    spendingKeysetRepairService.checkPrivateKeysetsResult =
      Ok(
        PrivateKeysetInfo.NeedsUnsealing(
          cachedResponseData = FakeCachedData.copy(
            response = FakeCachedData.response.copy(
              wrappedSsek = SealedSsekFake
            )
          )
        )
      )

    presenter.test(screen) { _ ->
      // Explanation screen should mention hardware
      awaitUntilBody<ExplanationFormBodyModel> {
        header.shouldNotBeNull().apply {
          headline.shouldBe("Wallet repair needed")
          sublineModel.shouldNotBeNull().string.shouldContain("Bitkey device")
        }
      }
    }
  }

  test("navigates to repair when continue pressed without hardware") {
    spendingKeysetRepairService.checkPrivateKeysetsResult =
      Ok(PrivateKeysetInfo.None(FakeCachedData))
    spendingKeysetRepairService.attemptRepairResult =
      Ok(KeysetRepairState.RepairComplete(KeyboxMock))
    sweepService.prepareSweepResult = Ok(null) // No sweep needed

    presenter.test(screen) { navigator ->
      // On explanation screen, click continue
      awaitUntilBody<ExplanationFormBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      // Should show repair in progress
      awaitBody<LoadingSuccessBodyModel> {
        message.shouldBe("Repairing wallet...")
      }

      // Should show checking for sweep
      awaitBody<LoadingSuccessBodyModel> {
        message.shouldBe("Checking for funds...")
      }

      // Should show success
      awaitBody<FormBodyModel> {
        header.shouldNotBeNull().headline.shouldBe("Wallet repaired")
        primaryButton.shouldNotBeNull().onClick()
      }

      // Should exit
      navigator.exitCalls.awaitItem()
    }
  }

  test("shows error screen when repair fails") {
    spendingKeysetRepairService.checkPrivateKeysetsResult =
      Ok(PrivateKeysetInfo.None(FakeCachedData))
    spendingKeysetRepairService.attemptRepairResult =
      Err(KeysetRepairError.FetchKeysetsFailed(cause = RuntimeException("Network error")))

    presenter.test(screen) { _ ->
      // On explanation screen, click continue
      awaitUntilBody<ExplanationFormBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      // Should show repair in progress
      awaitBody<LoadingSuccessBodyModel>()

      // Should show error
      awaitBody<FormBodyModel> {
        header.shouldNotBeNull().headline.shouldBe("Repair failed")
        primaryButton.shouldNotBeNull().text.shouldBe("Retry")
      }
    }
  }

  test("shows sweep UI when funds need to be swept") {
    spendingKeysetRepairService.checkPrivateKeysetsResult =
      Ok(PrivateKeysetInfo.None(FakeCachedData))
    spendingKeysetRepairService.attemptRepairResult =
      Ok(KeysetRepairState.RepairComplete(KeyboxMock))
    // Create a sweep with funds that need to be swept
    val sweepPsbt = SweepPsbt(
      psbt = PsbtMock,
      signaturePlan = SweepSignaturePlan.AppAndServer,
      sourceKeyset = SpendingKeysetMock,
      destinationAddress = "bc1qtest"
    )
    sweepService.prepareSweepResult = Ok(Sweep(unsignedPsbts = setOf(sweepPsbt)))

    presenter.test(screen) { _ ->
      // On explanation screen, click continue
      awaitUntilBody<ExplanationFormBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      // Repair in progress
      awaitBody<LoadingSuccessBodyModel>()

      // Checking for sweep
      awaitBody<LoadingSuccessBodyModel>()

      // Sweep UI should be shown (via sweepUiStateMachine mock)
      awaitUntilBody<BodyModelMock<SweepUiProps>>()
    }
  }

  test("cancel button exits flow") {
    spendingKeysetRepairService.checkPrivateKeysetsResult =
      Ok(PrivateKeysetInfo.None(FakeCachedData))

    presenter.test(screen) { navigator ->
      // Skip loading
      awaitBody<LoadingSuccessBodyModel>()

      // On explanation screen, click cancel
      awaitBody<FormBodyModel> {
        secondaryButton.shouldNotBeNull().onClick()
      }

      // Should exit
      navigator.exitCalls.awaitItem()
    }
  }

  test("shows error when checkPrivateKeysets fails") {
    spendingKeysetRepairService.checkPrivateKeysetsResult =
      Err(KeysetRepairError.FetchKeysetsFailed(cause = RuntimeException("Network error")))

    presenter.test(screen) { _ ->
      // Loading
      awaitBody<LoadingSuccessBodyModel>()

      // Error screen
      awaitBody<FormBodyModel> {
        header.shouldNotBeNull().headline.shouldBe("Repair failed")
      }
    }
  }

  test("retry from error screen restarts flow") {
    spendingKeysetRepairService.checkPrivateKeysetsResult =
      Err(KeysetRepairError.FetchKeysetsFailed(cause = RuntimeException("Network error")))

    presenter.test(screen) { _ ->
      // Loading
      awaitBody<LoadingSuccessBodyModel>()

      // Error screen - click retry
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      // Should restart - back to loading
      awaitBody<LoadingSuccessBodyModel> {
        message.shouldBe("Checking wallet data...")
      }

      awaitBody<FormBodyModel>()
    }
  }

  test("shows key regeneration explanation when MissingPrivateKeyForActiveKeyset error") {
    spendingKeysetRepairService.checkPrivateKeysetsResult =
      Ok(PrivateKeysetInfo.None(FakeCachedData))
    spendingKeysetRepairService.attemptRepairResult =
      Err(
        KeysetRepairError.MissingPrivateKeyForActiveKeyset(
          cause = RuntimeException("Missing key"),
          updatedKeybox = KeyboxMock
        )
      )

    presenter.test(screen) { _ ->
      // On explanation screen, click continue
      awaitUntilBody<ExplanationFormBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      // Should show repair in progress
      awaitBody<LoadingSuccessBodyModel> {
        message.shouldBe("Repairing wallet...")
      }

      // Should show key regeneration explanation
      awaitBody<KeyRegenerationExplanationFormBodyModel> {
        header.shouldNotBeNull().apply {
          headline.shouldBe("Recovery Required")
        }
      }
    }
  }

  test("key regeneration explanation cancel exits flow") {
    spendingKeysetRepairService.checkPrivateKeysetsResult =
      Ok(PrivateKeysetInfo.None(FakeCachedData))
    spendingKeysetRepairService.attemptRepairResult =
      Err(
        KeysetRepairError.MissingPrivateKeyForActiveKeyset(
          cause = RuntimeException("Missing key"),
          updatedKeybox = KeyboxMock
        )
      )

    presenter.test(screen) { navigator ->
      // Navigate to key regeneration explanation
      awaitUntilBody<ExplanationFormBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }
      awaitBody<LoadingSuccessBodyModel>()

      // On key regeneration explanation, click cancel
      awaitBody<KeyRegenerationExplanationFormBodyModel> {
        secondaryButton.shouldNotBeNull().onClick()
      }

      // Should exit
      navigator.exitCalls.awaitItem()
    }
  }

  test("key regeneration continue triggers auth token refresh") {
    spendingKeysetRepairService.checkPrivateKeysetsResult =
      Ok(PrivateKeysetInfo.None(FakeCachedData))
    spendingKeysetRepairService.attemptRepairResult =
      Err(
        KeysetRepairError.MissingPrivateKeyForActiveKeyset(
          cause = RuntimeException("Missing key"),
          updatedKeybox = KeyboxMock
        )
      )

    presenter.test(screen) { _ ->
      // Navigate to key regeneration explanation
      awaitUntilBody<ExplanationFormBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }
      awaitBody<LoadingSuccessBodyModel>()

      // On key regeneration explanation, click continue
      awaitBody<KeyRegenerationExplanationFormBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      // Should show refresh auth tokens UI (via mock)
      awaitUntilBody<BodyModelMock<RefreshAuthTokensProps>> {
        latestProps.fullAccountId.shouldBe(FullAccountMock.accountId)
      }
    }
  }

  test("auth token refresh back returns to key regeneration explanation") {
    spendingKeysetRepairService.checkPrivateKeysetsResult =
      Ok(PrivateKeysetInfo.None(FakeCachedData))
    spendingKeysetRepairService.attemptRepairResult =
      Err(
        KeysetRepairError.MissingPrivateKeyForActiveKeyset(
          cause = RuntimeException("Missing key"),
          updatedKeybox = KeyboxMock
        )
      )

    presenter.test(screen) { _ ->
      // Navigate to auth token refresh
      awaitUntilBody<ExplanationFormBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }
      awaitBody<LoadingSuccessBodyModel>()
      awaitBody<KeyRegenerationExplanationFormBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      // On refresh auth tokens, verify props and click back
      awaitUntilBody<BodyModelMock<RefreshAuthTokensProps>> {
        latestProps.fullAccountId.shouldBe(FullAccountMock.accountId)
        latestProps.onBack()
      }

      // Should go back to key regeneration explanation
      awaitUntilBody<KeyRegenerationExplanationFormBodyModel>()
    }
  }
})
