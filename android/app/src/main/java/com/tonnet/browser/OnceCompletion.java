package com.tonnet.browser;

import java.util.concurrent.atomic.AtomicBoolean;

/** Delivers exactly one success or failure result, including when callbacks race. */
public final class OnceCompletion<T> {

  public interface Callback<T> {
    void onSuccess(T value);

    void onFailure(String code, String message, Throwable cause);
  }

  private final AtomicBoolean completed = new AtomicBoolean();
  private final Callback<T> callback;

  public OnceCompletion(Callback<T> callback) {
    if (callback == null) {
      throw new IllegalArgumentException("callback must not be null");
    }
    this.callback = callback;
  }

  public boolean resolve(T value) {
    if (!completed.compareAndSet(false, true)) {
      return false;
    }
    callback.onSuccess(value);
    return true;
  }

  public boolean reject(String code, String message, Throwable cause) {
    if (!completed.compareAndSet(false, true)) {
      return false;
    }
    callback.onFailure(code, message, cause);
    return true;
  }

  public boolean isCompleted() {
    return completed.get();
  }
}
