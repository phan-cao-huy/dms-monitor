//go:build windows

package executor

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strconv"
	"testing"
	"time"

	"golang.org/x/sys/windows"
)

// This fixture uses a uniquely named copy of the test binary, never a user app.
func TestEndTaskFixture(t *testing.T) {
	dir := os.Getenv("DMS_ENDTASK_FIXTURE_DIR")
	if dir == "" {
		return
	}
	if os.Getenv("DMS_ENDTASK_FIXTURE_CHILD") == "1" {
		if err := os.WriteFile(filepath.Join(dir, "child.pid"), []byte(strconv.Itoa(os.Getpid())), 0600); err != nil {
			t.Fatal(err)
		}
	} else {
		exe, err := os.Executable()
		if err != nil {
			t.Fatal(err)
		}
		child := hw(exe, "-test.run=^TestEndTaskFixture$")
		child.Env = append(os.Environ(), "DMS_ENDTASK_FIXTURE_CHILD=1")
		if err = child.Start(); err != nil {
			t.Fatal(err)
		}
	}
	for {
		time.Sleep(time.Second)
	}
}

func TestEndTaskForceClosesDisposableApplicationAndChild(t *testing.T) {
	dir := t.TempDir()
	exe, err := os.Executable()
	if err != nil {
		t.Fatal(err)
	}
	name := fmt.Sprintf("dms-endtask-%d.exe", time.Now().UnixNano())
	target := filepath.Join(dir, name)
	src, err := os.Open(exe)
	if err != nil {
		t.Fatal(err)
	}
	defer src.Close()
	dst, err := os.Create(target)
	if err != nil {
		t.Fatal(err)
	}
	_, err = io.Copy(dst, src)
	dst.Close()
	if err != nil {
		t.Fatal(err)
	}
	cmd := hw(target, "-test.run=^TestEndTaskFixture$")
	cmd.Env = append(os.Environ(), "DMS_ENDTASK_FIXTURE_DIR="+dir)
	if err = cmd.Start(); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = hw("taskkill", "/F", "/T", "/IM", name).Run(); _ = cmd.Wait() })
	var childPID int
	deadline := time.Now().Add(10 * time.Second)
	for time.Now().Before(deadline) {
		data, e := os.ReadFile(filepath.Join(dir, "child.pid"))
		if e == nil {
			childPID, _ = strconv.Atoi(string(data))
			if childPID > 0 {
				break
			}
		}
		time.Sleep(50 * time.Millisecond)
	}
	if childPID == 0 {
		t.Fatal("fixture child did not start")
	}
	parentHandle, err := windows.OpenProcess(windows.SYNCHRONIZE, false, uint32(cmd.Process.Pid))
	if err != nil {
		t.Fatal(err)
	}
	defer windows.CloseHandle(parentHandle)
	childHandle, err := windows.OpenProcess(windows.SYNCHRONIZE, false, uint32(childPID))
	if err != nil {
		t.Fatal(err)
	}
	defer windows.CloseHandle(childHandle)
	if _, err = execKillProcess(map[string]interface{}{"process_name": name}); err != nil {
		t.Fatal(err)
	}
	for _, handle := range []windows.Handle{parentHandle, childHandle} {
		result, err := windows.WaitForSingleObject(handle, 3000)
		if err != nil || result != windows.WAIT_OBJECT_0 {
			t.Fatalf("process was not terminated: result=%v err=%v", result, err)
		}
	}
}
