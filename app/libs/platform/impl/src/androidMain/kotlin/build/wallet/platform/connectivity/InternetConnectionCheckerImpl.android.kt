package build.wallet.platform.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class InternetConnectionCheckerImpl(
  private val context: Context,
) : InternetConnectionChecker {
  override fun isConnected(): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
      ?: return false

    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

    // Check for both INTERNET capability and VALIDATED status.
    // NET_CAPABILITY_VALIDATED indicates the network has been tested by Android
    // and is actually able to reach the internet. Without this check, Android
    // may report "connected" before DNS resolution actually works.
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
      capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
  }
}
