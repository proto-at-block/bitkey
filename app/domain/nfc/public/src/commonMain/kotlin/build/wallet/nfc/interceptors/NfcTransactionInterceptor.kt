package build.wallet.nfc.interceptors

import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.NfcCommands

typealias NfcEffect = suspend (NfcSession, NfcCommands) -> Unit

fun interface NfcTransactionInterceptor : (NfcEffect) -> NfcEffect
