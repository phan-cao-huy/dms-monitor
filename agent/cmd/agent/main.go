// DMS Agent - entrypoint.
// Build anẩn (khong co cua so CMD):
//   go build -ldflags="-H=windowsgui" -o dms-agent.exe ./cmd/agent
//
// Dung qua CLI:
//   dms-agent.exe -server http://IP:8000 -token dms_xxx -id 15
//   dms-agent.exe -install    <- cai lam Windows Service (chay ngam, tu dong khi boot)
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
const serviceDesc = "He thong giam sat may tinh tu xa - DMS Monitor Agent"

func main() {
	// --- Parse flags TRUOC khi goi flag.Parse() trong config.Load() ---
	installFlag := flag.Bool("install", false, "Cai DMS Agent lam Windows Service")
	uninstallFlag := flag.Bool("uninstall", false, "Go cai DMS Agent Windows Service")
	runAsService := flag.Bool("service", false, "Chay o che do Windows Service (dung noi bo)")

	// config.Load() se goi flag.Parse() ben trong, nen chung ta parse truoc
	// va truyen lai cho no bang args rut gon
	if len(os.Args) > 1 {
		switch os.Args[1] {
		case "-install", "--install":
			setupLogFile()
			if err := installService(); err != nil {
				fmt.Fprintf(os.Stderr, "Loi cai service: %v\n", err)
				os.Exit(1)
			}
			fmt.Println("Da cai DMS Agent thanh Windows Service thanh cong!")
			fmt.Println("Service se tu dong chay khi may tinh khoi dong.")
			fmt.Println("De kiem tra: services.msc -> tim 'DMS-Agent'")
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
	_ = runAsService

	// Setup log file truoc khi load config
	setupLogFile()

	log.SetFlags(log.Ldate | log.Ltime | log.Lshortfile)
	log.Println("===================================================")
	log.Println("  DMS Agent v" + collector.AgentVersion)
	log.Println("  He thong Giam sat May tinh Tu xa")
	log.Println("===================================================")

	// 1. Doc cau hinh
	cfg, err := config.Load()
	if err != nil {
		log.Fatalf("[FATAL] Loi cau hinh: %v", err)
	}
	log.Printf("[CONFIG] Backend URL    : %s", cfg.BackendURL)
	log.Printf("[CONFIG] Device ID      : %d", cfg.DeviceID)
	log.Printf("[CONFIG] Heartbeat moi  : %d giay", cfg.HeartbeatIntervalSeconds)
	log.Printf("[CONFIG] Command poll   : %d giay", cfg.CommandPollIntervalSeconds)

	// 2. Thu thap thong tin tinh (gui kem heartbeat lan dau)
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

	// 3. Tao HTTP client
	client := sender.NewClient(cfg)

	// 4. Kenh tin hieu de dung graceful
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)

	// 5. Goroutine: Heartbeat Loop
	go func() {
		firstRun := true
		ticker := time.NewTicker(time.Duration(cfg.HeartbeatIntervalSeconds) * time.Second)
		defer ticker.Stop()

		sendHeartbeat(client, cfg, staticInfo, firstRun)
		firstRun = false

		for range ticker.C {
			sendHeartbeat(client, cfg, nil, false)
		}
	}()

	// 6. Goroutine: Command Poll Loop
	go func() {
		ticker := time.NewTicker(time.Duration(cfg.CommandPollIntervalSeconds) * time.Second)
		defer ticker.Stop()

		for range ticker.C {
			pollCommands(client)
		}
	}()

	// 7. Cho tin hieu dung
	sig := <-quit
	log.Printf("[INFO] Nhan tin hieu %v — Dang dung Agent...", sig)
	log.Println("[INFO] Agent da dung. Goodbye!")
}

func sendHeartbeat(client *sender.Client, cfg *config.Config, staticInfo *collector.StaticInfo, isFirst bool) {
	log.Println("[HEARTBEAT] Dang thu thap du lieu he thong...")

	metrics, err := collector.CollectMetrics(cfg.ProcessTopN)
	if err != nil {
		log.Printf("[HEARTBEAT] FAIL Loi thu thap: %v", err)
		return
	}

	log.Printf("[HEARTBEAT] CPU: %.1f%% | RAM: %.1f%% (%d MB) | Disk chinh: %.1f%% | Net down %.1f up %.1f KB/s",
		metrics.CPUUsage, metrics.RAMUsage, metrics.RAMUsedMB,
		metrics.DiskUsage, metrics.NetInKbps, metrics.NetOutKbps)
	for _, d := range metrics.Disks {
		log.Printf("[HEARTBEAT]   O %s: %.1f/%.1f GB (%.1f%%)", d.Mountpoint, d.UsedGB, d.TotalGB, d.UsedPercent)
	}

	var si *collector.StaticInfo
	if isFirst {
		si = staticInfo
	}

	if err := client.SendHeartbeat(metrics, si); err != nil {
		log.Printf("[HEARTBEAT] FAIL Gui that bai: %v", err)
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

	// Ghi log ra ca file va stdout (neu co)
	multiWriter := io.MultiWriter(os.Stdout, logFile)
	log.SetOutput(multiWriter)
}

// installService cai DMS Agent lam Windows Service dung sc.exe.
func installService() error {
	exePath, err := os.Executable()
	if err != nil {
		return fmt.Errorf("khong lay duoc duong dan exe: %w", err)
	}
	// Giai phong absolute path
	exePath, err = filepath.Abs(exePath)
	if err != nil {
		return err
	}

	log.Printf("[SERVICE] Cai service '%s' tu: %s", serviceName, exePath)

	// sc create DMS-Agent binPath= "C:\path\dms-agent.exe -service" start= auto
	cmd := exec.Command("sc", "create", serviceName,
		"binPath=", fmt.Sprintf(`"%s" -service`, exePath),
		"DisplayName=", serviceDisplayName,
		"start=", "auto",
		"obj=", "LocalSystem",
	)
	out, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("sc create that bai: %s — %w", string(out), err)
	}

	// sc description
	descCmd := exec.Command("sc", "description", serviceName, serviceDesc)
	_ = descCmd.Run()

	// sc start
	startCmd := exec.Command("sc", "start", serviceName)
	startOut, startErr := startCmd.CombinedOutput()
	if startErr != nil {
		log.Printf("[SERVICE] Canh bao: Khong the khoi dong ngay: %s", string(startOut))
		log.Printf("[SERVICE] Service da cai, se tu dong chay khi reboot.")
	} else {
		log.Printf("[SERVICE] Service da khoi dong: %s", string(startOut))
	}

	return nil
}

// uninstallService go cai DMS Agent Windows Service.
func uninstallService() error {
	log.Printf("[SERVICE] Dung service '%s'...", serviceName)
	stopCmd := exec.Command("sc", "stop", serviceName)
	_ = stopCmd.Run()
	time.Sleep(2 * time.Second)

	log.Printf("[SERVICE] Xoa service '%s'...", serviceName)
	cmd := exec.Command("sc", "delete", serviceName)
	out, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("sc delete that bai: %s — %w", string(out), err)
	}
	return nil
}
