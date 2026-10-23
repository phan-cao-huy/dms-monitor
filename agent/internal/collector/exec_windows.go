//go:build windows

// exec_windows.go — tao exec.Cmd an cua so console tren Windows.
// BUILD TAG: chi compile tren Windows.
package collector

import (
	"os/exec"
	"syscall"
)

// hiddenCmd tao exec.Cmd voi flag CREATE_NO_WINDOW de khong nhap nhay cua so CMD.
// Su dung thay cho exec.Command() tren Windows khi chay chuong trinh con.
func hiddenCmd(name string, args ...string) *exec.Cmd {
	cmd := exec.Command(name, args...)
	cmd.SysProcAttr = &syscall.SysProcAttr{
		HideWindow:    true,
		CreationFlags: 0x08000000, // CREATE_NO_WINDOW
	}
	return cmd
}
