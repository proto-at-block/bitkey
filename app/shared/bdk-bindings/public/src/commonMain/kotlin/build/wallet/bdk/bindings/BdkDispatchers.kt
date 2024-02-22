@file:OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)

package build.wallet.bdk.bindings

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlin.coroutines.CoroutineContext

/**
 * Dispatcher for running BDK write operations on. Uses [Dispatchers.Default] but guarantees
 * that no more than 1 coroutine is executed at the same time.
 *
 * We use this dispatcher to prevent [potential deadlocks](https://github.com/bitcoindevkit/bdk-ffi/issues/205)
 * when executing multiple concurrent BDK write calls (e.g. wallet sync). That's because our current
 * BDK UniFFI implementation is not inherently thread safe.
 */
@Suppress("UnusedReceiverParameter")
val Dispatchers.BdkIO: CoroutineContext get() = dispatcherBdkIO + CoroutineName("BdkIO")

/**
 * This is a singleton to ensure we reuse the same dispatcher instance.
 */
private val dispatcherBdkIO: CoroutineDispatcher = newSingleThreadContext("BdkIO")
