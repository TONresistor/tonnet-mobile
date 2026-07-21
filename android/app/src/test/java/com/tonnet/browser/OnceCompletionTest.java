package com.tonnet.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class OnceCompletionTest {

  @Test
  public void deliversOnlyTheFirstTerminalResult() {
    AtomicInteger successes = new AtomicInteger();
    AtomicInteger failures = new AtomicInteger();
    OnceCompletion<String> completion = new OnceCompletion<>(callback(successes, failures));

    assertTrue(completion.resolve("ready"));
    assertFalse(completion.reject("LATE", "late failure", null));
    assertFalse(completion.resolve("late success"));

    assertTrue(completion.isCompleted());
    assertEquals(1, successes.get());
    assertEquals(0, failures.get());
  }

  @Test(timeout = 5_000L)
  public void racingCallbacksStillCompleteExactlyOnce() throws Exception {
    AtomicInteger successes = new AtomicInteger();
    AtomicInteger failures = new AtomicInteger();
    OnceCompletion<String> completion = new OnceCompletion<>(callback(successes, failures));
    int contenders = 12;
    CountDownLatch ready = new CountDownLatch(contenders);
    CountDownLatch start = new CountDownLatch(1);
    List<Thread> threads = new ArrayList<>();

    for (int index = 0; index < contenders; index++) {
      final int contender = index;
      Thread thread =
          new Thread(
              () -> {
                ready.countDown();
                try {
                  start.await();
                  if (contender % 2 == 0) {
                    completion.resolve("success");
                  } else {
                    completion.reject("FAILED", "failure", null);
                  }
                } catch (InterruptedException error) {
                  Thread.currentThread().interrupt();
                }
              });
      threads.add(thread);
      thread.start();
    }

    ready.await();
    start.countDown();
    for (Thread thread : threads) {
      thread.join();
    }

    assertEquals(1, successes.get() + failures.get());
  }

  private static OnceCompletion.Callback<String> callback(
      AtomicInteger successes, AtomicInteger failures) {
    return new OnceCompletion.Callback<String>() {
      @Override
      public void onSuccess(String value) {
        successes.incrementAndGet();
      }

      @Override
      public void onFailure(String code, String message, Throwable cause) {
        failures.incrementAndGet();
      }
    };
  }
}
