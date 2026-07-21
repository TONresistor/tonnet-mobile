package com.tonnet.browser;

import java.util.concurrent.atomic.AtomicReference;

/** Adds a cancellable timeout to an exactly-once completion. */
final class TimedCompletion<T> {

  interface Cancellable {
    void cancel();
  }

  interface Scheduler {
    Cancellable schedule(Runnable task, long delayMillis);
  }

  private final OnceCompletion<T> completion;
  private final AtomicReference<Cancellable> timeout = new AtomicReference<>();

  TimedCompletion(
      OnceCompletion.Callback<T> callback,
      Scheduler scheduler,
      long timeoutMillis,
      String timeoutCode,
      String timeoutMessage) {
    completion =
        new OnceCompletion<>(
            new OnceCompletion.Callback<T>() {
              @Override
              public void onSuccess(T value) {
                cancelTimeout();
                callback.onSuccess(value);
              }

              @Override
              public void onFailure(String code, String message, Throwable cause) {
                cancelTimeout();
                callback.onFailure(code, message, cause);
              }
            });

    try {
      Cancellable scheduled =
          scheduler.schedule(
              () -> completion.reject(timeoutCode, timeoutMessage, null), timeoutMillis);
      timeout.set(scheduled);
      if (completion.isCompleted()) {
        cancelTimeout();
      }
    } catch (RuntimeException error) {
      completion.reject(
          "CALLBACK_SCHEDULING_FAILED", "Unable to schedule the browser operation timeout", error);
    }
  }

  boolean resolve(T value) {
    return completion.resolve(value);
  }

  boolean reject(String code, String message, Throwable cause) {
    return completion.reject(code, message, cause);
  }

  boolean isCompleted() {
    return completion.isCompleted();
  }

  private void cancelTimeout() {
    Cancellable scheduled = timeout.getAndSet(null);
    if (scheduled != null) {
      scheduled.cancel();
    }
  }
}
