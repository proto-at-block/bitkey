package build.wallet.bitkey.keybox

import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.spending.SpendingKeyset

/**
 *  Keys representing the App Key Bundle and the Keys along the Spending Factor
 */
sealed interface KeyCrossDraft {
  val config: KeyboxConfig

  //
  //                Serv      App      HW
  //             ━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  //             ┃        ┃   XX   ┃        ┃
  //      Auth   ┃        ┃   XX   ┃        ┃
  //             ┃━━━━━━━━━━━━━━━━━━━━━━━━━━┃
  //             ┃   //   ┃   XX   ┃   //   ┃
  //     Spend   ┃   //   ┃   XX   ┃   //   ┃
  //             ━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  data class WithAppKeys(
    val appKeyBundle: AppKeyBundle,
    override val config: KeyboxConfig,
  ) : KeyCrossDraft

  //                Serv      App      HW
  //             ┃━━━━━━━━━━━━━━━━━━━━━━━━━━┃
  //             ┃   //   ┃   XX   ┃   XX   ┃
  //      Auth   ┃   //   ┃   XX   ┃   XX   ┃
  //             ┃━━━━━━━━━━━━━━━━━━━━━━━━━━┃
  //             ┃   //   ┃   XX   ┃   XX   ┃
  //     Spend   ┃   //   ┃   XX   ┃   XX   ┃
  //             ━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  data class WithAppKeysAndHardwareKeys(
    val appKeyBundle: AppKeyBundle,
    val hardwareKeyBundle: HwKeyBundle,
    override val config: KeyboxConfig,
  ) : KeyCrossDraft

  //                Serv      App      HW
  //             ┃━━━━━━━━━━━━━━━━━━━━━━━━━━┃
  //             ┃        ┃   XX   ┃   XX   ┃
  //      Auth   ┃        ┃   XX   ┃   XX   ┃
  //             ┃━━━━━━━━━━━━━━━━━━━━━━━━━━┃
  //             ┃   XX   ┃   XX   ┃   XX   ┃
  //     Spend   ┃   XX   ┃   XX   ┃   XX   ┃
  //             ━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  data class CompleteKeyCross(
    override val config: KeyboxConfig,
    val appKeyBundle: AppKeyBundle,
    val hardwareKeyBundle: HwKeyBundle,
    val spendingKeyset: SpendingKeyset,
  ) : KeyCrossDraft
}
