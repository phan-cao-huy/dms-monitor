//go:build !windows

// exec_other.go — fallback Linux/macOS cho hideWindow.
package executor

import "os/exec"

// hideWindow la no-op tren non-Windows.
func hideWindow(cmd *exec.Cmd) *exec.Cmd {
	return cmd
}
