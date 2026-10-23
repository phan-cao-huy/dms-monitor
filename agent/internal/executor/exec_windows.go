//go:build windows

// exec_windows.go — helper an cua so CMD cho cac lenh thuc thi tren Windows.
package executor

import (
	"os/exec"
	"syscall"
)

const createNoWindow = 0x08000000

// hideWindow ap dung CREATE_NO_WINDOW vao bat ky exec.Cmd nao.
func hideWindow(cmd *exec.Cmd) *exec.Cmd {
	if cmd.SysProcAttr == nil {
		cmd.SysProcAttr = &syscall.SysProcAttr{}
	}
	cmd.SysProcAttr.HideWindow = true
	cmd.SysProcAttr.CreationFlags |= createNoWindow
	return cmd
}
