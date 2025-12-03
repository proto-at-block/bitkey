package build.wallet.ui.app.moneyhome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import build.wallet.statemachine.core.list.ListModel
import build.wallet.ui.components.list.ListGroup
import build.wallet.ui.components.list.ListHeader
import build.wallet.ui.theme.WalletTheme

@Composable
fun TransactionList(
  modifier: Modifier = Modifier,
  model: ListModel,
  hideValue: Boolean = false,
) {
  Column(
    modifier = modifier.background(WalletTheme.colors.background)
  ) {
    model.headerText?.let {
      ListHeader(title = it)
    }
    Column {
      model.sections.forEach { section ->
        ListGroup(
          model = section,
          collapseContent = hideValue
        )
      }
    }
  }
}
