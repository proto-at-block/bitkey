package build.wallet.ui.components.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.compose.collections.immutableListOf
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListGroupStyle.NONE
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun ListSectionForPreview(
  showHeader: Boolean = true,
  style: ListGroupStyle = NONE,
  collapsed: Boolean = false,
  explainerSubtext: String? = null,
) {
  PreviewWalletTheme {
    Box(modifier = Modifier.background(WalletTheme.colors.foreground10)) {
      ListGroup(
        collapseContent = collapsed,
        model =
          ListGroupModel(
            header = "Header".takeIf { showHeader },
            items =
              immutableListOf(
                ListItemModel(
                  title = "Title 1"
                ),
                ListItemModel(
                  title = "Title 2",
                  secondaryText = "Secondary text 2"
                ),
                ListItemModel(
                  title = "Title 3",
                  sideText = "Side text 3"
                )
              ),
            style = style,
            explainerSubtext = explainerSubtext
          )
      )
    }
  }
}
