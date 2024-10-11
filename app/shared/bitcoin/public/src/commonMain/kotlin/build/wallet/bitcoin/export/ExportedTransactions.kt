package build.wallet.bitcoin.export

import okio.ByteString

/*
 * A wrapped CSV blob of a customer's transacion history.
 */
data class ExportedTransactions(val data: ByteString)
