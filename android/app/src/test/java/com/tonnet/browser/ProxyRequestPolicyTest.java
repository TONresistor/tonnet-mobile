package com.tonnet.browser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProxyRequestPolicyTest {

  @Test
  public void manuallyInterceptsOnlyBodylessIdempotentMethods() {
    assertTrue(ProxyRequestPolicy.supportsManualInterception("GET"));
    assertTrue(ProxyRequestPolicy.supportsManualInterception("head"));

    assertFalse(ProxyRequestPolicy.supportsManualInterception("POST"));
    assertFalse(ProxyRequestPolicy.supportsManualInterception("PUT"));
    assertFalse(ProxyRequestPolicy.supportsManualInterception("DELETE"));
    assertFalse(ProxyRequestPolicy.supportsManualInterception(null));
  }
}
