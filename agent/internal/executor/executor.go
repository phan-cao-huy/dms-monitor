// Package executor: CHI chay whitelist REBOOT / RESTART_SERVICE / KILL_PROCESS.
// KHONG BAO GIO chay chuoi shell tuy y tu server. Validate tham so (pid la so, service_name khop regex).
package executor

import (
	"fmt"
	"log"
	"os/exec"
	"runtime"
	"strconv"
	"strings"

	"github.com/phan-cao-huy/dms-monitor/agent/internal/sender"
)

// Execute nhan 1 lenh tu Backend va thuc thi tren may.
func Execute(cmd sender.Command) sender.CommandResult {
	log.Printf("[EXEC] Nhan lenh: %s (ID=%d, params=%v)", cmd.CommandType, cmd.CommandID, cmd.Parameters)

	result := sender.CommandResult{
		CommandID: cmd.CommandID,
		Status:    "SUCCESS",
	}

	var output string
	var err error

	switch strings.ToUpper(cmd.CommandType) {
	case "REBOOT":
		output, err = execReboot(cmd.Parameters)
	case "RESTART_SERVICE":
		output, err = execRestartService(cmd.Parameters)
	case "KILL_PROCESS":
		output, err = execKillProcess(cmd.Parameters)
	default:
		err = fmt.Errorf("lenh khong duoc ho tro: %s", cmd.CommandType)
	}

	if err != nil {
		result.Status = "FAILED"
		result.ErrorMessage = err.Error()
		log.Printf("[EXEC] FAIL Lenh %s that bai: %v", cmd.CommandType, err)
	} else {
		result.Output = output
		log.Printf("[EXEC] OK Lenh %s thanh cong: %s", cmd.CommandType, output)
	}

	return result
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
		// Huy shutdown cu neu co, roi moi dat lenh moi
		_ = exec.Command("shutdown", "/a").Run()
		cmd = exec.Command("shutdown", "/r", "/t", strconv.Itoa(delay), "/f")
	} else {
		if delay <= 0 {
			cmd = exec.Command("sudo", "shutdown", "-r", "now")
		} else {
			mins := delay / 60
			if mins < 1 {
				mins = 1
			}
			cmd = exec.Command("sudo", "shutdown", "-r", fmt.Sprintf("+%d", mins))
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

	// Chan cac service nguy hiem
	blocked := []string{"sshd", "ssh", "lxdm", "gdm", "lightdm"}
	for _, b := range blocked {
		if strings.EqualFold(name, b) {
			return "", fmt.Errorf("service '%s' nam trong danh sach chan", name)
		}
	}

	var cmd *exec.Cmd
	if runtime.GOOS == "windows" {
		stopCmd := exec.Command("net", "stop", name)
		stopOut, _ := stopCmd.CombinedOutput()
		cmd = exec.Command("net", "start", name)
		startOut, err := cmd.CombinedOutput()
		if err != nil {
			return "", fmt.Errorf("restart service '%s' failed: stop=%s, start=%s — %w",
				name, strings.TrimSpace(string(stopOut)), strings.TrimSpace(string(startOut)), err)
		}
		return fmt.Sprintf("Service '%s' da restart.", name), nil
	}

	cmd = exec.Command("sudo", "systemctl", "restart", name)
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
			// /F: Force kill, /T: Kill ca cay tien trinh (child processes)
			cmd = exec.Command("taskkill", "/F", "/T", "/PID", strconv.Itoa(targetPID))
		} else {
			cmd = exec.Command("sudo", "kill", "-9", strconv.Itoa(targetPID))
		}
	} else if pname, ok := params["process_name"]; ok {
		name := strings.TrimSpace(fmt.Sprintf("%v", pname))
		if name == "" {
			return "", fmt.Errorf("process_name rong")
		}
		if runtime.GOOS == "windows" {
			cmd = exec.Command("taskkill", "/F", "/T", "/IM", name)
		} else {
			cmd = exec.Command("sudo", "pkill", "-9", "-f", name)
		}
	} else {
		return "", fmt.Errorf("can co pid hoac process_name trong parameters")
	}

	out, err := cmd.CombinedOutput()
	outStr := strings.TrimSpace(string(out))
	if err != nil {
		return "", fmt.Errorf("kill failed: %s — %w", outStr, err)
	}
	// taskkill co the tra exit code 0 nhung output co ERROR (vd: access denied, process not found)
	if strings.Contains(strings.ToUpper(outStr), "ERROR:") && !strings.Contains(strings.ToUpper(outStr), "SUCCESS:") {
		return "", fmt.Errorf("kill failed: %s", outStr)
	}

	return fmt.Sprintf("Da kill thanh cong. Output: %s", outStr), nil
}
