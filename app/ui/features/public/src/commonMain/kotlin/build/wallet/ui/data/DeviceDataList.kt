package build.wallet.ui.data

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.form.FormMainContentModel.DataList

@Composable
fun DeviceDataList(rows: DataList) {
  Box(
    modifier = Modifier.clip(RoundedCornerShape(24.dp))
  ) {
    DataGroupDevice(rows = rows)
  }
}
