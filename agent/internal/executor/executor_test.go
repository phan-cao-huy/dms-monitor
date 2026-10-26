package executor

import (
	"os"
	"path/filepath"
	"testing"
)

func TestUninstallRejectsSharedDirectory(t *testing.T) {
	dir := t.TempDir()
	exe := filepath.Join(dir, "dms-agent.exe")
	sentinel := filepath.Join(dir, "personal.txt")
	if err := os.WriteFile(exe, []byte("agent"), 0600); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(sentinel, []byte("keep"), 0600); err != nil {
		t.Fatal(err)
	}
	if _, err := validateUninstallDir(exe); err == nil {
		t.Fatal("shared directory accepted")
	}
	if data, err := os.ReadFile(sentinel); err != nil || string(data) != "keep" {
		t.Fatal("uninstall validation modified unrelated files")
	}
}

func TestUninstallRejectsUnknownPath(t *testing.T) {
	if _, err := validateUninstallDir(filepath.Join(t.TempDir(), "missing.exe")); err == nil {
		t.Fatal("missing executable accepted")
	}
}
