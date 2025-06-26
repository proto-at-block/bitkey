package build.wallet.statemachine.status

import build.wallet.analytics.events.screen.id.AppFunctionalityEventTrackerScreenId
import build.wallet.availability.*
import build.wallet.availability.FunctionalityFeatureStates.FeatureState.*
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.Instant

data class AppFunctionalityStatusBodyModel(
  val status: AppFunctionalityStatus.LimitedFunctionality,
  val cause: ConnectivityCause,
  val dateFormatter: (Instant) -> String,
  val onClose: () -> Unit,
) : FormBodyModel(
    id = AppFunctionalityEventTrackerScreenId.APP_FUNCTIONALITY_STATUS,
    onBack = onClose,
    toolbar = ToolbarModel(leadingAccessory = CloseAccessory(onClose)),
    header = FormHeaderModel(
      icon = Icon.LargeIconNetworkError,
      headline = when (cause) {
        is F8eUnreachable -> "We’re having trouble reaching Bitkey services."
        is InternetUnreachable -> "It looks like you’re offline."
        EmergencyExitMode -> "You're in Emergency Exit Mode."
      },
      subline = when (cause) {
        is F8eUnreachable -> "Some features may not be available:"
        is InternetUnreachable -> "Some functionality may not be available until you’re connected to the internet:"
        EmergencyExitMode -> "Some features may not be available."
      }
    ),
    mainContentList = immutableListOf(
      FormMainContentModel.ListGroup(
        listGroupModel = ListGroupModel(
          style = ListGroupStyle.CARD_GROUP,
          items = status.featureStates.listItemModels(
            dateFormatter = dateFormatter
          )
        )
      )
    ),
    primaryButton = null
  )

fun FunctionalityFeatureStates.listItemModels(
  dateFormatter: (Instant) -> String,
): ImmutableList<ListItemModel> {
  // first step: Create our list of ListItemModels and group them by their availability
  // this creates a map of "Available", "Unavailable", and "OutOfDate" to a list of ListItemModels
  val groupedByAvailability =
    listOf(
      receive.listItemModel("Receive", dateFormatter),
      fiatExchangeRates.listItemModel("Currency Rates", dateFormatter),
      send.listItemModel("Send", dateFormatter),
      mobilePay.listItemModel("Transfer without hardware", dateFormatter),
      customElectrumServer.listItemModel("Custom Electrum Server", dateFormatter),
      securityAndRecovery.listItemModel("Recover Lost Keys", dateFormatter)
    ).groupBy {
      when (it.secondaryText) {
        "Available" -> "Available"
        "Unavailable" -> "Unavailable"
        else -> "OutOfDate"
      }
    }

  // second step: Sort each list of ListItemModels by their title alphabetically
  val sortedGroups = groupedByAvailability.mapValues { (_, items) -> items.sortedBy { it.title } }

  // third step: Flatten the map of lists into a single list of ListItemModels and return it
  return sortedGroups.values.flatten().toImmutableList()
}

private fun FunctionalityFeatureStates.FeatureState.listItemModel(
  title: String,
  dateFormatter: (Instant) -> String,
): ListItemModel {
  fun listItemStatusAccessory(
    icon: Icon,
    iconTint: IconTint,
  ) = ListItemAccessory.IconAccessory(
    model =
      IconModel(
        iconImage = IconImage.LocalImage(icon),
        iconSize = IconSize.Small,
        iconTint = iconTint
      )
  )

  return ListItemModel(
    title = title,
    secondaryText =
      when (this) {
        is Available -> "Available"
        is Unavailable -> "Unavailable"
        is OutOfDate -> lastUpdated?.let { "Last updated: ${dateFormatter(it)}" }
      },
    trailingAccessory =
      when (this) {
        is Available -> listItemStatusAccessory(Icon.SmallIconCheckFilled, IconTint.Green)
        is Unavailable -> listItemStatusAccessory(Icon.SmallIconMinusFilled, IconTint.Destructive)
        is OutOfDate -> listItemStatusAccessory(Icon.SmallIconWarningFilled, IconTint.OutOfDate)
      }
  )
}
