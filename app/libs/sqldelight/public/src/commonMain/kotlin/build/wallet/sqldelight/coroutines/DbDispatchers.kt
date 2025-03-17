package build.wallet.sqldelight.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.newSingleThreadContext
import kotlin.coroutines.CoroutineContext

/**
 * We utilize an SQLite based database. Since SQLite allows only one connection at a time, any
 * parallel attempts block due to a lock until the initial connection is released. To mitigate
 * SQLite writer thread lock contention, we employ a single threaded dispatcher.
 *
 * Relevant resources:
 * https://twitter.com/Piwai/status/1568113478946406403
 * https://github.com/cashapp/sqldelight/issues/4376
 *
 * This dispatcher is designated solely for the `bitkey.db` database in the :database module.
 * If we introduce another database in the future, it should have its dedicated dispatcher.
 * Currently, for simplicity, this dispatcher is employed in all our SqlDelight coroutine
 * extensions. Future additions of databases and dispatchers will necessitate ensuring the
 * appropriate dispatcher is used with its respective database.
 */
@Suppress("UnusedReceiverParameter")
internal val Dispatchers.BitkeyDatabaseIO: CoroutineContext
  get() = bitkeyDatabaseIO + CoroutineName("BitkeyDatabaseIO")

/**
 * This is a singleton to ensure we reuse the same dispatcher instance.
 */
private val bitkeyDatabaseIO: CoroutineDispatcher = newSingleThreadContext("BitkeyDatabaseIO")
