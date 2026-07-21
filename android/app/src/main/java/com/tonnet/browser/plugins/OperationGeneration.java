package com.tonnet.browser.plugins;

import java.util.concurrent.atomic.AtomicLong;

/** Rejects callbacks produced by native operations superseded by a stop or newer operation. */
final class OperationGeneration {

  private final AtomicLong generation = new AtomicLong();
  private final AtomicLong activeStart = new AtomicLong();

  enum Disposition {
    CURRENT,
    IDLE,
    SUPERSEDED
  }

  long beginStart() {
    long token = generation.incrementAndGet();
    activeStart.set(token);
    return token;
  }

  long invalidate() {
    activeStart.set(0);
    return generation.incrementAndGet();
  }

  Disposition classify(long candidate) {
    long active = activeStart.get();
    if (active == candidate) {
      return Disposition.CURRENT;
    }
    return active == 0 ? Disposition.IDLE : Disposition.SUPERSEDED;
  }

  boolean isLatest(long candidate) {
    return generation.get() == candidate;
  }
}
