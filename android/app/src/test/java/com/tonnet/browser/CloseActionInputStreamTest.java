package com.tonnet.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class CloseActionInputStreamTest {

  @Test
  public void closesBodyAndConnectionExactlyOnce() throws Exception {
    AtomicInteger connectionCloses = new AtomicInteger();
    CountingInputStream body = new CountingInputStream(false);
    CloseActionInputStream input =
        new CloseActionInputStream(body, connectionCloses::incrementAndGet);

    input.close();
    input.close();

    assertEquals(1, body.closeCount.get());
    assertEquals(1, connectionCloses.get());
  }

  @Test
  public void disconnectsWhenClosingTheBodyFails() {
    AtomicInteger connectionCloses = new AtomicInteger();
    CloseActionInputStream input =
        new CloseActionInputStream(
            new CountingInputStream(true), connectionCloses::incrementAndGet);

    assertThrows(IOException.class, input::close);
    assertEquals(1, connectionCloses.get());
  }

  private static final class CountingInputStream extends InputStream {
    private final ByteArrayInputStream delegate = new ByteArrayInputStream(new byte[] {1});
    private final AtomicInteger closeCount = new AtomicInteger();
    private final boolean failOnClose;

    private CountingInputStream(boolean failOnClose) {
      this.failOnClose = failOnClose;
    }

    @Override
    public int read() {
      return delegate.read();
    }

    @Override
    public void close() throws IOException {
      closeCount.incrementAndGet();
      if (failOnClose) {
        throw new IOException("close failed");
      }
      delegate.close();
    }
  }
}
