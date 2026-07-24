package main

/*
#include <stdlib.h>
*/
import "C"

import (
	"context"
	"encoding/json"
	"fmt"
	"net"
	"sync"
	"time"
	"unsafe"

	"github.com/rs/zerolog"
	tunnelConfig "github.com/ton-blockchain/adnl-tunnel/config"
	"github.com/xssnick/tonutils-go/liteclient"
	"github.com/xssnick/tonutils-proxy/proxy"
)

var GitCommit = "development"
var shutdownTimeout = 7 * time.Second

const (
	sessionStateStopped = iota
	sessionStateStarting
	sessionStateReady
	sessionStateFailed
	sessionStateRecovering
)

var session struct {
	sync.Mutex
	cancel       context.CancelFunc
	done         chan struct{}
	state        int
	mode         int
	generation   uint64
	httpReady    bool
	expectedStop bool
}

type startupOutcome struct {
	ready bool
}

func main() {}

func init() {
	zerolog.SetGlobalLevel(zerolog.Disabled)
}

//export StartProxySession
func StartProxySession(configTextJSON *C.char, mode C.int) *C.char {
	if configTextJSON == nil {
		return C.CString("ERR:missing TON global config")
	}
	if mode != 0 && mode != 1 {
		return C.CString("ERR:invalid network mode")
	}

	var networkConfig liteclient.GlobalConfig
	if err := json.Unmarshal([]byte(C.GoString(configTextJSON)), &networkConfig); err != nil {
		return C.CString("ERR:invalid TON global config")
	}
	if len(networkConfig.Liteservers) == 0 || len(networkConfig.DHT.StaticNodes.Nodes) == 0 {
		return C.CString("ERR:incomplete TON global config")
	}

	session.Lock()
	if session.cancel != nil {
		session.Unlock()
		return C.CString("ERR:session already running")
	}

	listener, err := net.Listen("tcp4", "127.0.0.1:0")
	if err != nil {
		session.Unlock()
		return C.CString("ERR:cannot bind loopback proxy")
	}

	var tunnel *tunnelConfig.ClientConfig
	if mode == 1 {
		tunnel, err = tunnelConfig.GenerateClientConfig()
		if err != nil {
			_ = listener.Close()
			session.Unlock()
			return C.CString("ERR:cannot generate tunnel session")
		}
		tunnel.TunnelSectionsNum = 2
		tunnel.NodesPoolConfigPath = ""
		tunnel.PaymentsEnabled = false
	}

	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan struct{})
	startup := make(chan startupOutcome, 1)
	session.cancel = cancel
	session.done = done
	session.state = sessionStateStarting
	session.mode = int(mode)
	session.generation++
	generation := session.generation
	session.httpReady = false
	session.expectedStop = false
	session.Unlock()

	states := make(chan proxy.State, 16)
	runResult := make(chan error, 1)
	monitorDone := make(chan struct{})

	proxy.OnAskReroute = func() bool {
		return approveTunnelReroute(generation)
	}
	proxy.OnTunnelStopped = func() {
		if markUnexpectedTunnelStop(generation) {
			signalStartup(startup, startupOutcome{})
		}
	}

	go func() {
		defer close(monitorDone)
		for state := range states {
			applyProxyState(generation, state, startup)
		}
	}()

	go func() {
		err := proxy.RunProxyWithConfig(
			ctx,
			listener.Addr().String(),
			nil,
			states,
			true,
			"TONNET Mobile "+GitCommit,
			&networkConfig,
			tunnel,
			&networkConfig,
			nil,
			nil,
			listener,
		)
		close(states)
		<-monitorDone
		_ = listener.Close()
		finishSession(generation, done)
		close(done)
		runResult <- err
	}()

	timeout := 45 * time.Second
	if mode == 1 {
		timeout = 90 * time.Second
	}
	timer := time.NewTimer(timeout)
	defer timer.Stop()

	for {
		select {
		case outcome := <-startup:
			if outcome.ready {
				port := listener.Addr().(*net.TCPAddr).Port
				return C.CString(fmt.Sprintf("OK:%d", port))
			}
			if !stopSession(cancel, done) {
				return C.CString("ERR:TON proxy failed to start and shutdown failed")
			}
			return C.CString("ERR:TON proxy failed to start")
		case err := <-runResult:
			if err == nil {
				return C.CString("ERR:TON proxy stopped")
			}
			return C.CString("ERR:TON proxy initialization failed")
		case <-timer.C:
			if !stopSession(cancel, done) {
				return C.CString("ERR:TON proxy startup timed out and shutdown failed")
			}
			return C.CString("ERR:TON proxy startup timed out")
		}
	}
}

