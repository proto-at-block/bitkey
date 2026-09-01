package build.wallet.statemachine.core.list

import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarTitleModel
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Model for a screen that shows a long list of items
 */
data class ListFormBodyModel(
  override val onBack: () -> Unit,
  val toolbarTitle: String,
  val listGroups: ImmutableList<ListGroupModel>,
  override val id: EventTrackerScreenId?,
) : FormBodyModel(
    id = id,
    onBack = onBack,
    toolbar = ToolbarModel(
      leadingAccessory = BackAccessory(onBack),
      title = ToolbarTitleModel.Inline(title = toolbarTitle)
    ),
    header = null,
    mainContentList = listGroups.map {
      FormMainContentModel.ListGroup(it)
    }.toImmutableList(),
    primaryButton = null
  )
