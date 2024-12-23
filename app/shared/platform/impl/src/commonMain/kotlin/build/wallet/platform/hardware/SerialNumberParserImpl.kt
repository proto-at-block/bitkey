package build.wallet.platform.hardware

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class SerialNumberParserImpl : SerialNumberParser {
  override fun parse(serialNumber: String?): SerialNumberComponents {
    var hwModel: String? = null
    var hwManufactureInfo: String? = null
    if (serialNumber != null && serialNumber.length > 11) {
      hwModel = serialNumber.substring(0, 8)
      hwManufactureInfo = serialNumber.substring(8, 11)
    }

    return SerialNumberComponents(hwModel, hwManufactureInfo)
  }
}
