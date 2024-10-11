package build.wallet.bdk.bindings

val BdkUtxoMock = BdkUtxo(
  outPoint = BdkOutPointMock,
  txOut = BdkTxOutMock,
  isSpent = false
)

val BdkUtxoMock2 = BdkUtxoMock.copy(
  txOut = BdkTxOutMock2
)
