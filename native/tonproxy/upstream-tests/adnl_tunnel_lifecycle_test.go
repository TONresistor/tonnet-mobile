package tunnel

import (
	"context"
	"testing"
	"time"
)

func TestRouteRetryStopsWhenSessionIsAlreadyCanceled(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	started := time.Now()
	if waitForRouteRetry(ctx, context.Canceled, time.Hour) {
		t.Fatal("canceled tunnel session must not retry a route")
	}
	if time.Since(started) > 100*time.Millisecond {
		t.Fatal("canceled tunnel retry did not stop immediately")
	}
}

func TestRouteRetryDelayIsCancellationAware(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	result := make(chan bool, 1)
	go func() {
		result <- waitForRouteRetry(ctx, context.DeadlineExceeded, time.Hour)
	}()

	cancel()
	select {
	case retry := <-result:
		if retry {
			t.Fatal("tunnel route retried after cancellation")
		}
	case <-time.After(time.Second):
		t.Fatal("tunnel retry remained blocked after cancellation")
	}
}

func TestExhaustedRoutesCanRetryWithoutDelay(t *testing.T) {
	if !waitForRouteRetry(context.Background(), ErrNoMoreRoutes, time.Hour) {
		t.Fatal("an active tunnel session should reset an exhausted route pool")
	}
}
