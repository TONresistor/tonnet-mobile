package com.tonnet.browser;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/** Reports whether Android has a validated network capable of reaching the Internet. */
public final class NetworkConnection {

  private NetworkConnection() {}

  public static boolean hasValidatedInternet(Context context) {
    ConnectivityManager connectivityManager =
        (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    if (connectivityManager == null) {
      return false;
    }

    Network network = connectivityManager.getActiveNetwork();
    if (network == null) {
      return false;
    }

    NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
    return capabilities != null
        && isUsable(
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
  }

  static boolean isUsable(boolean hasInternetCapability, boolean isValidated) {
    return hasInternetCapability && isValidated;
  }
}
