package bitkey.ui.screens.securityhub

import app.cash.turbine.ReceiveTurbine
import bitkey.securitycenter.DelayNotifyConfigurationServiceFake
import bitkey.ui.framework.test
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.ktor.result.HttpError
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.auth.ActionProofType
import build.wallet.statemachine.auth.HardwareAuthUiProps
import build.wallet.statemachine.auth.HardwareAuthUiStateMachine
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class DelayNotifyPeriodPresenterTests : FunSpec({
  fun testPresenter(
    initialPeriodDays: Int? = 14,
    delayNotifyConfigurationService: DelayNotifyConfigurationServiceFake =
      DelayNotifyConfigurationServiceFake(),
  ): Pair<DelayNotifyPeriodPresenter, DelayNotifyConfigurationServiceFake> {
    delayNotifyConfigurationService.periodDays.value = initialPeriodDays
    return DelayNotifyPeriodPresenter(
      delayNotifyConfigurationService = delayNotifyConfigurationService,
      hardwareAuthUiStateMachine = object : HardwareAuthUiStateMachine,
        ScreenStateMachineMock<HardwareAuthUiProps>(id = "hardware-auth") {}
    ) to delayNotifyConfigurationService
  }

  test("shows loading while current period is unknown") {
    val (presenter, delayNotifyConfigurationService) = testPresenter(initialPeriodDays = null)

    presenter.test(DelayNotifyPeriodScreen(account = FullAccountMock, origin = null)) {
      awaitUntil {
        it.body !is FormBodyModel
      }

      delayNotifyConfigurationService.periodDays.value = 14

      awaitSecurityPeriodForm {
        currentPeriodItem().sideText.shouldBe("14 days")
      }
    }
  }

  test("shows current period and marks it in the picker") {
    val (presenter, _) = testPresenter(initialPeriodDays = 14)

    presenter.test(DelayNotifyPeriodScreen(account = FullAccountMock, origin = null)) {
      awaitSecurityPeriodForm {
        header.shouldNotBeNull().headline.shouldBe("Security waiting period")
        currentPeriodItem().sideText.shouldBe("14 days")
        currentPeriodItem().onClick!!.invoke()
      }

      val sheetBody = awaitPeriodSheet()
        .bottomSheetModel
        .shouldNotBeNull()
        .body
        .shouldBeInstanceOf<FormBodyModel>()
      val options = sheetBody.periodOptions()
      options.map { it.title }.shouldContainExactly("7 days", "14 days", "30 days")
      options.single { it.title == "14 days" }
        .trailingAccessory
        .shouldBeInstanceOf<ListItemAccessory.IconAccessory>()
    }
  }

  test("successful update requests hardware proof, updates configuration, and shows updated period") {
    val proof = PrivilegedActionProof.HwKeyProof(HwFactorProofOfPossession("signed-token"))
    val (presenter, delayNotifyConfigurationService) = testPresenter(initialPeriodDays = 14)

    presenter.test(DelayNotifyPeriodScreen(account = FullAccountMock, origin = null)) {
      awaitSecurityPeriodForm {
        currentPeriodItem().onClick!!.invoke()
      }

      awaitPeriodSheet().selectPeriod(30)

      awaitBodyMock<HardwareAuthUiProps>(id = "hardware-auth") {
        fullAccountId.shouldBe(FullAccountMock.accountId)
        actionProofType.shouldBe(ActionProofType.SetDelayNotifyPeriod(periodDays = 30))
        actionDescription.shouldBe("Setting delay notify period to 30 days")
        screenPresentationStyle.shouldBe(ScreenPresentationStyle.Modal)
        onSuccess(proof)
      }

      awaitUntilBody<FormBodyModel>(
        matching = { it.currentPeriodSideTextOrNull() == "30 days" }
      )
    }

    delayNotifyConfigurationService.setCalls.shouldHaveSize(1)
    delayNotifyConfigurationService.setCalls.single().delayPeriodDays.shouldBe(30)
    delayNotifyConfigurationService.setCalls.single().proof.shouldBe(proof)
  }

  test("failed update shows retry and retries with a new hardware proof") {
    val firstProof = PrivilegedActionProof.HwKeyProof(HwFactorProofOfPossession("first-proof"))
    val retryProof = PrivilegedActionProof.HwKeyProof(HwFactorProofOfPossession("retry-proof"))
    val failingThenSuccessfulService = DelayNotifyConfigurationServiceFake().apply {
      setResults = mutableListOf(
        Err(HttpError.NetworkError(Throwable("network error"))),
        Ok(30)
      )
    }
    val (presenter, delayNotifyConfigurationService) = testPresenter(
      initialPeriodDays = 14,
      delayNotifyConfigurationService = failingThenSuccessfulService
    )

    presenter.test(DelayNotifyPeriodScreen(account = FullAccountMock, origin = null)) {
      awaitSecurityPeriodForm {
        currentPeriodItem().onClick!!.invoke()
      }

      awaitPeriodSheet().selectPeriod(30)

      awaitBodyMock<HardwareAuthUiProps>(id = "hardware-auth") {
        actionProofType.shouldBe(ActionProofType.SetDelayNotifyPeriod(periodDays = 30))
        onSuccess(firstProof)
      }

      awaitUntilBody<FormBodyModel>(
        matching = { it.header?.headline == "We couldn't update your security waiting period" }
      ) {
        currentPeriodSideTextOrNull().shouldBe(null)
        primaryButton.shouldNotBeNull().text.shouldBe("Retry")
        primaryButton!!.onClick.invoke()
      }

      awaitBodyMock<HardwareAuthUiProps>(id = "hardware-auth") {
        actionProofType.shouldBe(ActionProofType.SetDelayNotifyPeriod(periodDays = 30))
        onSuccess(retryProof)
      }

      awaitUntilBody<FormBodyModel>(
        matching = { it.currentPeriodSideTextOrNull() == "30 days" }
      )
    }

    delayNotifyConfigurationService.setCalls.shouldHaveSize(2)
    delayNotifyConfigurationService.setCalls[0].proof.shouldBe(firstProof)
    delayNotifyConfigurationService.setCalls[1].proof.shouldBe(retryProof)
  }
})

