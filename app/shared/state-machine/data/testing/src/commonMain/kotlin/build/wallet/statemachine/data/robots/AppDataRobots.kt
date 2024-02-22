package build.wallet.statemachine.data.robots

import app.cash.turbine.ReceiveTurbine
import build.wallet.coroutines.turbine.map
import build.wallet.coroutines.turbine.withTypeOf
import build.wallet.statemachine.data.app.AppData
import build.wallet.statemachine.data.app.AppData.AppLoadedData
import build.wallet.statemachine.data.keybox.AccountData.CheckingActiveAccountData
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.CheckingRecoveryOrOnboarding
import io.kotest.matchers.shouldBe

/**
 * Awaits for app data to load.
 */
suspend inline fun ReceiveTurbine<AppData>.awaitKeyboxDataRobot() {
  withTypeOf<AppLoadedData> {
    map({ it.accountData }) {
      awaitItem()
        .shouldBe(CheckingActiveAccountData)

      awaitItem()
        .shouldBe(CheckingRecoveryOrOnboarding)
    }
  }
}