//export GetProxySessionState
func GetProxySessionState() C.int {
	session.Lock()
	defer session.Unlock()
	return C.int(session.state)
}

//export StopProxy
func StopProxy() *C.char {
	session.Lock()
	cancel := session.cancel
	done := session.done
	session.Unlock()

	if cancel == nil {
		return C.CString("OK")
	}
	if !stopSession(cancel, done) {
		return C.CString("ERR:TON proxy shutdown timed out")
	}
	return C.CString("OK")
}

func stopSession(cancel context.CancelFunc, done chan struct{}) bool {
	session.Lock()
	if session.done == done {
		session.expectedStop = true
	}
	session.Unlock()
	cancel()
	stopped := false
	select {
	case <-done:
		stopped = true
	case <-time.After(shutdownTimeout):
	}

	if stopped {
		session.Lock()
		if session.done == done {
			session.cancel = nil
			session.done = nil
			session.state = sessionStateStopped
			session.httpReady = false
			session.expectedStop = false
		}
		session.Unlock()
	}
	return stopped
}

func signalStartup(startup chan<- startupOutcome, outcome startupOutcome) {
	select {
	case startup <- outcome:
	default:
	}
}

func applyProxyState(generation uint64, state proxy.State, startup chan<- startupOutcome) {
	switch {
	case state.Type == "ready" && !state.Stopped:
		accepted := false
		session.Lock()
		if session.generation == generation && session.cancel != nil && !session.expectedStop {
			session.httpReady = true
			session.state = sessionStateReady
			accepted = true
		}
		session.Unlock()
		if accepted {
			signalStartup(startup, startupOutcome{ready: true})
		}
	case state.Stopped || state.Type == "error":
		if markSessionFailed(generation) {
			signalStartup(startup, startupOutcome{})
		}
	case state.Type == "loading":
		session.Lock()
		if session.generation == generation &&
			session.cancel != nil &&
			session.mode == 1 &&
			session.httpReady &&
			!session.expectedStop {
			session.state = sessionStateRecovering
		}
		session.Unlock()
	}
}

func approveTunnelReroute(generation uint64) bool {
	session.Lock()
	defer session.Unlock()
	if session.generation != generation ||
		session.cancel == nil ||
		session.mode != 1 ||
		session.expectedStop {
		return false
	}
	if session.httpReady {
		session.state = sessionStateRecovering
	}
	return true
}

func markUnexpectedTunnelStop(generation uint64) bool {
	return markSessionFailed(generation)
}

func markSessionFailed(generation uint64) bool {
	session.Lock()
	defer session.Unlock()
	if session.generation != generation || session.cancel == nil || session.expectedStop {
		return false
	}
	session.state = sessionStateFailed
	return true
}

func finishSession(generation uint64, done chan struct{}) {
	session.Lock()
	defer session.Unlock()
	if session.generation != generation || session.done != done {
		return
	}
	if session.expectedStop {
		session.state = sessionStateStopped
	} else {
		session.state = sessionStateFailed
	}
	session.cancel = nil
	session.done = nil
	session.httpReady = false
	session.expectedStop = false
}

//export FreeCString
func FreeCString(value *C.char) {
	C.free(unsafe.Pointer(value))
}
