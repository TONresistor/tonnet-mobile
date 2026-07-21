package com.tonnet.browser;

import java.util.Locale;

/** Request methods that can be safely replayed by the manual WebView interception path. */
final class ProxyRequestPolicy {

  private ProxyRequestPolicy() {}

  static boolean supportsManualInterception(String method) {
    if (method == null) {
      return false;
    }
    String normalized = method.toUpperCase(Locale.ROOT);
    return "GET".equals(normalized) || "HEAD".equals(normalized);
  }
}
