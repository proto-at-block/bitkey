package build.wallet.statemachine.data.keybox.address

import build.wallet.bitcoin.address.BitcoinAddress
import com.github.michaelbull.result.Result

/**
 * @property latestAddress provides latest address generated for the keybox. Currently does not
 * persist across app launches.
 * @property generateAddress callback for requesting to generate a new receiving address.
 */
data class KeyboxAddressData(
  val latestAddress: BitcoinAddress?,
  val generateAddress: (Result<BitcoinAddress, Throwable>.() -> Unit) -> Unit,
)
