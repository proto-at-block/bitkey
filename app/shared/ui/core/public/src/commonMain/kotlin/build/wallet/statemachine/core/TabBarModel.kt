package build.wallet.statemachine.core

import build.wallet.ui.model.Model

data class TabBarModel(
  val isShown: Boolean,
  val firstItem: TabBarItem,
  val secondItem: TabBarItem,
) : Model()

data class TabBarItem(
  val icon: Icon,
  val selected: Boolean,
  val onClick: () -> Unit,
  val testTag: String? = null,
)
