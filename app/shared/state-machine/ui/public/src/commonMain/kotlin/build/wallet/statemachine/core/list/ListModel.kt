package build.wallet.statemachine.core.list

import build.wallet.ui.model.Model
import build.wallet.ui.model.list.ListGroupModel
import kotlinx.collections.immutable.ImmutableList

/**
 * Model for list UI that has an overall header for the list content
 */
data class ListModel(
  val headerText: String?,
  val sections: ImmutableList<ListGroupModel>,
) : Model()
