package com.tonnet.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProxyPortTest {

  @Test
  public void acceptsUserPortsWithinBounds() {
    assertTrue(ProxyPort.isValid(ProxyPort.MIN));
    assertTrue(ProxyPort.isValid(8080));
    assertTrue(ProxyPort.isValid(ProxyPort.MAX));
  }

  @Test
  public void rejectsReservedAndOutOfRangePorts() {
    assertFalse(ProxyPort.isValid(0));
    assertFalse(ProxyPort.isValid(1024));
    assertFalse(ProxyPort.isValid(65536));
  }

  @Test
  public void fallsBackToTheSingleDefaultForInvalidPorts() {
    assertEquals(ProxyPort.DEFAULT, ProxyPort.normalize(-1));
    assertEquals(ProxyPort.DEFAULT, ProxyPort.normalize(1024));
    assertEquals(ProxyPort.DEFAULT, ProxyPort.normalize(65536));
    assertEquals(4242, ProxyPort.normalize(4242));
  }
}
