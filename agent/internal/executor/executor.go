// Package executor: chay whitelist REBOOT / RESTART_SERVICE / KILL_PROCESS / PAUSE_MONITORING / RESUME_MONITORING / UNINSTALL.
// KHONG BAO GIO chay chuoi shell tuy y tu server. Validate tham so.
package executor

import (
	"fmt"
	"log"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"sync/atomic"

	"github.com/phan-cao-huy/dms-monitor/agent/internal/sender"
)

var monitoringPaused atomic.Bool

// IsMonitoringPaused kiem tra xem agent co dang tam dung theo doi khong.
func IsMonitoringPaused() bool {
	return monitoringPaused.Load()
}

// SetMonitoringPaused bat hoac tat che do tam dung theo doi.
func SetMonitoringPaused(paused bool) {
	monitoringPaused.Store(paused)
	if paused {
		log.Println("[EXEC] Da CHUYEN SANG CHE DO TAM DUNG THEO DOI (Pause)")
	} else {
		log.Println("[EXEC] Da KICH HOAT LAI THEO DOI (Resume)")
	}
}

// Execute nhan 1 lenh tu Backend va thuc thi tren may.
func Execute(cmd sender.Command) sender.CommandResult {
	log.Printf("[EXEC] Nhan lenh: %s (ID=%d, params=%v)", cmd.CommandType, cmd.CommandID, cmd.Parameters)

	result := sender.CommandResult{
		CommandID: cmd.CommandID,
		Status:    "SUCCESS",
	}

	var output string
	var err error
	var afterReport func() error

	switch strings.ToUpper(cmd.CommandType) {
	case "REBOOT":
		output, err = execReboot(cmd.Parameters)
	case "RESTART_SERVICE":
		output, err = execRestartService(cmd.Parameters)
	case "KILL_PROCESS":
		output, err = execKillProcess(cmd.Parameters)
	case "PAUSE_MONITORING":
		SetMonitoringPaused(true)
		output = "Da tam dung theo doi tien trinh va tai nguyen may tinh"
	case "RESUME_MONITORING":
		SetMonitoringPaused(false)
		output = "Da kich hoat lai theo doi may tinh thanh cong"
	case "UNINSTALL":
		output, afterReport, err = execUninstall(cmd.Parameters)
	default:
		err = fmt.Errorf("lenh khong duoc ho tro: %s", cmd.CommandType)
	}

	if err != nil {
		result.Status = "FAILED"
		result.ErrorMessage = err.Error()
		log.Printf("[EXEC] FAIL Lenh %s that bai: %v", cmd.CommandType, err)
	} else {
		result.AfterReport = afterReport
		result.Output = output
		log.Printf("[EXEC] OK Lenh %s thanh cong: %s", cmd.CommandType, output)
	}

	return result
}

// hw tao exec.Cmd an cua so va ap dung hideWindow (dinh nghia trong exec_windows.go / exec_other.go).
func hw(name string, args ...string) *exec.Cmd {
	return hideWindow(exec.Command(name, args...))
}

func execReboot(params map[string]interface{}) (string, error) {
	delay := 10
	if d, ok := params["delay_seconds"]; ok {
		switch v := d.(type) {
		case float64:
			delay = int(v)
		case int:
			delay = v
		case int64:
			delay = int(v)
		case string:
			if parsed, err := strconv.Atoi(v); err == nil && parsed >= 0 {
				delay = parsed
			}
		}
	}

	var cmd *exec.Cmd
	if runtime.GOOS == "windows" {
		_ = hw("shutdown", "/a").Run()
		cmd = hw("shutdown", "/r", "/t", strconv.Itoa(delay), "/f")
	} else {
		if delay <= 0 {
			cmd = hw("sudo", "shutdown", "-r", "now")
		} else {
			mins := delay / 60
			if mins < 1 {
				mins = 1
			}
			cmd = hw("sudo", "shutdown", "-r", fmt.Sprintf("+%d", mins))
		}
	}

	out, err := cmd.CombinedOutput()
	if err != nil {
		return "", fmt.Errorf("reboot failed: %s — %w", strings.TrimSpace(string(out)), err)
	}
	return fmt.Sprintf("Reboot sau %d giay. Output: %s", delay, strings.TrimSpace(string(out))), nil
}

func execRestartService(params map[string]interface{}) (string, error) {
	serviceName, ok := params["service_name"]
	if !ok {
		return "", fmt.Errorf("thieu service_name trong parameters")
	}
	name := fmt.Sprintf("%v", serviceName)

	blocked := []string{"sshd", "ssh", "lxdm", "gdm", "lightdm"}
	for _, b := range blocked {
		if strings.EqualFold(name, b) {
			return "", fmt.Errorf("service '%s' nam trong danh sach chan", name)
		}
	}

	if runtime.GOOS == "windows" {
		stopCmd := hw("net", "stop", name)
		stopOut, _ := stopCmd.CombinedOutput()
		startCmd := hw("net", "start", name)
		startOut, err := startCmd.CombinedOutput()
		if err != nil {
			return "", fmt.Errorf("restart service '%s' failed: stop=%s, start=%s — %w",
				name, strings.TrimSpace(string(stopOut)), strings.TrimSpace(string(startOut)), err)
		}
		return fmt.Sprintf("Service '%s' da restart.", name), nil
	}

	cmd := hw("sudo", "systemctl", "restart", name)
	out, err := cmd.CombinedOutput()
	if err != nil {
		return "", fmt.Errorf("restart service '%s' failed: %s — %w", name, string(out), err)
	}
	return fmt.Sprintf("Service '%s' da restart thanh cong.", name), nil
}

