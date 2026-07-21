package com.tonnet.browser;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WebResponseStatusTest {

  @Test
  public void mapsConditionalAndUnhandledRedirectResponsesToSupportedCodes() {
    assertEquals(200, WebResponseStatus.normalizeCode(304));
    assertEquals(502, WebResponseStatus.normalizeCode(307));
    assertEquals(200, WebResponseStatus.normalizeCode(200));
    assertEquals(404, WebResponseStatus.normalizeCode(404));
    assertEquals(500, WebResponseStatus.normalizeCode(99));
  }

  @Test
  public void alwaysReturnsANonEmptyAsciiReasonPhrase() {
    assertEquals("OK", WebResponseStatus.normalizeReason(200, "Not Modified"));
    assertEquals("Bad Gateway", WebResponseStatus.normalizeReason(502, "Temporary Redirect"));
    assertEquals("Created", WebResponseStatus.normalizeReason(201, "Created"));
    assertEquals("Crme", WebResponseStatus.normalizeReason(418, "Crème"));
    assertEquals("HTTP 500", WebResponseStatus.normalizeReason(500, null));
  }
}
