package com.tonnet.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class TimedCompletionTest {

  @Test
  public void timeoutRejectsOnceAndIgnoresLateSuccess() {
    AtomicInteger successes = new AtomicInteger();
    AtomicInteger failures = new AtomicInteger();
    AtomicReference<String> failureCode = new AtomicReference<>();
    FakeScheduler scheduler = new FakeScheduler();
    TimedCompletion<String> completion =
        new TimedCompletion<>(
            callback(successes, failures, failureCode), scheduler, 5_000L, "TIMEOUT", "timed out");

    assertEquals(5_000L, scheduler.delayMillis);
    scheduler.runTimeout();

    assertTrue(completion.isCompleted());
    assertFalse(completion.resolve("late"));
    assertEquals(0, successes.get());
    assertEquals(1, failures.get());
    assertEquals("TIMEOUT", failureCode.get());
    assertTrue(scheduler.cancelled.get());
  }

  @Test
  public void successCancelsTimeout() {
    AtomicInteger successes = new AtomicInteger();
    AtomicInteger failures = new AtomicInteger();
    FakeScheduler scheduler = new FakeScheduler();
    TimedCompletion<String> completion =
        new TimedCompletion<>(
            callback(successes, failures, new AtomicReference<>()),
            scheduler,
            5_000L,
            "TIMEOUT",
            "timed out");

    assertTrue(completion.resolve("ready"));
    scheduler.runTimeout();

    assertEquals(1, successes.get());
    assertEquals(0, failures.get());
    assertTrue(scheduler.cancelled.get());
  }

  @Test
  public void schedulerFailureIsReportedOnce() {
    AtomicInteger failures = new AtomicInteger();
    AtomicReference<String> failureCode = new AtomicReference<>();
    TimedCompletion<String> completion =
        new TimedCompletion<>(
            callback(new AtomicInteger(), failures, failureCode),
            (task, delayMillis) -> {
              throw new IllegalStateException("scheduler unavailable");
            },
            5_000L,
            "TIMEOUT",
            "timed out");

    assertTrue(completion.isCompleted());
    assertEquals(1, failures.get());
    assertEquals("CALLBACK_SCHEDULING_FAILED", failureCode.get());
  }

  private static OnceCompletion.Callback<String> callback(
      AtomicInteger successes, AtomicInteger failures, AtomicReference<String> failureCode) {
    return new OnceCompletion.Callback<String>() {
      @Override
      public void onSuccess(String value) {
        successes.incrementAndGet();
      }

      @Override
      public void onFailure(String code, String message, Throwable cause) {
        failureCode.set(code);
        failures.incrementAndGet();
      }
    };
  }

  private static final class FakeScheduler implements TimedCompletion.Scheduler {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private Runnable task;
    private long delayMillis;

    @Override
    public TimedCompletion.Cancellable schedule(Runnable scheduledTask, long delay) {
      task = scheduledTask;
      delayMillis = delay;
      return () -> cancelled.set(true);
    }

    private void runTimeout() {
      if (!cancelled.get()) {
        task.run();
      }
    }
  }
}
