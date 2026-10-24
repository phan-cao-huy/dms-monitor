// DMS Agent - entrypoint.
// Build an (khong co cua so console):
//   go build -ldflags="-s -w -H=windowsgui" -o dms-agent.exe ./cmd/agent
//
// Dung qua CLI:
//   dms-agent.exe -server http://IP:8000 -token dms_xxx -id 15
//   dms-agent.exe -install    <- cai lam Windows Service (chay ngam trong Session 0, boot cung Windows)
//   dms-agent.exe -uninstall  <- go cai Windows Service
package main

import (
	"flag"
	"fmt"
	"io"
	"log"
	"os"
	"os/exec"
	"os/signal"
	"path/filepath"
	"syscall"
	"time"

	"github.com/phan-cao-huy/dms-monitor/agent/internal/collector"
	"github.com/phan-cao-huy/dms-monitor/agent/internal/config"
	"github.com/phan-cao-huy/dms-monitor/agent/internal/executor"
	"github.com/phan-cao-huy/dms-monitor/agent/internal/sender"
)

const serviceName = "DMS-Agent"
const serviceDisplayName = "DMS Monitor Agent"
const serviceDesc = "He thong giam sat may tinh tu xa - DMS Monitor Agent v1.1.0"

func main() {
	// Ghi log ra file dms-agent.log cung thu muc voi exe
	setupLogFile()

	// 1. Kiem tra xem co phai duoc khoi dong boi Windows Service Control Manager khong
	if isWindowsService() {
		log.Println("[INIT] Khoi dong duoi dang Windows Service (Session 0)...")
		if err := runWindowsService(); err != nil {
			log.Fatalf("[FATAL] Loi chay Windows Service: %v", err)
		}
		return
	}

	// 2. Parse flags CLI neu chay thu cong
	installFlag := flag.Bool("install", false, "Cai DMS Agent lam Windows Service")
	uninstallFlag := flag.Bool("uninstall", false, "Go cai DMS Agent Windows Service")

	if len(os.Args) > 1 {
		switch os.Args[1] {
		case "-install", "--install":
			if err := installService(); err != nil {
				fmt.Fprintf(os.Stderr, "Loi cai service: %v\n", err)
				os.Exit(1)
			}
			fmt.Println("Da cai DMS Agent thanh Windows Service thanh cong!")
			fmt.Println("Service se tu dong chay ngam moi khi may tinh khoi dong.")
			return
		case "-uninstall", "--uninstall":
			if err := uninstallService(); err != nil {
				fmt.Fprintf(os.Stderr, "Loi go cai service: %v\n", err)
				os.Exit(1)
			}
			fmt.Println("Da go cai DMS Agent Service thanh cong!")
			return
		}
	}

	_ = installFlag
	_ = uninstallFlag

	log.SetFlags(log.Ldate | log.Ltime | log.Lshortfile)
	log.Println("===================================================")
	log.Println("  DMS Agent v" + collector.AgentVersion)
	log.Println("  He thong Giam sat May tinh Tu xa")
	log.Println("===================================================")

	// 3. Chay o che do Interactive (CLI / Test)
	stopCh := make(chan struct{})
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)

	go runAgent(stopCh)

	sig := <-quit
	log.Printf("[INFO] Nhan tin hieu %v — Dang dung Agent...", sig)
	close(stopCh)
	time.Sleep(500 * time.Millisecond)
	log.Println("[INFO] Agent da dung. Goodbye!")
}

// runAgent chua vong lap thu thap heartbeat va thuc thi lenh.
func runAgent(stopCh <-chan struct{}) {
	cfg, err := config.Load()
	if err != nil {
		log.Fatalf("[FATAL] Loi cau hinh: %v", err)
	}
	log.Printf("[CONFIG] Backend URL    : %s", cfg.BackendURL)
	log.Printf("[CONFIG] Device ID      : %d", cfg.DeviceID)
	log.Printf("[CONFIG] Heartbeat moi  : %d giay", cfg.HeartbeatIntervalSeconds)
	log.Printf("[CONFIG] Command poll   : %d giay", cfg.CommandPollIntervalSeconds)

	// Thu thap thong tin tinh (gui kem heartbeat dau tien)
	staticInfo, err := collector.CollectStaticInfo()
	if err != nil {
		log.Printf("[WARN] Khong lay duoc thong tin tinh: %v", err)
	} else {
		log.Printf("[INFO] Hostname: %s | OS: %s %s | CPU: %s (%d cores) | RAM: %d MB | So o cung: %d",
			staticInfo.Hostname, staticInfo.OSName, staticInfo.OSVersion,
			staticInfo.CPUModel, staticInfo.CPUCores, staticInfo.RAMTotalMB, len(staticInfo.Disks))
		for i, d := range staticInfo.Disks {
			log.Printf("[INFO]   O %d: %s (%.1f GB / %.1f GB, %.1f%%)",
				i+1, d.Mountpoint, d.UsedGB, d.TotalGB, d.UsedPercent)
		}
	}

	client := sender.NewClient(cfg)

	// Heartbeat ticker
	heartbeatTicker := time.NewTicker(time.Duration(cfg.HeartbeatIntervalSeconds) * time.Second)
	defer heartbeatTicker.Stop()

	// Gui heartbeat dau tien ngay khi start
	sendHeartbeat(client, cfg, staticInfo, true)

	// Command poll ticker
	pollTicker := time.NewTicker(time.Duration(cfg.CommandPollIntervalSeconds) * time.Second)
	defer pollTicker.Stop()

	for {
		select {
		case <-stopCh:
			log.Println("[INFO] Dung cac tien trinh thu thap Agent...")
			return
		case <-heartbeatTicker.C:
			sendHeartbeat(client, cfg, nil, false)
		case <-pollTicker.C:
			pollCommands(client)
		}
	}
}

