package com.tonnet.browser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NetworkConnectionTest {

  @Test
  public void requiresInternetAndValidationCapabilities() {
    assertTrue(NetworkConnection.isUsable(true, true));
    assertFalse(NetworkConnection.isUsable(true, false));
    assertFalse(NetworkConnection.isUsable(false, true));
    assertFalse(NetworkConnection.isUsable(false, false));
  }
}
