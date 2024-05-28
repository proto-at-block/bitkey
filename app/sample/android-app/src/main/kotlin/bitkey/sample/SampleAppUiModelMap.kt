package bitkey.sample

import bitkey.sample.ui.core.ErrorScreen
import bitkey.sample.ui.core.LoadingScreen
import bitkey.sample.ui.error.ErrorBodyModel
import bitkey.sample.ui.home.AccountHomeBodyModel
import bitkey.sample.ui.home.AccountHomeScreen
import bitkey.sample.ui.model.LoadingBodyModel
import bitkey.sample.ui.onboarding.AccountCreatedBodyModel
import bitkey.sample.ui.onboarding.AccountCreatedScreen
import bitkey.sample.ui.onboarding.RequestAccountNameBodyModel
import bitkey.sample.ui.onboarding.RequestAccountNameScreen
import bitkey.sample.ui.onboarding.WelcomeBodyModel
import bitkey.sample.ui.onboarding.WelcomeScreen
import bitkey.sample.ui.settings.SettingsListBodyModel
import bitkey.sample.ui.settings.SettingsListScreen
import bitkey.sample.ui.settings.account.AccountSettingsBodyModel
import bitkey.sample.ui.settings.account.AccountSettingsScreen
import build.wallet.ui.model.TypedUiModelMap
import build.wallet.ui.model.UiModel
import build.wallet.ui.model.UiModelMap

internal object SampleAppUiModelMap : UiModelMap by TypedUiModelMap(
  UiModel<WelcomeBodyModel> { WelcomeScreen(it) },
  UiModel<AccountCreatedBodyModel> { AccountCreatedScreen(it) },
  UiModel<LoadingBodyModel> { LoadingScreen(it) },
  UiModel<AccountHomeBodyModel> { AccountHomeScreen(it) },
  UiModel<RequestAccountNameBodyModel> { RequestAccountNameScreen(it) },
  UiModel<SettingsListBodyModel> { SettingsListScreen(it) },
  UiModel<AccountSettingsBodyModel> { AccountSettingsScreen(it) },
  UiModel<ErrorBodyModel> { ErrorScreen(it) }
)
