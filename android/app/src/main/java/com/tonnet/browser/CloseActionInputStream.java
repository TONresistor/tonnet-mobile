package com.tonnet.browser;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/** Closes a response stream and its owning connection exactly once. */
final class CloseActionInputStream extends FilterInputStream {

  private final AtomicBoolean closed = new AtomicBoolean();
  private final Runnable closeAction;

  CloseActionInputStream(InputStream input, Runnable closeAction) {
    super(input);
    this.closeAction = closeAction;
  }

  @Override
  public void close() throws IOException {
    if (!closed.compareAndSet(false, true)) {
      return;
    }

    try {
      super.close();
    } finally {
      closeAction.run();
    }
  }
}
