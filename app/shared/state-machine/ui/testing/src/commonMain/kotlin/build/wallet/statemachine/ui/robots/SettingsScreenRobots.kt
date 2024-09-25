package build.wallet.statemachine.ui.robots

import build.wallet.statemachine.settings.SettingsBodyModel

fun SettingsBodyModel.clickMobilePay() {
  clickRow("Mobile Pay")
}

fun SettingsBodyModel.clickTransferSettings() {
  clickRow("Transfer settings")
}

private fun SettingsBodyModel.clickRow(title: String) {
  sectionModels
    .flatMap { it.rowModels }
    .single { it.title == title }
    .onClick()
}
