package build.wallet.statemachine.data.robots

import app.cash.turbine.ReceiveTurbine
import build.wallet.statemachine.data.app.AppData
import build.wallet.statemachine.data.app.AppData.LoadingAppData
import io.kotest.matchers.shouldBe

/**
 * Awaits for keybox data to load.
 */
suspend inline fun ReceiveTurbine<AppData>.awaitAppLoadingDataRobot() {
  awaitItem()
    .shouldBe(LoadingAppData)

  awaitKeyboxDataRobot()
}
