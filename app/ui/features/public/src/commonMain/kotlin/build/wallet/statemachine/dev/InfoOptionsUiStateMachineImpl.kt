package build.wallet.statemachine.dev

import androidx.compose.runtime.*
import build.wallet.account.AccountService
import build.wallet.account.AccountStatus
import build.wallet.account.analytics.AppInstallationDao
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.CloudBackupV3
import build.wallet.cloud.backup.local.CloudBackupDao
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
  private val cloudBackupDao: CloudBackupDao,
) : InfoOptionsUiStateMachine {
  @Composable
  override fun model(props: InfoOptionsProps): ListGroupModel {
    var state by remember { mutableStateOf(State()) }

    LoadAccountId(state) { accountId ->
      state = state.copy(accountId = accountId)
    }

    LoadAppInstallationId(state) { appInstallationId ->
      state = state.copy(appInstallationId = appInstallationId)
    }

    LoadCloudBackupVersion(state) { version ->
      state = state.copy(cloudBackupVersion = version)
    }

    return ListGroupModel(
      style = ListGroupStyle.DIVIDER,
      header = "Identifiers (tap to copy)",
      items = buildInfoItems(state, props)
    )
  }

  @Composable
  private fun LoadAccountId(
    state: State,
    onAccountIdLoaded: (String) -> Unit,
  ) {
    if (state.accountId == null) {
      LaunchedEffect("load-account-id") {
        accountService.accountStatus().first()
          .onSuccess { status ->
            val account =
              when (status) {
                is AccountStatus.ActiveAccount -> status.account
                is AccountStatus.OnboardingAccount -> status.account
                is AccountStatus.LiteAccountUpgradingToFullAccount -> status.onboardingAccount
                AccountStatus.NoAccount -> null
              }
            onAccountIdLoaded(account?.accountId?.serverId ?: NO_ACCOUNT)
          }
      }
    }
  }

  @Composable
  private fun LoadAppInstallationId(
    state: State,
    onAppInstallationIdLoaded: (String) -> Unit,
  ) {
    if (state.appInstallationId == null) {
      LaunchedEffect("load-app-installation") {
        appInstallationDao.getOrCreateAppInstallation()
          .onSuccess { appInstallation ->
            onAppInstallationIdLoaded(appInstallation.localId)
          }
          .logFailure { "Failed to read app installation ID from db" }
      }
    }
  }

  @Composable
  private fun LoadCloudBackupVersion(
    state: State,
    onVersionLoaded: (String) -> Unit,
  ) {
    if (state.cloudBackupVersion == null && state.accountId != null && state.accountId != NO_ACCOUNT) {
      LaunchedEffect("load-cloud-backup-version") {
        cloudBackupDao.get(state.accountId!!)
          .onSuccess { backup ->
            val version = when (backup) {
              is CloudBackupV2 -> "v2"
              is CloudBackupV3 -> "v3"
              null -> "None"
            }
            onVersionLoaded(version)
          }
          .logFailure { "Failed to read cloud backup from dao" }
      }
    }
  }

  private fun buildInfoItems(
    state: State,
    props: InfoOptionsProps,
  ) = listOfNotNull(
    buildAccountIdItem(state),
    buildAppInstallationIdItem(state),
    buildAppVersionItem(),
    buildOsVersionItem(),
    buildCloudBackupVersionItem(state)
  )
    .map { item ->
      item.copy(
        onClick = {
          item.sideText?.let { clipboard.setItem(PlainText(data = it)) }
          props.onPasteboardCopy(item.title)
        }
      )
    }
    .toImmutableList()

  private fun buildAccountIdItem(state: State): ListItemModel? {
    return when (appVariant) {
      AppVariant.Customer -> null
      else ->
        ListItemModel(
          title = "Account ID",
          sideText = state.accountId ?: "..."
        )
    }
  }

  private fun buildAppInstallationIdItem(state: State): ListItemModel {
    return ListItemModel(
      title = "App Installation ID",
      sideText = state.appInstallationId ?: "..."
    )
  }

  private fun buildAppVersionItem(): ListItemModel {
    return ListItemModel(
      title = "App Version",
      sideText = appVersion.value
    )
  }

  private fun buildOsVersionItem(): ListItemModel? {
    return when (appVariant) {
      AppVariant.Customer -> null
      else ->
        ListItemModel(
          title = "OS Version",
          sideText = osVersionInfoProvider.getOsVersion()
        )
    }
  }

  private fun buildCloudBackupVersionItem(state: State): ListItemModel? {
    return when (appVariant) {
      AppVariant.Customer -> null
      else ->
        ListItemModel(
          title = "Cloud Backup Version",
          sideText = state.cloudBackupVersion ?: "..."
        )
    }
  }

  private data class State(
    val accountId: String? = null,
    val appInstallationId: String? = null,
    val cloudBackupVersion: String? = null,
  )

  private companion object {
    private const val NO_ACCOUNT = "N/A"
  }
}
