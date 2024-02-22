package build.wallet.testing

import build.wallet.nfc.NfcSessionFake
import build.wallet.nfc.NfcTransactor
import build.wallet.nfc.TransactionFn

suspend fun <T> NfcTransactor.fakeTransact(transaction: TransactionFn<T>) =
  transact(
    parameters = NfcSessionFake.FakeParameters,
    transaction = transaction
  )
