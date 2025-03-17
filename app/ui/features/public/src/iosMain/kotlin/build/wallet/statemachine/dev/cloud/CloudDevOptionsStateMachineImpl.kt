package build.wallet.statemachine.dev.cloud

import androidx.compose.runtime.*
import build.wallet.cloud.store.iCloudAccountRepository
import build.wallet.compose.collections.immutableListOf
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.dev.DebugMenuBodyModel
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemModel
import com.github.michaelbull.result.get

/**
 * iOS implementation of [CloudDevOptionsStateMachine].
 *
 * Allows to view iCloud account information and status.
 */

@BitkeyInject(ActivityScope::class)
class CloudDevOptionsStateMachineImpl(
  private val iCloudAccountRepository: iCloudAccountRepository,
) : CloudDevOptionsStateMachine {
  @Composable
  override fun model(props: CloudDevOptionsProps): BodyModel {
    val iCloudAccount = iCloudAccountRepository.currentAccount().get()

    var ubiquityContainerPath: String? by remember { mutableStateOf("Fetching…") }

    LaunchedEffect("ubiquity-container-path") {
      ubiquityContainerPath = iCloudAccountRepository
        .currentUbiquityContainerPath()
        .get() ?: "None"
    }

    return DebugMenuBodyModel(
      title = "Cloud Storage",
      onBack = props.onExit,
      groups = immutableListOf(
        ListGroupModel(
          header = "iCloud Account",
          items = immutableListOf(
            ListItemModel(
              title = "Ubiquity Identity Token",
              secondaryText = "Unique iCloud account identity token.",
              sideText = iCloudAccount?.ubiquityIdentityToken.toString()
            ),
            ListItemModel(
              title = "Ubiquity Container Path",
              secondaryText = "iCloud Drive filesystem location.",
              sideText = ubiquityContainerPath
            )
          ),
          style = ListGroupStyle.CARD_GROUP
        )
      )
    )
  }
}