private fun FormBodyModel.currentPeriodItem(): ListItemModel =
  mainContentList
    .filterIsInstance<FormMainContentModel.ListGroup>()
    .flatMap { it.listGroupModel.items }
    .single { it.title == "Current period" }

private fun FormBodyModel.currentPeriodSideTextOrNull(): String? =
  mainContentList
    .filterIsInstance<FormMainContentModel.ListGroup>()
    .flatMap { it.listGroupModel.items }
    .firstOrNull { it.title == "Current period" }
    ?.sideText

private fun FormBodyModel.periodOptions(): List<ListItemModel> =
  mainContentList
    .filterIsInstance<FormMainContentModel.ListGroup>()
    .flatMap { it.listGroupModel.items }

private fun ScreenModel.selectPeriod(days: Int) {
  val sheetBody = bottomSheetModel
    .shouldNotBeNull()
    .body
    .shouldBeInstanceOf<FormBodyModel>()
  sheetBody
    .periodOptions()
    .single { it.title == "$days days" }
    .onClick!!
    .invoke()
}

private suspend fun ReceiveTurbine<ScreenModel>.awaitPeriodSheet(): ScreenModel =
  awaitUntil { it.bottomSheetModel != null }

private suspend fun ReceiveTurbine<ScreenModel>.awaitSecurityPeriodForm(
  assertions: FormBodyModel.() -> Unit,
) {
  awaitUntilBody<FormBodyModel>(
    matching = { it.header?.headline == "Security waiting period" },
    validate = assertions
  )
}
