package build.wallet.bitcoin.export

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class ExportWatchingDescriptorServiceMock : ExportWatchingDescriptorService {
  private val defaultDescriptorString: String =
    """
      External: wsh(sortedmulti(2,[a0a76f7a/84'/0'/0']xpub6CeEJqRzUbpUHsEkbz65NLzHYzrT5TpngE2pmuikVFJgSwU7hjEVpjeroQkdbGKLksMDnXPRxvif9jM2jBGxBXKdZYXNBLwG23tEqkAneEn/0/*,[18240e16/84'/0'/0']xpub6CF6yNxSPoNFJESR4xoxzTGKF6GZad5zdB14waPanqnkFWsNAuYNzq2y7nZk5TGiPcGjY8P3ZwrdixbsSp9uVMZ4QtRkMdUNHUsabMnPbKD/0/*,[afc9474a/84'/0'/0']xpub6CHktMjw1eKdvXgiYdzHVr58KfKnoZpZHqDmTD8MMPJA5vom1sd1TdrNnNESvrxXgZDUMeyBjg2wQYnmMXB767nwh5Z8eNDeFmdPrDdmKaq/0/*)) 

      Internal: wsh(sortedmulti(2,[a0a76f7a/84'/0'/0']xpub6CeEJqRzUbpUHsEkbz65NLzHYzrT5TpngE2pmuikVFJgSwU7hjEVpjeroQkdbGKLksMDnXPRxvif9jM2jBGxBXKdZYXNBLwG23tEqkAneEn/1/*,[18240e16/84'/0'/0']xpub6CF6yNxSPoNFJESR4xoxzTGKF6GZad5zdB14waPanqnkFWsNAuYNzq2y7nZk5TGiPcGjY8P3ZwrdixbsSp9uVMZ4QtRkMdUNHUsabMnPbKD/1/*,[afc9474a/84'/0'/0']xpub6CHktMjw1eKdvXgiYdzHVr58KfKnoZpZHqDmTD8MMPJA5vom1sd1TdrNnNESvrxXgZDUMeyBjg2wQYnmMXB767nwh5Z8eNDeFmdPrDdmKaq/1/*))
    """.trimIndent()

  var result: Result<String, Error> = Ok(defaultDescriptorString)

  override suspend fun formattedActiveWalletDescriptorString(): Result<String, Throwable> {
    return result
  }

  fun reset() {
    result = Ok(defaultDescriptorString)
  }
}
