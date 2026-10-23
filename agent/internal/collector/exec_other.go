//go:build !windows

// exec_other.go — fallback cho Linux/macOS, khong can an cua so.
package collector

import "os/exec"

// hiddenCmd tren non-Windows = exec.Command binh thuong.
func hiddenCmd(name string, args ...string) *exec.Cmd {
	return exec.Command(name, args...)
}
