package build.wallet.statemachine.ui.robots

import build.wallet.statemachine.settings.SettingsBodyModel

fun SettingsBodyModel.clickMobilePay() {
  clickRow("Mobile Pay")
}

fun SettingsBodyModel.clickTransferSettings() {
  clickRow("Transfers")
}

fun SettingsBodyModel.clickExportTools() {
  clickRow("Exports")
}

private fun SettingsBodyModel.clickRow(title: String) {
  sectionModels
    .flatMap { it.rowModels }
    .single { it.title == title }
    .onClick()
}
