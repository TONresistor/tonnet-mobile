package proxy

import (
	"net/http"
	"testing"
)

func TestCanonicalTONHost(t *testing.T) {
	tests := []struct {
		host    string
		allowed bool
		want    string
	}{
		{host: "site.ton", allowed: true, want: "site.ton"},
		{host: "SITE.TON:80", allowed: true, want: "site.ton"},
		{host: "node.adnl.", allowed: true, want: "node.adnl"},
		{host: "node_name.adnl", allowed: true, want: "node_name.adnl"},
		{host: "sub.domain.ton", allowed: true, want: "sub.domain.ton"},
		{host: "COLLECTIBLE.T.ME:80", allowed: true, want: "collectible.t.me"},
		{host: "sub.collectible.t.me", allowed: true, want: "sub.collectible.t.me"},
		{host: ".ton", allowed: false},
		{host: "t.me", allowed: false},
		{host: "site.ton:443", allowed: false},
		{host: "site.ton.evil", allowed: false},
		{host: "site.t.me.evil", allowed: false},
		{host: "site..ton", allowed: false},
		{host: "localhost", allowed: false},
		{host: "127.0.0.1", allowed: false},
		{host: "[::1]", allowed: false},
		{host: "sité.ton", allowed: false},
	}

	for _, test := range tests {
		got, allowed := canonicalTONHost(test.host)
		if allowed != test.allowed || got != test.want {
			t.Fatalf("canonicalTONHost(%q) = (%q, %v), want (%q, %v)", test.host, got, allowed, test.want, test.allowed)
		}
	}
}

func TestApplyPrivacyHeaders(t *testing.T) {
	header := http.Header{
		"Referer":          {"http://source.ton/private/path"},
		"Origin":           {"http://source.ton"},
		"Sec-CH-UA":        {`"Chromium";v="150"`},
		"Sec-CH-UA-Model":  {"Pixel 9a"},
		"Device-Memory":    {"8"},
		"Viewport-Width":   {"412"},
		"X-Requested-With": {"com.tonnet.browser"},
		"X-Tonutils-Proxy": {"TONNET Mobile development"},
	}

	applyPrivacyHeaders(header)

	if got := header.Get("Referer"); got != "" {
		t.Fatalf("Referer was not removed: %q", got)
	}
	if got := header.Get("Origin"); got != "http://source.ton" {
		t.Fatalf("Origin changed unexpectedly: %q", got)
	}
	if got := header.Get("DNT"); got != "1" {
		t.Fatalf("DNT = %q, want 1", got)
	}
	if got := header.Get("Sec-GPC"); got != "1" {
		t.Fatalf("Sec-GPC = %q, want 1", got)
	}
	for _, name := range []string{
		"Sec-CH-UA",
		"Sec-CH-UA-Model",
		"Device-Memory",
		"Viewport-Width",
		"X-Requested-With",
		"X-Tonutils-Proxy",
	} {
		if got := header.Get(name); got != "" {
			t.Fatalf("%s was not removed: %q", name, got)
		}
	}
}

func TestHeaderFilteringPreservesOrigin(t *testing.T) {
	header := http.Header{
		"Connection":        {"keep-alive, X-Private-Hop"},
		"Keep-Alive":        {"timeout=5"},
		"Trailer":           {"Digest"},
		"X-Private-Hop":     {"private"},
		"Referer":           {"http://source.ton/private/path"},
		"Origin":            {"http://source.ton"},
		"X-Forwarded-For":   {"192.0.2.1"},
		"X-Forwarded-Proto": {"http"},
	}

	delHopHeaders(header)
	applyPrivacyHeaders(header)

	for _, name := range []string{
		"Connection",
		"Keep-Alive",
		"Trailer",
		"X-Private-Hop",
		"Referer",
		"X-Forwarded-For",
		"X-Forwarded-Proto",
	} {
		if got := header.Get(name); got != "" {
			t.Fatalf("%s was not removed: %q", name, got)
		}
	}
	if got := header.Get("Origin"); got != "http://source.ton" {
		t.Fatalf("Origin changed unexpectedly: %q", got)
	}
}
