@file:JvmName("DataRowGroupKt")

package build.wallet.ui.data

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.form.FormMainContentModel.DataList
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.layout.Divider
import build.wallet.ui.theme.WalletTheme
import kotlin.jvm.JvmName

@Composable
fun DataGroup(
  modifier: Modifier = Modifier,
  rows: DataList,
) {
  val lineColor = WalletTheme.colors.foreground10
  Column(
    modifier =
      modifier
        .background(WalletTheme.colors.background)
        .border(
          width = 2.dp,
          color = lineColor,
          shape = RoundedCornerShape(16.dp)
        )
        .clip(RoundedCornerShape(16.dp)),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    rows.hero?.let {
      Spacer(Modifier.height(20.dp))
      DataHero(model = it)
    }
    rows.items.forEachIndexed { idx, element ->
      DataRowRegular(model = element, isFirst = idx == 0)
    }
    rows.total?.let {
      Divider(color = lineColor)
      DataRowTotal(
        modifier =
          Modifier
            .padding(horizontal = 16.dp),
        model = it
      )
    }
    rows.buttons.forEach {
      Button(modifier = Modifier.padding(vertical = 8.dp), model = it)
      Spacer(Modifier.height(20.dp))
    }
  }
}
