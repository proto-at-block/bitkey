package build.wallet.bitcoin.descriptor

import build.wallet.bitcoin.descriptor.BitcoinDescriptor.Spending
import build.wallet.bitcoin.descriptor.BitcoinDescriptor.Watching
import build.wallet.bitcoin.keys.DescriptorPublicKey
import build.wallet.bitcoin.keys.ExtendedPrivateKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.bitkey.spending.SpendingKeysetMock

data class WatchingReceivingDescriptorCall(
  val appPublicKey: DescriptorPublicKey,
  val hardwareKey: DescriptorPublicKey,
  val serverKey: DescriptorPublicKey,
)

data class WatchingDescriptorCall(
  val appPublicKey: DescriptorPublicKey,
  val hardwareKey: DescriptorPublicKey,
  val serverKey: DescriptorPublicKey,
)

class BitcoinMultiSigDescriptorBuilderMock : BitcoinMultiSigDescriptorBuilder {
  val spendingKeysetMock: SpendingKeyset = SpendingKeysetMock

  var watchingReceivingDescriptorResult: String = "default-descriptor"
  val watchingReceivingDescriptorCalls = mutableListOf<WatchingReceivingDescriptorCall>()

  var watchingDescriptorResult: String = "default-descriptor"
  val watchingDescriptorCalls = mutableListOf<WatchingDescriptorCall>()

  override fun spendingReceivingDescriptor(
    descriptorKeyset: String,
    publicKey: DescriptorPublicKey,
    privateKey: ExtendedPrivateKey,
  ): Spending {
    return Spending("")
  }

  override fun spendingChangeDescriptor(
    descriptorKeyset: String,
    publicKey: DescriptorPublicKey,
    privateKey: ExtendedPrivateKey,
  ): Spending {
    return Spending("")
  }

  override fun spendingReceivingDescriptor(
    appPrivateKey: ExtendedPrivateKey,
    hardwareKey: DescriptorPublicKey,
    serverKey: DescriptorPublicKey,
  ): Spending {
    return Spending("")
  }

  override fun watchingReceivingDescriptor(
    appPublicKey: DescriptorPublicKey,
    hardwareKey: DescriptorPublicKey,
    serverKey: DescriptorPublicKey,
  ): Watching {
    watchingReceivingDescriptorCalls.add(
      WatchingReceivingDescriptorCall(
        appPublicKey = appPublicKey,
        hardwareKey = hardwareKey,
        serverKey = serverKey
      )
    )
    return Watching(watchingReceivingDescriptorResult)
  }

  override fun spendingChangeDescriptor(
    appPrivateKey: ExtendedPrivateKey,
    hardwareKey: DescriptorPublicKey,
    serverKey: DescriptorPublicKey,
  ): Spending {
    return Spending("")
  }

  override fun watchingChangeDescriptor(
    appPublicKey: DescriptorPublicKey,
    hardwareKey: DescriptorPublicKey,
    serverKey: DescriptorPublicKey,
  ): Watching {
    return Watching("")
  }

  override fun watchingDescriptor(
    appPublicKey: DescriptorPublicKey,
    hardwareKey: DescriptorPublicKey,
    serverKey: DescriptorPublicKey,
  ): Watching {
    watchingDescriptorCalls.add(
      WatchingDescriptorCall(
        appPublicKey = appPublicKey,
        hardwareKey = hardwareKey,
        serverKey = serverKey
      )
    )
    return Watching(watchingDescriptorResult)
  }

  fun reset() {
    watchingReceivingDescriptorResult = "default-descriptor"
    watchingReceivingDescriptorCalls.clear()
    watchingDescriptorResult = "default-descriptor"
    watchingDescriptorCalls.clear()
  }
}
