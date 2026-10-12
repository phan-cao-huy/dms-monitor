// Package collector dung gopsutil lay CPU/RAM/Disk/Net/Uptime (+ static info khi khoi dong).
package collector

import (
	"fmt"
	"runtime"
	"sort"
	"time"

	"github.com/shirou/gopsutil/v3/cpu"
	"github.com/shirou/gopsutil/v3/disk"
	"github.com/shirou/gopsutil/v3/host"
	"github.com/shirou/gopsutil/v3/load"
	"github.com/shirou/gopsutil/v3/mem"
	"github.com/shirou/gopsutil/v3/net"
	"github.com/shirou/gopsutil/v3/process"
)

const AgentVersion = "1.0.0"

// Metrics chua du lieu 1 lan heartbeat — map 1:1 voi bang device_metrics.
type Metrics struct {
	CPUUsage      float64       `json:"cpu_usage"`
	CPULoad1m     float64       `json:"cpu_load_1m"`
	CPULoad5m     float64       `json:"cpu_load_5m"`
	RAMUsage      float64       `json:"ram_usage"`
	RAMUsedMB     int64         `json:"ram_used_mb"`
	DiskUsage     float64       `json:"disk_usage"`
	DiskUsedGB    float64       `json:"disk_used_gb"`
	NetInKbps     float64       `json:"net_in_kbps"`
	NetOutKbps    float64       `json:"net_out_kbps"`
	PingLatencyMs float64       `json:"ping_latency_ms"`
	UptimeSeconds int64         `json:"uptime_seconds"`
	AgentVersion  string        `json:"agent_version"`
	Processes     []ProcessInfo `json:"processes"`
}

// ProcessInfo chua thong tin 1 process.
type ProcessInfo struct {
	PID     int32   `json:"pid"`
	Name    string  `json:"name"`
	ExePath string  `json:"exe_path"`
	CPUPct  float64 `json:"cpu_pct"`
	RAMMB   float64 `json:"ram_mb"`
	User    string  `json:"user"`
}

// StaticInfo chua thong tin tinh cua may — chi gui 1 lan khi dang ky.
type StaticInfo struct {
	Hostname      string  `json:"hostname"`
	OSName        string  `json:"os_name"`
	OSVersion     string  `json:"os_version"`
	KernelVersion string  `json:"kernel_version"`
	Arch          string  `json:"arch"`
	CPUModel      string  `json:"cpu_model"`
	CPUCores      int     `json:"cpu_cores"`
	RAMTotalMB    int64   `json:"ram_total_mb"`
	DiskTotalGB   float64 `json:"disk_total_gb"`
	AgentVersion  string  `json:"agent_version"`
}

// CollectMetrics thu thap toan bo thong so he thong tai thoi diem goi.
func CollectMetrics(topN int) (*Metrics, error) {
	m := &Metrics{AgentVersion: AgentVersion}

	// CPU Usage (do trong 1 giay)
	cpuPcts, err := cpu.Percent(1*time.Second, false)
	if err == nil && len(cpuPcts) > 0 {
		m.CPUUsage = round2(cpuPcts[0])
	}

	// CPU Load Average (Linux/macOS only)
	if runtime.GOOS != "windows" {
		if avg, err := load.Avg(); err == nil {
			m.CPULoad1m = round2(avg.Load1)
			m.CPULoad5m = round2(avg.Load5)
		}
	}

	// RAM
	if vm, err := mem.VirtualMemory(); err == nil {
		m.RAMUsage = round2(vm.UsedPercent)
		m.RAMUsedMB = int64(vm.Used / 1024 / 1024)
	}

	// Disk (o root: C:\ tren Windows, / tren Linux)
	rootPath := "/"
	if runtime.GOOS == "windows" {
		rootPath = "C:\\"
	}
	if du, err := disk.Usage(rootPath); err == nil {
		m.DiskUsage = round2(du.UsedPercent)
		m.DiskUsedGB = round2(float64(du.Used) / 1024 / 1024 / 1024)
	}

	// Network I/O (delta trong 1 giay)
	m.NetInKbps, m.NetOutKbps = collectNetSpeed()

	// Uptime
	if uptime, err := host.Uptime(); err == nil {
		m.UptimeSeconds = int64(uptime)
	}

	// Top N Process
	m.Processes = collectTopProcesses(topN)

	return m, nil
}

