package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.BdkError
import build.wallet.bdk.bindings.BdkWallet
import build.wallet.bitcoin.wallet.WalletDescriptor
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import uniffi.bdk.Persister
import uniffi.bdk.Wallet as BdkV2Wallet

class BdkWalletProviderMock(
  private val wallet: BdkWallet? = null,
  private val walletV2: BdkV2Wallet? = null,
  private val persister: Persister? = null,
) : BdkWalletProvider {
  override suspend fun getBdkWallet(
    walletDescriptor: WalletDescriptor,
  ): Result<BdkWallet, BdkError> {
    return wallet?.let {
      Ok(wallet)
    } ?: return Err(BdkError.Generic(Error(), message = "Wallet not found"))
  }

  override fun getBdkWalletV2(walletDescriptor: WalletDescriptor): Result<BdkV2Wallet, Throwable> {
    return walletV2?.let {
      Ok(walletV2)
    } ?: return Err(IllegalStateException("BDK wallet mock not provided"))
  }

  override fun getPersister(identifier: String): Persister {
    return persister ?: error("Persister mock not provided")
  }
}
