package main

import (
	"context"
	"testing"
	"time"

	"github.com/xssnick/tonutils-proxy/proxy"
)

func TestStopTimeoutKeepsSessionReserved(t *testing.T) {
	originalTimeout := shutdownTimeout
	shutdownTimeout = 10 * time.Millisecond
	defer func() { shutdownTimeout = originalTimeout }()

	_, cancel := context.WithCancel(context.Background())
	done := make(chan struct{})
	session.Lock()
	session.cancel = cancel
	session.done = done
	session.state = sessionStateReady
	session.expectedStop = false
	session.Unlock()
	if GetProxySessionState() != sessionStateReady {
		t.Fatal("reserved ready session must report ready")
	}

	if stopSession(cancel, done) {
		t.Fatal("expected shutdown timeout")
	}
	session.Lock()
	reserved := session.cancel != nil && session.done == done
	session.Unlock()
	if !reserved {
		t.Fatal("timed-out session must remain reserved")
	}

	close(done)
	if !stopSession(cancel, done) {
		t.Fatal("expected completed shutdown")
	}
	session.Lock()
	cleared := session.cancel == nil && session.done == nil && session.state == sessionStateStopped
	session.Unlock()
	if !cleared {
		t.Fatal("completed session must be cleared")
	}
	if GetProxySessionState() != sessionStateStopped {
		t.Fatal("stopped session must report stopped")
	}
}

func TestTunnelRecoveryUsesUpstreamRerouteLifecycle(t *testing.T) {
	_, cancel := context.WithCancel(context.Background())
	done := make(chan struct{})
	session.Lock()
	session.cancel = cancel
	session.done = done
	session.state = sessionStateReady
	session.mode = 1
	session.generation++
	generation := session.generation
	session.httpReady = true
	session.expectedStop = false
	session.Unlock()

	if !approveTunnelReroute(generation) {
		t.Fatal("active tunnel reroute must be approved")
	}
	if GetProxySessionState() != sessionStateRecovering {
		t.Fatal("approved reroute must report recovery")
	}

	applyProxyState(
		generation,
		proxy.State{Type: "ready", State: "Ready"},
		make(chan startupOutcome, 1),
	)
	if GetProxySessionState() != sessionStateReady {
		t.Fatal("updated tunnel route must report ready")
	}

	session.Lock()
	session.expectedStop = true
	session.Unlock()
	if approveTunnelReroute(generation) {
		t.Fatal("stopping tunnel must not start another reroute")
	}

	close(done)
	if !stopSession(cancel, done) {
		t.Fatal("expected completed shutdown")
	}
}

func TestUnexpectedTunnelStopFailsSession(t *testing.T) {
	_, cancel := context.WithCancel(context.Background())
	done := make(chan struct{})
	session.Lock()
	session.cancel = cancel
	session.done = done
	session.state = sessionStateReady
	session.mode = 1
	session.generation++
	generation := session.generation
	session.httpReady = true
	session.expectedStop = false
	session.Unlock()

	if !markUnexpectedTunnelStop(generation) {
		t.Fatal("unexpected active tunnel stop must be accepted")
	}
	if GetProxySessionState() != sessionStateFailed {
		t.Fatal("unexpected tunnel stop must fail the native session")
	}

	session.Lock()
	session.expectedStop = true
	session.Unlock()
	close(done)
	if !stopSession(cancel, done) {
		t.Fatal("expected completed shutdown")
	}
}
