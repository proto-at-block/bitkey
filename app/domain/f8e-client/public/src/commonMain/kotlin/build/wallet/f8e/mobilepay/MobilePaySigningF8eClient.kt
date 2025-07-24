package build.wallet.f8e.mobilepay

import bitkey.verification.PendingTransactionVerification
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.display.BitcoinDisplayUnit
import com.github.michaelbull.result.Result

/**
 * F8e service used to sign Bitcoin transactions with the f8e keyset. Meant to be used in
 * conjunction with signing psbt with the app keyset without the need for hardware - hence the
 * name "mobile pay".
 */
interface MobilePaySigningF8eClient {
  /**
   * Sends partially signed Bitcoin transaction (PSBT) to the server for signing with the keyset of the given ID,
   * and returns signed copy of the transaction.
   *
   * @param fullAccountId The ID of the account building the transaction.
   * @param keysetId The server id of the keyset you wish to sign with.
   * @param psbt The half signed transaction to be signed by the server key before becoming a valid transaction.
   */
  suspend fun signWithSpecificKeyset(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    keysetId: String,
    psbt: Psbt,
  ): Result<Psbt, Error>

  /**
   * Requests verification required before signing a transaction.
   *
   * This method is only used when the transaction limit is above the user's
   * transaction verification limit, but below their mobile spending limit.
   *
   * @param fullAccountId The ID of the account building the transaction.
   * @param keysetId The server id of the keyset you wish to sign with.
   * @param psbt The half signed transaction to be signed by the server key before becoming a valid transaction.
   * @param fiatCurrency The fiat currency to display during verification.
   * @param bitcoinDisplayUnit The Bitcoin display unit to use during verification.
   */
  suspend fun requestVerificationForSigning(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    keysetId: String,
    psbt: Psbt,
    fiatCurrency: FiatCurrency,
    bitcoinDisplayUnit: BitcoinDisplayUnit,
  ): Result<PendingTransactionVerification, Error>
}