func parsePID(pidRaw interface{}) int {
	switch v := pidRaw.(type) {
	case float64:
		return int(v)
	case int:
		return v
	case int64:
		return int(v)
	case string:
		p, _ := strconv.Atoi(strings.TrimSpace(v))
		return p
	default:
		p, _ := strconv.Atoi(fmt.Sprintf("%v", v))
		return p
	}
}

func execKillProcess(params map[string]interface{}) (string, error) {
	var cmd *exec.Cmd
	var targetPID int

	if pidRaw, ok := params["pid"]; ok {
		targetPID = parsePID(pidRaw)
		if targetPID <= 0 {
			return "", fmt.Errorf("PID khong hop le: %v", pidRaw)
		}
		if runtime.GOOS == "windows" {
			cmd = hw("taskkill", "/F", "/T", "/PID", strconv.Itoa(targetPID))
		} else {
			cmd = hw("sudo", "kill", "-9", strconv.Itoa(targetPID))
		}
	} else if pname, ok := params["process_name"]; ok {
		name := strings.TrimSpace(fmt.Sprintf("%v", pname))
		if name == "" {
			return "", fmt.Errorf("process_name rong")
		}
		if runtime.GOOS == "windows" {
			cmd = hw("taskkill", "/F", "/T", "/IM", name)
		} else {
			cmd = hw("sudo", "pkill", "-9", "-f", name)
		}
	} else {
		return "", fmt.Errorf("can co pid hoac process_name trong parameters")
	}

	out, err := cmd.CombinedOutput()
	outStr := strings.TrimSpace(string(out))
	if err != nil {
		return "", fmt.Errorf("kill failed: %s — %w", outStr, err)
	}
	if strings.Contains(strings.ToUpper(outStr), "ERROR:") && !strings.Contains(strings.ToUpper(outStr), "SUCCESS:") {
		return "", fmt.Errorf("kill failed: %s", outStr)
	}

	return fmt.Sprintf("Da kill thanh cong. Output: %s", outStr), nil
}

// execUninstall go cai Windows Service / Service va tu huy file tren PC.
func execUninstall(params map[string]interface{}) (string, func() error, error) {
	log.Println("[EXEC] Nhan lenh UNINSTALL — Bat dau quy trinh go cai dat va xoa sach file...")

	exePath, err := os.Executable()
	if err != nil {
		return "", nil, fmt.Errorf("cannot determine executable path: %w", err)
	}
	exeDir, err := validateUninstallDir(exePath)
	if err != nil {
		return "", nil, err
	}

	if runtime.GOOS == "windows" {
		batchContent := fmt.Sprintf(`@echo off
timeout /t 3 /nobreak > NUL
sc.exe stop DMS-Agent > NUL 2>&1
sc.exe delete DMS-Agent > NUL 2>&1
taskkill /F /IM dms-agent.exe > NUL 2>&1
taskkill /F /IM dms-agent-debug.exe > NUL 2>&1
reg delete "HKCU\Software\Microsoft\Windows\CurrentVersion\Run" /v "DMSAgent" /f > NUL 2>&1
if exist "%s" rmdir /S /Q "%s" > NUL 2>&1
del "%%~f0" > NUL 2>&1
`, exeDir, exeDir)

		scriptPath, err := writeUninstallScript(".bat", batchContent)
		if err != nil {
			return "", nil, fmt.Errorf("khong the tao script go cai dat: %w", err)
		}

		return fmt.Sprintf("Uninstall scheduled for %s after result acknowledgement", exeDir), func() error {
			return hw("cmd.exe", "/c", scriptPath).Start()
		}, nil
	}

	// Fallback Linux
	shContent := fmt.Sprintf(`#!/bin/bash
sleep 3
systemctl stop dms-agent > /dev/null 2>&1
systemctl disable dms-agent > /dev/null 2>&1
rm -f /etc/systemd/system/dms-agent.service
systemctl daemon-reload > /dev/null 2>&1
rm -rf "%s" > /dev/null 2>&1
rm -f "$0"
`, exeDir)
	scriptPath, err := writeUninstallScript(".sh", shContent)
	if err != nil {
		return "", nil, err
	}
	return fmt.Sprintf("Uninstall scheduled for %s after result acknowledgement", exeDir), func() error {
		return hw("bash", scriptPath).Start()
	}, nil
}

func validateUninstallDir(exePath string) (string, error) {
	resolved, err := filepath.EvalSymlinks(exePath)
	if err != nil {
		return "", fmt.Errorf("resolve agent path: %w", err)
	}
	dir := filepath.Clean(filepath.Dir(resolved))
	expected := "/opt/dms-agent"
	if runtime.GOOS == "windows" {
		expected = `C:\DMS-Agent`
	}
	if dir != filepath.Clean(expected) && (runtime.GOOS != "windows" || !strings.EqualFold(dir, filepath.Clean(expected))) {
		return "", fmt.Errorf("refusing recursive uninstall outside dedicated directory: %s", dir)
	}
	return dir, nil
}

func writeUninstallScript(extension, content string) (string, error) {
	file, err := os.CreateTemp("", "dms-agent-uninstall-*"+extension)
	if err != nil {
		return "", err
	}
	name := file.Name()
	_, writeErr := file.WriteString(content)
	closeErr := file.Close()
	if writeErr != nil {
		_ = os.Remove(name)
		return "", writeErr
	}
	if closeErr != nil {
		_ = os.Remove(name)
		return "", closeErr
	}
	return name, nil
}
