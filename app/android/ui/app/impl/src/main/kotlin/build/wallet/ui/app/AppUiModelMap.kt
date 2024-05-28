package build.wallet.ui.app

import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.cloud.health.CloudBackupHealthDashboardBodyModel
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.SplashBodyModel
import build.wallet.statemachine.core.TabBarModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.demo.DemoModeConfigBodyModel
import build.wallet.statemachine.dev.DebugMenuBodyModel
import build.wallet.statemachine.dev.FirmwareMetadataBodyModel
import build.wallet.statemachine.dev.analytics.AnalyticsBodyModel
import build.wallet.statemachine.dev.featureFlags.FeatureFlagsBodyModel
import build.wallet.statemachine.dev.logs.LogsBodyModel
import build.wallet.statemachine.education.EducationBodyModel
import build.wallet.statemachine.fwup.FwupNfcBodyModel
import build.wallet.statemachine.limit.picker.SpendingLimitPickerModel
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.moneyhome.lite.LiteMoneyHomeBodyModel
import build.wallet.statemachine.nfc.FwupInstructionsBodyModel
import build.wallet.statemachine.nfc.NfcBodyModel
import build.wallet.statemachine.partnerships.purchase.CustomAmountBodyModel
import build.wallet.statemachine.platform.permissions.AskingToGoToSystemBodyModel
import build.wallet.statemachine.platform.permissions.RequestPermissionBodyModel
import build.wallet.statemachine.receive.AddressQrCodeBodyModel
import build.wallet.statemachine.recovery.inprogress.waiting.AppDelayNotifyInProgressBodyModel
import build.wallet.statemachine.send.QrCodeScanBodyModel
import build.wallet.statemachine.send.TransferAmountBodyModel
import build.wallet.statemachine.settings.SettingsBodyModel
import build.wallet.statemachine.settings.full.electrum.CustomElectrumServerBodyModel
import build.wallet.statemachine.settings.full.mobilepay.MobilePayStatusModel
import build.wallet.ui.app.account.ChooseAccountAccessScreen
import build.wallet.ui.app.account.create.hardware.PairNewHardwareScreen
import build.wallet.ui.app.backup.health.CloudBackupHealthDashboardScreen
import build.wallet.ui.app.core.InAppBrowserScreen
import build.wallet.ui.app.core.LoadingSuccessScreen
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.app.demo.DemoModeConfigScreen
import build.wallet.ui.app.dev.DebugMenuScreen
import build.wallet.ui.app.dev.FirmwareMetadataScreen
import build.wallet.ui.app.dev.analytics.AnalyticsScreen
import build.wallet.ui.app.dev.flags.FeatureFlagsScreen
import build.wallet.ui.app.dev.logs.LogsScreen
import build.wallet.ui.app.education.EducationScreen
import build.wallet.ui.app.limit.SpendingLimitPickerScreen
import build.wallet.ui.app.loading.SplashScreen
import build.wallet.ui.app.mobilepay.MobilePayStatusScreen
import build.wallet.ui.app.moneyhome.LiteMoneyHomeScreen
import build.wallet.ui.app.moneyhome.MoneyHomeScreen
import build.wallet.ui.app.moneyhome.receive.AddressQrCodeScreen
import build.wallet.ui.app.nfc.FwupInstructionsScreen
import build.wallet.ui.app.nfc.FwupNfcScreen
import build.wallet.ui.app.nfc.NfcScreen
import build.wallet.ui.app.partnerships.CustomAmountScreen
import build.wallet.ui.app.platform.permissions.AskingToGoToSystemScreen
import build.wallet.ui.app.platform.permissions.RequestPermissionScreen
import build.wallet.ui.app.qrcode.QrCodeScanScreen
import build.wallet.ui.app.recovery.AppDelayNotifyInProgressScreen
import build.wallet.ui.app.send.TransferAmountScreen
import build.wallet.ui.app.settings.SettingsScreen
import build.wallet.ui.app.settings.electrum.CustomElectrumServerScreen
import build.wallet.ui.components.status.StatusBanner
import build.wallet.ui.components.tabbar.TabBar
import build.wallet.ui.model.TypedUiModelMap
import build.wallet.ui.model.UiModel
import build.wallet.ui.model.UiModelMap
import build.wallet.ui.model.status.StatusBannerModel

/**
 * [UiModelMap] that provides [UiModel]s for all of the app's models (screens, system components,
 * sheets, etc).
 */
object AppUiModelMap : UiModelMap by TypedUiModelMap(
  // Screens
  UiModel<AnalyticsBodyModel> { AnalyticsScreen(it) },
  UiModel<ChooseAccountAccessModel> { ChooseAccountAccessScreen(it) },
  UiModel<LoadingSuccessBodyModel> { LoadingSuccessScreen(it) },
  UiModel<MoneyHomeBodyModel> { MoneyHomeScreen(it) },
  UiModel<LiteMoneyHomeBodyModel> { LiteMoneyHomeScreen(it) },
  UiModel<NfcBodyModel> { NfcScreen(it) },
  UiModel<AddressQrCodeBodyModel> { AddressQrCodeScreen(it) },
  UiModel<FormBodyModel> { FormScreen(it) },
  UiModel<SettingsBodyModel> { SettingsScreen(it) },
  UiModel<SplashBodyModel> { SplashScreen(it) },
  UiModel<PairNewHardwareBodyModel> { PairNewHardwareScreen(it) },
  UiModel<FwupInstructionsBodyModel> { FwupInstructionsScreen(it) },
  UiModel<TransferAmountBodyModel> { TransferAmountScreen(it) },
  UiModel<MobilePayStatusModel> { MobilePayStatusScreen(it) },
  UiModel<CustomElectrumServerBodyModel> { CustomElectrumServerScreen(it) },
  UiModel<SpendingLimitPickerModel> { SpendingLimitPickerScreen(it) },
  UiModel<AppDelayNotifyInProgressBodyModel> { AppDelayNotifyInProgressScreen(it) },
  UiModel<QrCodeScanBodyModel> { QrCodeScanScreen(it) },
  UiModel<AskingToGoToSystemBodyModel> { AskingToGoToSystemScreen(it) },
  UiModel<RequestPermissionBodyModel> { RequestPermissionScreen(it) },
  UiModel<FwupNfcBodyModel> { FwupNfcScreen(it) },
  UiModel<InAppBrowserModel> { InAppBrowserScreen(it) },
  UiModel<EducationBodyModel> { EducationScreen(it) },
  UiModel<CustomAmountBodyModel> { CustomAmountScreen(it) },
  UiModel<CloudBackupHealthDashboardBodyModel> { CloudBackupHealthDashboardScreen(it) },
  UiModel<DemoModeConfigBodyModel> { DemoModeConfigScreen(it) },
  // Components
  UiModel<StatusBannerModel> { StatusBanner(it) },
  UiModel<TabBarModel> { TabBar(it) },
  // Dev
  UiModel<DebugMenuBodyModel> { DebugMenuScreen(it) },
  UiModel<FeatureFlagsBodyModel> { FeatureFlagsScreen(it) },
  UiModel<FirmwareMetadataBodyModel> { FirmwareMetadataScreen(it) },
  UiModel<LogsBodyModel> { LogsScreen(it) }
)