func sendHeartbeat(client *sender.Client, cfg *config.Config, staticInfo *collector.StaticInfo, isFirst bool) {
	if executor.IsMonitoringPaused() {
		log.Println("[HEARTBEAT] Che do TAM DUNG (Pause) dang bat — bo qua thu thap tien trinh va metric")
		minimalMetrics := &collector.Metrics{
			Processes: nil,
		}
		var si *collector.StaticInfo
		if isFirst {
			si = staticInfo
		}
		resp, err := client.SendHeartbeat(minimalMetrics, si)
		if err != nil {
			log.Printf("[HEARTBEAT] FAIL Gui that bai: %v", err)
			return
		}
		if resp != nil && resp.MonitoringEnabled != nil && *resp.MonitoringEnabled {
			executor.SetMonitoringPaused(false)
		}
		return
	}

	log.Println("[HEARTBEAT] Dang thu thap du lieu he thong...")

	metrics, err := collector.CollectMetrics(cfg.ProcessTopN)
	if err != nil {
		log.Printf("[HEARTBEAT] FAIL Loi thu thap: %v", err)
		return
	}

	log.Printf("[HEARTBEAT] CPU: %.1f%% | RAM: %.1f%% (%d MB) | Disk: %.1f%% | Net down %.1f up %.1f KB/s",
		metrics.CPUUsage, metrics.RAMUsage, metrics.RAMUsedMB,
		metrics.DiskUsage, metrics.NetInKbps, metrics.NetOutKbps)
	for _, d := range metrics.Disks {
		log.Printf("[HEARTBEAT]   O %s: %.1f/%.1f GB (%.1f%%)", d.Mountpoint, d.UsedGB, d.TotalGB, d.UsedPercent)
	}

	var si *collector.StaticInfo
	if isFirst {
		si = staticInfo
	}

	resp, err := client.SendHeartbeat(metrics, si)
	if err != nil {
		log.Printf("[HEARTBEAT] FAIL Gui that bai: %v", err)
		return
	}
	if resp != nil && resp.MonitoringEnabled != nil && !*resp.MonitoringEnabled {
		executor.SetMonitoringPaused(true)
	}
}

func pollCommands(client *sender.Client) {
	commands, err := client.FetchCommands()
	if err != nil {
		log.Printf("[CMD-POLL] FAIL Loi lay lenh: %v", err)
		return
	}

	if len(commands) == 0 {
		return
	}

	log.Printf("[CMD-POLL] Nhan %d lenh moi tu Backend", len(commands))

	for _, cmd := range commands {
		result := executor.Execute(cmd)
		if err := client.ReportCommandResult(result); err != nil {
			log.Printf("[CMD-POLL] FAIL Khong gui duoc ket qua lenh #%d: %v", cmd.CommandID, err)
		}
	}
}

// setupLogFile chuyen log ra file dms-agent.log trong cung thu muc voi exe.
func setupLogFile() {
	exePath, err := os.Executable()
	if err != nil {
		return
	}
	exeDir := filepath.Dir(exePath)
	logPath := filepath.Join(exeDir, "dms-agent.log")

	logFile, err := os.OpenFile(logPath, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0644)
	if err != nil {
		return
	}

	multiWriter := io.MultiWriter(os.Stdout, logFile)
	log.SetOutput(multiWriter)
}

// installService cai DMS Agent lam Windows Service dung sc.exe.
func installService() error {
	exePath, err := os.Executable()
	if err != nil {
		return fmt.Errorf("khong lay duoc duong dan exe: %w", err)
	}
	exePath, err = filepath.Abs(exePath)
	if err != nil {
		return err
	}

	log.Printf("[SERVICE] Cai service '%s' tu: %s", serviceName, exePath)

	cmd := exec.Command("sc", "create", serviceName,
		"binPath=", fmt.Sprintf(`"%s"`, exePath),
		"DisplayName=", serviceDisplayName,
		"start=", "auto",
		"obj=", "LocalSystem",
	)
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: 0x08000000}
	out, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("sc create that bai: %s — %w", string(out), err)
	}

	descCmd := exec.Command("sc", "description", serviceName, serviceDesc)
	descCmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: 0x08000000}
	_ = descCmd.Run()

	failCmd := exec.Command("sc", "failure", serviceName, "reset=", "86400", "actions=", "restart/5000/restart/5000/restart/5000")
	failCmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: 0x08000000}
	_ = failCmd.Run()

	startCmd := exec.Command("sc", "start", serviceName)
	startCmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: 0x08000000}
	startOut, startErr := startCmd.CombinedOutput()
	if startErr != nil {
		log.Printf("[SERVICE] Canh bao: Khong the khoi dong ngay: %s", string(startOut))
	} else {
		log.Printf("[SERVICE] Service da khoi dong: %s", string(startOut))
	}

	return nil
}

// uninstallService go cai DMS Agent Windows Service.
func uninstallService() error {
	log.Printf("[SERVICE] Dung service '%s'...", serviceName)
	stopCmd := exec.Command("sc", "stop", serviceName)
	stopCmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: 0x08000000}
	_ = stopCmd.Run()
	time.Sleep(2 * time.Second)

	log.Printf("[SERVICE] Xoa service '%s'...", serviceName)
	cmd := exec.Command("sc", "delete", serviceName)
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: 0x08000000}
	out, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("sc delete that bai: %s — %w", string(out), err)
	}
	return nil
}