// CollectStaticInfo thu thap thong tin phan cung (chi can goi 1 lan).
func CollectStaticInfo() (*StaticInfo, error) {
	info := &StaticInfo{
		Arch:         runtime.GOARCH,
		AgentVersion: AgentVersion,
	}

	if h, err := host.Info(); err == nil {
		info.Hostname = h.Hostname
		info.OSName = h.Platform
		info.OSVersion = h.PlatformVersion
		info.KernelVersion = h.KernelVersion
	}

	if cpus, err := cpu.Info(); err == nil && len(cpus) > 0 {
		info.CPUModel = cpus[0].ModelName
	}
	if cores, err := cpu.Counts(true); err == nil {
		info.CPUCores = cores
	}

	if vm, err := mem.VirtualMemory(); err == nil {
		info.RAMTotalMB = int64(vm.Total / 1024 / 1024)
	}

	rootPath := "/"
	if runtime.GOOS == "windows" {
		rootPath = "C:\\"
	}
	if du, err := disk.Usage(rootPath); err == nil {
		info.DiskTotalGB = round2(float64(du.Total) / 1024 / 1024 / 1024)
	}

	return info, nil
}

// collectNetSpeed do toc do mang thuc te bang cach lay delta trong 1 giay.
func collectNetSpeed() (inKbps, outKbps float64) {
	counters1, err := net.IOCounters(false)
	if err != nil || len(counters1) == 0 {
		return 0, 0
	}
	time.Sleep(1 * time.Second)
	counters2, err := net.IOCounters(false)
	if err != nil || len(counters2) == 0 {
		return 0, 0
	}
	deltaIn := counters2[0].BytesRecv - counters1[0].BytesRecv
	deltaOut := counters2[0].BytesSent - counters1[0].BytesSent
	return round2(float64(deltaIn) / 1024), round2(float64(deltaOut) / 1024)
}

// collectTopProcesses lay top N process theo CPU%.
func collectTopProcesses(topN int) []ProcessInfo {
	procs, err := process.Processes()
	if err != nil {
		return nil
	}

	var infos []ProcessInfo
	for _, p := range procs {
		name, _ := p.Name()
		if name == "" {
			continue
		}
		cpuPct, _ := p.CPUPercent()
		memInfo, _ := p.MemoryInfo()
		exePath, _ := p.Exe()
		user, _ := p.Username()

		ramMB := float64(0)
		if memInfo != nil {
			ramMB = round2(float64(memInfo.RSS) / 1024 / 1024)
		}
		infos = append(infos, ProcessInfo{
			PID: p.Pid, Name: name, ExePath: exePath,
			CPUPct: round2(cpuPct), RAMMB: ramMB, User: user,
		})
	}

	sort.Slice(infos, func(i, j int) bool { return infos[i].CPUPct > infos[j].CPUPct })
	if len(infos) > topN {
		infos = infos[:topN]
	}
	return infos
}

func round2(v float64) float64 { return float64(int(v*100)) / 100 }

// FormatBytes chuyen bytes thanh chuoi de doc (cho log).
func FormatBytes(b uint64) string {
	const unit = 1024
	if b < unit {
		return fmt.Sprintf("%d B", b)
	}
	div, exp := uint64(unit), 0
	for n := b / unit; n >= unit; n /= unit {
		div *= unit
		exp++
	}
	return fmt.Sprintf("%.1f %cB", float64(b)/float64(div), "KMGTPE"[exp])
}
