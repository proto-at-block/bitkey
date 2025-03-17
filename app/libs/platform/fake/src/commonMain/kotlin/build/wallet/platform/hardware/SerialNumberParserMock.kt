package build.wallet.platform.hardware

class SerialNumberParserMock : SerialNumberParser {
  var serialNumberComponents = SerialNumberComponents(model = "abcdef01", manufactureInfo = "234")

  override fun parse(serialNumber: String?): SerialNumberComponents {
    return serialNumberComponents
  }
}
