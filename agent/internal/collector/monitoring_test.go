package collector

import (
	"encoding/json"
	"path/filepath"
	"strings"
	"testing"
	"time"

	gnet "github.com/shirou/gopsutil/v3/net"
)

func TestConnectionLimitAndFilter(t *testing.T) {
	rows := []gnet.ConnectionStat{{Pid: 0, Status: "CLOSE_WAIT"}}
	for i := 0; i < 80; i++ {
		rows = append(rows, gnet.ConnectionStat{Pid: 0, Status: "ESTABLISHED"})
	}
	got := selectConnections(rows, 50)
	if len(got) != 50 {
		t.Fatalf("got %d connections", len(got))
	}
	for _, row := range got {
		if row.Status != "ESTABLISHED" {
			t.Fatal("unexpected status")
		}
	}
}

func TestEventTextIsLimitedByUnicodeCharacters(t *testing.T) {
	text := strings.Repeat("ệ", 1500)
	if len([]rune(truncateText(text, 1000))) != 1000 {
		t.Fatal("invalid Unicode truncation")
	}
	if len([]rune(eventFallback("source", 1, []string{text}))) > 1000 {
		t.Fatal("fallback too long")
	}
}

func TestEventCursorPersistsAcrossRestart(t *testing.T) {
	path := filepath.Join(t.TempDir(), "event-cursors.json")
	before := map[string]EventCursor{"System": {RecordID: 42, Time: time.Now().UTC()}}
	if err := SaveEventCursors(path, before); err != nil {
		t.Fatal(err)
	}
	got := LoadEventCursors(path)
	if got["System"].RecordID != 42 {
		t.Fatal("cursor not persisted")
	}
	before["System"] = EventCursor{RecordID: 43}
	if err := SaveEventCursors(path, before); err != nil {
		t.Fatal(err)
	}
	if LoadEventCursors(path)["System"].RecordID != 43 {
		t.Fatal("cursor not replaced")
	}
}

func TestUnknownDiskHealthSerializesAsNull(t *testing.T) {
	data, err := json.Marshal(DiskHealth{Name: "Unknown"})
	if err != nil || !strings.Contains(string(data), `"healthy":null`) {
		t.Fatalf("unknown health fabricated: %s", data)
	}
}
