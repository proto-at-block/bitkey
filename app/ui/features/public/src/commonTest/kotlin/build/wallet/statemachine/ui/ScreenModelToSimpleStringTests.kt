package build.wallet.statemachine.ui

import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.walletmigration.W3UpgradeCompleteSheetModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain

/**
 * The `Previous models` diagnostic printed by the `awaitUntil*` helpers is often the only
 * evidence available for a CI-only state machine failure. It must name the concrete sheet body
 * type, otherwise every bottom sheet looks identical ("SheetModel") and the two most common
 * explanations for a stalled flow cannot be told apart.
 */
class ScreenModelToSimpleStringTests : FunSpec({
  val body = LoadingSuccessBodyModel(
    state = LoadingSuccessBodyModel.State.Loading,
    id = null
  )

  test("bottom sheet is described by its concrete body type, not just 'SheetModel'") {
    val screen = ScreenModel(
      body = body,
      bottomSheetModel = W3UpgradeCompleteSheetModel(onDone = {})
    )

    screen.toSimpleString().shouldContain("W3UpgradeCompleteSheetBodyModel")
  }

  test("screen without a bottom sheet still describes its body") {
    ScreenModel(body = body).toSimpleString()
      .shouldContain("LoadingSuccessBodyModel")
  }
})
