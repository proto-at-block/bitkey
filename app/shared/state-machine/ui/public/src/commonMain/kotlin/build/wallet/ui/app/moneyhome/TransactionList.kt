package build.wallet.ui.app.moneyhome

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import build.wallet.statemachine.core.list.ListModel
import build.wallet.ui.components.list.ListGroup
import build.wallet.ui.components.list.ListHeader

@Composable
fun TransactionList(
  modifier: Modifier = Modifier,
  model: ListModel,
  hideValue: Boolean = false,
) {
  Column(modifier = modifier) {
    model.headerText?.let {
      ListHeader(title = it)
    }
    model.sections.forEach { section ->
      ListGroup(
        model = section,
        collapseContent = hideValue
      )
    }
  }
}
