package collector

import (
	"os"
	"path/filepath"
	"testing"
)

func TestWatchProcessesNotLimitedToTopList(t *testing.T) {
	ownName := filepath.Base(os.Args[0])
	missing := "dms-impossible-process-760723.exe"
	SetWatchConfig(WatchConfig{Processes: []string{ownName, missing}})
	defer SetWatchConfig(WatchConfig{})
	status := collectWatchStatus()
	if !status["processes"][ownName] {
		t.Fatal("running process not detected")
	}
	if status["processes"][missing] {
		t.Fatal("missing process reported as running")
	}
}
