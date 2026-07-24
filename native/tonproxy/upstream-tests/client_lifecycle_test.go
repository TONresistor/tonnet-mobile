package transport

import (
	"context"
	"errors"
	"io"
	"testing"
	"time"

	"github.com/xssnick/tonutils-storage/storage"
)

type lifecycleTestDownloader struct {
	ctx context.Context
}

func (d *lifecycleTestDownloader) Close()         {}
func (d *lifecycleTestDownloader) IsActive() bool { return d.ctx.Err() == nil }

func TestCreatePersistentDownloaderCancelsPendingCreation(t *testing.T) {
	requestCtx, cancelRequest := context.WithCancel(context.Background())
	globalCtx, cancelGlobal := context.WithCancel(context.Background())
	defer cancelGlobal()

	started := make(chan struct{})
	result := make(chan error, 1)
	go func() {
		_, _, err := createPersistentDownloader(requestCtx, globalCtx, func(ctx context.Context) (storage.TorrentDownloader, error) {
			close(started)
			<-ctx.Done()
			return nil, ctx.Err()
		})
		result <- err
	}()

	<-started
	cancelRequest()
	select {
	case err := <-result:
		if !errors.Is(err, context.Canceled) {
			t.Fatalf("expected cancellation, got %v", err)
		}
	case <-time.After(time.Second):
		t.Fatal("pending downloader creation did not stop")
	}
}

func TestCreatePersistentDownloaderSurvivesRequestCompletion(t *testing.T) {
	requestCtx, cancelRequest := context.WithCancel(context.Background())
	globalCtx, cancelGlobal := context.WithCancel(context.Background())
	defer cancelGlobal()

	var downloaderCtx context.Context
	downloader, stop, err := createPersistentDownloader(requestCtx, globalCtx, func(ctx context.Context) (storage.TorrentDownloader, error) {
		downloaderCtx = ctx
		return &lifecycleTestDownloader{ctx: ctx}, nil
	})
	if err != nil {
		t.Fatal(err)
	}
	defer downloader.Close()
	defer stop()

	cancelRequest()
	select {
	case <-downloaderCtx.Done():
		t.Fatal("completed request canceled cached downloader")
	default:
	}
}

func TestTransportStopClosesRequestsAndIsIdempotent(t *testing.T) {
	globalCtx, cancel := context.WithCancel(context.Background())
	stream := newDataStreamer()
	transport := &Transport{
		activeRequests: map[string]*payloadStream{
			"request": {Data: stream},
		},
		activeSites: map[string]*siteInfo{},
		globalCtx:   globalCtx,
		stop:        cancel,
	}

	transport.Stop()
	transport.Stop()

	if globalCtx.Err() == nil {
		t.Fatal("transport context was not canceled")
	}
	buffer := make([]byte, 1)
	if _, err := stream.Read(buffer); !errors.Is(err, io.ErrUnexpectedEOF) {
		t.Fatalf("active request stream was not closed, got %v", err)
	}
	if len(transport.activeRequests) != 0 {
		t.Fatal("active requests were not released")
	}
}
