//go:build windows

package collector

import (
	"os"
	"strings"
	"testing"
)

func TestWindowsHealthState(t *testing.T) {
	if healthState(0) == nil || !*healthState(0) {
		t.Fatal("healthy")
	}
	if *healthState(1) || *healthState(2) {
		t.Fatal("warning/unhealthy")
	}
	if healthState(5) != nil {
		t.Fatal("unknown must remain nil")
	}
}

func TestWindowsEventXML(t *testing.T) {
	fixture := `<Event xmlns="http://schemas.microsoft.com/win/2004/08/events/event"><System><Provider Name="Test Provider"/><EventID>1000</EventID><Level>1</Level><EventRecordID>123</EventRecordID><TimeCreated SystemTime="2026-10-28T00:00:00.0000000Z"/></System><EventData><Data>Example</Data></EventData></Event>`
	event, err := parseEventXML(fixture, "System", func(string) string { return strings.Repeat("ệ", 1200) })
	if err != nil {
		t.Fatal(err)
	}
	if event.RecordID != 123 || event.Level != "CRITICAL" || len([]rune(event.Message)) != 1000 {
		t.Fatalf("invalid parsed event: %#v", event)
	}
}

func TestWindowsReadOnlyCollectors(t *testing.T) {
	if os.Getenv("DMS_NATIVE_TEST") != "1" {
		t.Skip("optional local native Windows read-only smoke test")
	}
	disks := collectDiskHealth()
	events, cursors, status := CollectEvents(map[string]EventCursor{})
	if len(disks) > 32 || len(events) > 50 {
		t.Fatal("collection limit exceeded")
	}
	t.Logf("Disk count=%d; baseline events=%d; cursor count=%d; log status=%v", len(disks), len(events), len(cursors), status)
}
