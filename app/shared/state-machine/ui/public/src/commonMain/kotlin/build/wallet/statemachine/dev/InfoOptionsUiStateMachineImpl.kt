package build.wallet.statemachine.dev

import androidx.compose.runtime.*
import build.wallet.account.AccountService
import build.wallet.account.AccountStatus
import build.wallet.account.analytics.AppInstallationDao
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import build.wallet.platform.clipboard.ClipItem.PlainText
import build.wallet.platform.clipboard.Clipboard
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVersion
import build.wallet.platform.versions.OsVersionInfoProvider
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemModel
import com.github.michaelbull.result.onSuccess
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first

@BitkeyInject(ActivityScope::class)
class InfoOptionsUiStateMachineImpl(
  private val accountService: AccountService,
  private val appInstallationDao: AppInstallationDao,
  private val appVariant: AppVariant,
  private val appVersion: AppVersion,
  private val osVersionInfoProvider: OsVersionInfoProvider,
  private val clipboard: Clipboard,
) : InfoOptionsUiStateMachine {
  @Composable
  override fun model(props: Unit): ListGroupModel {
    var state by remember { mutableStateOf(State()) }

    if (state.accountId == null) {
      LaunchedEffect("load-account-id") {
        accountService.accountStatus().first()
          .onSuccess { status ->
            val account =
              when (status) {
                is AccountStatus.ActiveAccount -> status.account
                is AccountStatus.OnboardingAccount -> status.account
                is AccountStatus.LiteAccountUpgradingToFullAccount -> status.account
                AccountStatus.NoAccount -> null
              }
            state = state.copy(accountId = account?.accountId?.serverId ?: "N/A")
          }
      }
    }

    if (state.appInstallationId == null) {
      LaunchedEffect("load-app-installation") {
        appInstallationDao.getOrCreateAppInstallation()
          .onSuccess { appInstallation ->
            state = state.copy(appInstallationId = appInstallation.localId)
          }
          .logFailure { "Failed to read app installation ID from db" }
      }
    }

    return ListGroupModel(
      style = ListGroupStyle.DIVIDER,
      items =
        listOfNotNull(
          // Don't show Account ID in Customer build
          when (appVariant) {
            AppVariant.Customer -> null
            else ->
              ListItemModel(
                title = "Account ID",
                sideText = state.accountId ?: "..."
              )
          },
          ListItemModel(
            title = "App Installation ID",
            sideText = state.appInstallationId ?: "..."
          ),
          ListItemModel(
            title = "App Version",
            sideText = appVersion.value
          ),
          // Don't show OS Version in Customer build
          when (appVariant) {
            AppVariant.Customer -> null
            else ->
              ListItemModel(
                title = "OS Version",
                sideText = osVersionInfoProvider.getOsVersion()
              )
          }
        )
          .map { item ->
            item.copy(
              onClick = {
                item.sideText?.let { clipboard.setItem(PlainText(data = it)) }
              }
            )
          }
          .toImmutableList()
    )
  }

  private data class State(
    val accountId: String? = null,
    val appInstallationId: String? = null,
  )
}
