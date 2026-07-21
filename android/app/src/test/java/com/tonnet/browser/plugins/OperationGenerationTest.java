package com.tonnet.browser.plugins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OperationGenerationTest {

  @Test
  public void invalidationRejectsLateCallbacks() {
    OperationGeneration operations = new OperationGeneration();
    long start = operations.beginStart();

    long stop = operations.invalidate();

    assertEquals(OperationGeneration.Disposition.IDLE, operations.classify(start));
    assertTrue(operations.isLatest(stop));
    assertFalse(operations.isLatest(start));
  }

  @Test
  public void newerOperationSupersedesEarlierOperation() {
    OperationGeneration operations = new OperationGeneration();
    long first = operations.beginStart();
    long second = operations.beginStart();

    assertEquals(OperationGeneration.Disposition.SUPERSEDED, operations.classify(first));
    assertEquals(OperationGeneration.Disposition.CURRENT, operations.classify(second));
    assertTrue(operations.isLatest(second));
  }

  @Test
  public void lateStartCannotBecomeCurrentAfterStopAndRetry() {
    OperationGeneration operations = new OperationGeneration();
    long obsoleteStart = operations.beginStart();
    long stop = operations.invalidate();
    long retry = operations.beginStart();

    assertFalse(operations.isLatest(stop));
    assertEquals(OperationGeneration.Disposition.SUPERSEDED, operations.classify(obsoleteStart));
    assertEquals(OperationGeneration.Disposition.CURRENT, operations.classify(retry));
  }
}
