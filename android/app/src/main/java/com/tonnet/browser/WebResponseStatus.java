package com.tonnet.browser;

/** Adapts upstream HTTP statuses to the stricter WebResourceResponse contract. */
final class WebResponseStatus {

  private WebResponseStatus() {}

  static int normalizeCode(int statusCode) {
    if (statusCode == 304) {
      // The manual bridge has no shared HTTP cache body to pair with a 304 response.
      return 200;
    }
    if (statusCode >= 300 && statusCode <= 399) {
      // HttpURLConnection follows normal redirects. A remaining 3xx cannot be represented by
      // WebResourceResponse, so surface it as an upstream gateway failure instead of crashing.
      return 502;
    }
    if (statusCode < 100 || statusCode > 599) {
      return 500;
    }
    return statusCode;
  }

  static String normalizeReason(int statusCode, String reason) {
    if (statusCode == 200 && (reason == null || reason.equalsIgnoreCase("Not Modified"))) {
      return "OK";
    }
    if (statusCode == 502) {
      return "Bad Gateway";
    }

    if (reason != null) {
      StringBuilder ascii = new StringBuilder();
      for (int index = 0; index < reason.length(); index++) {
        char character = reason.charAt(index);
        if (character >= 0x20 && character <= 0x7e) {
          ascii.append(character);
        }
      }
      String normalized = ascii.toString().trim();
      if (!normalized.isEmpty()) {
        return normalized;
      }
    }

    return "HTTP " + statusCode;
  }
}
