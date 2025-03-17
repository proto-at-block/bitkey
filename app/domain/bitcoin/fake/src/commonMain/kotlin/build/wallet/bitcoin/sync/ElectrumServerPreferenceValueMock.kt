package build.wallet.bitcoin.sync

val OffElectrumServerPreferenceValueMock =
  ElectrumServerPreferenceValue.Off(
    previousUserDefinedElectrumServer = null
  )

val OffElectrumServerWithPreviousPreferenceValueMock =
  ElectrumServerPreferenceValue.Off(
    previousUserDefinedElectrumServer = CustomElectrumServerMock
  )

val OnElectrumServerPreferenceValueMock =
  ElectrumServerPreferenceValue.On(
    server = CustomElectrumServerMock
  )
