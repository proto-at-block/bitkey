@file:OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)

package build.wallet.nfc

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlin.coroutines.CoroutineContext

/**
 * Single threaded [CoroutineDispatcher] for waiting for NFC I/O operations on.
 */
@Suppress("UnusedReceiverParameter")
val Dispatchers.NfcIO: CoroutineContext get() = dispatcherNfcIO + CoroutineName("NfcIO")

/**
 * This is a singleton to ensure we reuse the same dispatcher instance.
 */
private val dispatcherNfcIO: CoroutineDispatcher = newSingleThreadContext("NfcIO")
