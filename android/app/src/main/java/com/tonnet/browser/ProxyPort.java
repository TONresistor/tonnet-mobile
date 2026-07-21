package com.tonnet.browser;

/** Shared validation rules for the local TON proxy port. */
public final class ProxyPort {

  public static final int DEFAULT = 8080;
  public static final int MIN = 1025;
  public static final int MAX = 65535;

  private ProxyPort() {}

  public static boolean isValid(int port) {
    return port >= MIN && port <= MAX;
  }

  public static int normalize(int port) {
    return isValid(port) ? port : DEFAULT;
  }
}
