package build.wallet.statemachine.dev.cloud

import androidx.compose.runtime.Composable
import build.wallet.cloud.store.iCloudAccountRepository
import build.wallet.compose.collections.immutableListOf
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
class CloudDevOptionsStateMachineImpl(
  private val iCloudAccountRepository: iCloudAccountRepository,
) : CloudDevOptionsStateMachine {
  @Composable
  override fun model(props: CloudDevOptionsProps): BodyModel {
    val iCloudAccount = iCloudAccountRepository.currentAccount().get()

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
            )
          ),
          style = ListGroupStyle.CARD_GROUP
        )
      )
    )
  }
}
