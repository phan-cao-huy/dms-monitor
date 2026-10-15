// DMS Agent - entrypoint.
// Luong: load config -> collect static info -> moi 60s: collect -> POST /heartbeat
//        + moi 10s: GET /commands -> execute -> POST result.
package main

import (
	"log"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/phan-cao-huy/dms-monitor/agent/internal/collector"
	"github.com/phan-cao-huy/dms-monitor/agent/internal/config"
	"github.com/phan-cao-huy/dms-monitor/agent/internal/executor"
	"github.com/phan-cao-huy/dms-monitor/agent/internal/sender"
)

func main() {
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
		log.Printf("[INFO] Hostname: %s | OS: %s %s | CPU: %s (%d cores) | RAM: %d MB",
			staticInfo.Hostname, staticInfo.OSName, staticInfo.OSVersion,
			staticInfo.CPUModel, staticInfo.CPUCores, staticInfo.RAMTotalMB)
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

	log.Printf("[HEARTBEAT] CPU: %.1f%% | RAM: %.1f%% (%d MB) | Disk: %.1f%% | Net down %.1f up %.1f KB/s",
		metrics.CPUUsage, metrics.RAMUsage, metrics.RAMUsedMB,
		metrics.DiskUsage, metrics.NetInKbps, metrics.NetOutKbps)

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
