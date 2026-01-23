package build.wallet.platform.connectivity

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_monitor_t
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create
import kotlin.concurrent.Volatile

@BitkeyInject(AppScope::class)
@OptIn(ExperimentalForeignApi::class)
class InternetConnectionCheckerImpl : InternetConnectionChecker {
  private val monitorQueue = dispatch_queue_create("build.wallet.network.monitor", null)
  private val monitor: nw_path_monitor_t = nw_path_monitor_create()

  @Volatile
  private var isConnected: Boolean = true // Default to true until first update

  init {
    nw_path_monitor_set_queue(monitor, monitorQueue)
    nw_path_monitor_set_update_handler(monitor) { path ->
      isConnected = path != null && nw_path_get_status(path) == nw_path_status_satisfied
    }
    nw_path_monitor_start(monitor)
  }

  override fun isConnected(): Boolean {
    return isConnected
  }
}
