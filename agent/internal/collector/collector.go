// Package collector dung gopsutil lay CPU/RAM/Disk/Net/Uptime (+ static info khi khoi dong).
package collector

import (
	"fmt"
	"runtime"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/shirou/gopsutil/v3/cpu"
	"github.com/shirou/gopsutil/v3/disk"
	"github.com/shirou/gopsutil/v3/host"
	"github.com/shirou/gopsutil/v3/load"
	"github.com/shirou/gopsutil/v3/mem"
	"github.com/shirou/gopsutil/v3/net"
	"github.com/shirou/gopsutil/v3/process"
)

const AgentVersion = "1.2.0"

// DiskInfo chua thong tin 1 o cung / partition.
type DiskInfo struct {
	Mountpoint  string  `json:"mountpoint"`
	Fstype      string  `json:"fstype"`
	TotalGB     float64 `json:"total_gb"`
	UsedGB      float64 `json:"used_gb"`
	FreeGB      float64 `json:"free_gb"`
	UsedPercent float64 `json:"used_percent"`
}

// Metrics chua du lieu 1 lan heartbeat — map 1:1 voi bang device_metrics.
type Metrics struct {
	WatchStatus   map[string]map[string]bool `json:"watch_status,omitempty"`
	CPUUsage      float64                    `json:"cpu_usage"`
	CPULoad1m     float64                    `json:"cpu_load_1m"`
	CPULoad5m     float64                    `json:"cpu_load_5m"`
	RAMUsage      float64                    `json:"ram_usage"`
	RAMUsedMB     int64                      `json:"ram_used_mb"`
	DiskUsage     float64                    `json:"disk_usage"`   // o chinh (C:\ hoac /)
	DiskUsedGB    float64                    `json:"disk_used_gb"` // o chinh
	Disks         []DiskInfo                 `json:"disks"`        // tat ca o cung
	NetInKbps     float64                    `json:"net_in_kbps"`
	NetOutKbps    float64                    `json:"net_out_kbps"`
	PingLatencyMs float64                    `json:"ping_latency_ms"`
	UptimeSeconds int64                      `json:"uptime_seconds"`
	AgentVersion  string                     `json:"agent_version"`
	Processes     []ProcessInfo              `json:"processes"`
	// Nhiet do & GPU
	CPUTempC       float64 `json:"cpu_temp_c,omitempty"`
	GPUName        string  `json:"gpu_name,omitempty"`
	GPUUsagePct    float64 `json:"gpu_usage_pct"`
	GPUTempC       float64 `json:"gpu_temp_c,omitempty"`
	GPUVramTotalMB int64   `json:"gpu_vram_total_mb,omitempty"`
	GPUVramUsedMB  int64   `json:"gpu_vram_used_mb,omitempty"`
}

// ProcessInfo chua thong tin 1 process.
type ProcessInfo struct {
	PID         int32    `json:"pid"`
	Name        string   `json:"name"`
	ExePath     string   `json:"exe_path"`
	CPUPct      float64  `json:"cpu_pct"`
	GPUUsagePct *float64 `json:"gpu_usage_pct"`
	RAMMB       float64  `json:"ram_mb"`
	User        string   `json:"user"`
}

// StaticInfo chua thong tin tinh cua may — chi gui 1 lan khi dang ky.
type StaticInfo struct {
	Hostname      string     `json:"hostname"`
	OSName        string     `json:"os_name"`
	OSVersion     string     `json:"os_version"`
	KernelVersion string     `json:"kernel_version"`
	Arch          string     `json:"arch"`
	CPUModel      string     `json:"cpu_model"`
	CPUCores      int        `json:"cpu_cores"`
	RAMTotalMB    int64      `json:"ram_total_mb"`
	DiskTotalGB   float64    `json:"disk_total_gb"` // o chinh
	Disks         []DiskInfo `json:"disks"`         // tat ca o cung
	AgentVersion  string     `json:"agent_version"`
	GPUName       string     `json:"gpu_name,omitempty"`
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

	// Disk - doc tat ca partition
	m.Disks = collectAllDisks()
	// O chinh: C:\ tren Windows, / tren Linux
	rootPath := "/"
	if runtime.GOOS == "windows" {
		rootPath = "C:\\"
	}
	for _, d := range m.Disks {
		if strings.EqualFold(d.Mountpoint, rootPath) || d.Mountpoint == rootPath {
			m.DiskUsage = d.UsedPercent
			m.DiskUsedGB = d.UsedGB
			break
		}
	}
	// Fallback: neu khong tim thay o chinh, lay o dau tien
	if m.DiskUsage == 0 && len(m.Disks) > 0 {
		m.DiskUsage = m.Disks[0].UsedPercent
		m.DiskUsedGB = m.Disks[0].UsedGB
	}

	// Network I/O (delta trong 1 giay)
	m.NetInKbps, m.NetOutKbps = collectNetSpeed()

	// Uptime
	if uptime, err := host.Uptime(); err == nil {
		m.UptimeSeconds = int64(uptime)
	}

	// Top N Process
	m.Processes = collectTopProcesses(topN)

	m.WatchStatus = collectWatchStatus()
	// Nhiet do CPU & GPU
	collectThermalGPU(m)

	return m, nil
}

// collectAllDisks lay thong tin tat ca cac o cung / partition dang mount.
func collectAllDisks() []DiskInfo {
	partitions, err := disk.Partitions(false) // false = chi lay physical drives
	if err != nil {
		return nil
	}

	seen := make(map[string]bool)
	var result []DiskInfo

	for _, p := range partitions {
		// Bo qua cac filesystem ao / doc-only
		fs := strings.ToLower(p.Fstype)
		if fs == "tmpfs" || fs == "devtmpfs" || fs == "sysfs" || fs == "proc" ||
			fs == "devfs" || fs == "squashfs" || fs == "overlay" || fs == "aufs" ||
			strings.HasPrefix(fs, "fuse.") {
			continue
		}

		mp := p.Mountpoint
		if seen[mp] {
			continue
		}
		seen[mp] = true

		usage, err := disk.Usage(mp)
		if err != nil || usage.Total == 0 {
			continue
		}

		result = append(result, DiskInfo{
			Mountpoint:  mp,
			Fstype:      p.Fstype,
			TotalGB:     round2(float64(usage.Total) / 1024 / 1024 / 1024),
			UsedGB:      round2(float64(usage.Used) / 1024 / 1024 / 1024),
			FreeGB:      round2(float64(usage.Free) / 1024 / 1024 / 1024),
			UsedPercent: round2(usage.UsedPercent),
		})
	}

	return result
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

	// Tat ca o cung
	info.Disks = collectAllDisks()
	// O chinh
	rootPath := "/"
	if runtime.GOOS == "windows" {
		rootPath = "C:\\"
	}
	for _, d := range info.Disks {
		if strings.EqualFold(d.Mountpoint, rootPath) || d.Mountpoint == rootPath {
			info.DiskTotalGB = d.TotalGB
			break
		}
	}
	if info.DiskTotalGB == 0 && len(info.Disks) > 0 {
		info.DiskTotalGB = info.Disks[0].TotalGB
	}

	// GPU Name trong static info
	if runtime.GOOS == "windows" {
		if hasNvidiaSmi() {
			outNvidia, errNvidia := hiddenCmd("nvidia-smi", "--query-gpu=name", "--format=csv,noheader").Output()
			if errNvidia == nil && len(outNvidia) > 0 {
				info.GPUName = strings.TrimSpace(strings.Split(string(outNvidia), "\n")[0])
			}
		}
		if info.GPUName == "" {
			if name, _, err := getWindowsWmiGPU(); err == nil && name != "" {
				info.GPUName = name
			}
		}
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

type processSample struct {
	created int64
	total   float64
}

var processSamples = map[int32]processSample{}
var processSampleTime time.Time
var processSampleLock sync.Mutex

func processCPUPercent(previous, current processSample, elapsed float64, logicalCPUs int) float64 {
	if elapsed <= 0 || logicalCPUs <= 0 || previous.created != current.created || current.total < previous.total {
		return 0
	}
	value := (current.total - previous.total) / elapsed / float64(logicalCPUs) * 100
	if value > 100 {
		return 100
	}
	if value < 0 {
		return 0
	}
	return value
}

// collectTopProcesses lay top N process theo CPU%.
func collectTopProcesses(topN int) []ProcessInfo {
	processSampleLock.Lock()
	defer processSampleLock.Unlock()
	now := time.Now()
	elapsed := now.Sub(processSampleTime).Seconds()
	nextSamples := map[int32]processSample{}
	procs, err := process.Processes()
	if err != nil {
		return nil
	}

	var infos []ProcessInfo
	gpuUsage := getProcessGPUUsage()
	for _, p := range procs {
		name, _ := p.Name()
		if name == "" {
			continue
		}
		cpuPct := 0.0
		times, cpuErr := p.Times()
		created, createErr := p.CreateTime()
		if cpuErr == nil && createErr == nil {
			current := processSample{created: created, total: times.Total()}
			nextSamples[p.Pid] = current
			if previous, ok := processSamples[p.Pid]; ok && !processSampleTime.IsZero() {
				cpuPct = processCPUPercent(previous, current, elapsed, runtime.NumCPU())
			}
		}
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
			GPUUsagePct: gpuValue(gpuUsage, p.Pid),
		})
	}

	processSamples = nextSamples
	processSampleTime = now
	return selectTopProcesses(infos, topN)
}

func gpuValue(values map[int32]float64, pid int32) *float64 {
	value, ok := values[pid]
	if !ok {
		return nil
	}
	return &value
}

// Union keeps memory/GPU-heavy processes even when they have low CPU usage.
func selectTopProcesses(infos []ProcessInfo, topN int) []ProcessInfo {
	if topN <= 0 {
		return nil
	}
	selected := map[int32]ProcessInfo{}
	rankings := []func(i, j int) bool{
		func(i, j int) bool { return infos[i].CPUPct > infos[j].CPUPct },
		func(i, j int) bool { return infos[i].RAMMB > infos[j].RAMMB },
		func(i, j int) bool {
			a, b := infos[i].GPUUsagePct, infos[j].GPUUsagePct
			if a == nil {
				return false
			}
			if b == nil {
				return true
			}
			return *a > *b
		},
	}
	for _, less := range rankings {
		sort.SliceStable(infos, less)
		for i, p := range infos {
			if i >= topN {
				break
			}
			selected[p.PID] = p
		}
	}
	result := make([]ProcessInfo, 0, len(selected))
	for _, p := range selected {
		result = append(result, p)
	}
	sort.Slice(result, func(i, j int) bool {
		if result[i].CPUPct == result[j].CPUPct {
			return result[i].PID < result[j].PID
		}
		return result[i].CPUPct > result[j].CPUPct
	})
	return result
}

func round2(v float64) float64 { return float64(int(v*100)) / 100 }

// collectThermalGPU thu thap nhiet do va thong tin GPU.
func collectThermalGPU(m *Metrics) {
	if runtime.GOOS == "windows" {
		collectWindowsGPU(m)
		collectWindowsCPUTemp(m)
	} else {
		collectLinuxTemp(m)
		collectLinuxGPU(m)
	}
}

func collectWindowsGPU(m *Metrics) {
	// 1. Uu tien dung nvidia-smi (chinh xac va nhanh nhat cho NVIDIA GPU)
	if hasNvidiaSmi() {
		outNvidia, errNvidia := hiddenCmd("nvidia-smi", "--query-gpu=name,utilization.gpu,temperature.gpu,memory.total,memory.used", "--format=csv,noheader,nounits").Output()
		if errNvidia == nil && len(outNvidia) > 0 {
			firstLine := strings.Split(strings.TrimSpace(string(outNvidia)), "\n")[0]
			parts := strings.Split(firstLine, ",")
			if len(parts) >= 5 {
				m.GPUName = strings.TrimSpace(parts[0])
				if usage, err := strconv.ParseFloat(strings.TrimSpace(parts[1]), 64); err == nil {
					m.GPUUsagePct = round2(usage)
				}
				if temp, err := strconv.ParseFloat(strings.TrimSpace(parts[2]), 64); err == nil {
					m.GPUTempC = round2(temp)
				}
				if totalMB, err := strconv.ParseInt(strings.TrimSpace(parts[3]), 10, 64); err == nil {
					m.GPUVramTotalMB = totalMB
				}
				if usedMB, err := strconv.ParseInt(strings.TrimSpace(parts[4]), 10, 64); err == nil {
					m.GPUVramUsedMB = usedMB
				}
				return
			}
		}
	}

	// 2. Fallback sang native WMI (0 chuong trinh con, 0 cua so cmd)
	if name, ramMB, err := getWindowsWmiGPU(); err == nil && name != "" {
		m.GPUName = name
		if ramMB > 0 {
			m.GPUVramTotalMB = int64(ramMB)
		}
	}
}

func collectWindowsCPUTemp(m *Metrics) {
	// Query native WMI root/wmi (0 chuong trinh con, 0 cua so cmd)
	if tempC, err := getWindowsWmiCPUTemp(); err == nil && tempC > 0 && tempC < 120 {
		m.CPUTempC = round2(tempC)
	}
}

func collectLinuxTemp(m *Metrics) {
	out, err := hiddenCmd("sensors", "-j").Output()
	if err != nil || len(out) == 0 {
		return
	}
	var maxTemp float64
	lines := strings.Split(string(out), "\n")
	for _, line := range lines {
		if strings.Contains(line, "temp") && strings.Contains(line, ":") {
			parts := strings.Split(line, ":")
			if len(parts) == 2 {
				valStr := strings.TrimSpace(strings.Trim(parts[1], " ,"))
				if v, err := strconv.ParseFloat(valStr, 64); err == nil && v > 0 && v < 150 {
					if v > maxTemp {
						maxTemp = v
					}
				}
			}
		}
	}
	if maxTemp > 0 {
		m.CPUTempC = maxTemp
	}
}

func collectLinuxGPU(m *Metrics) {
	out, err := hiddenCmd("nvidia-smi", "--query-gpu=name,utilization.gpu,temperature.gpu,memory.total,memory.used",
		"--format=csv,noheader,nounits").Output()
	if err == nil && len(out) > 0 {
		parts := strings.Split(strings.TrimSpace(string(out)), ",")
		if len(parts) >= 5 {
			m.GPUName = strings.TrimSpace(parts[0])
			if v, e := strconv.ParseFloat(strings.TrimSpace(parts[1]), 64); e == nil {
				m.GPUUsagePct = round2(v)
			}
			if v, e := strconv.ParseFloat(strings.TrimSpace(parts[2]), 64); e == nil {
				m.GPUTempC = round2(v)
			}
			if v, e := strconv.ParseInt(strings.TrimSpace(parts[3]), 10, 64); e == nil {
				m.GPUVramTotalMB = v
			}
			if v, e := strconv.ParseInt(strings.TrimSpace(parts[4]), 10, 64); e == nil {
				m.GPUVramUsedMB = v
			}
		}
		return
	}
	// Thu rocm-smi cho AMD
	out2, err2 := hiddenCmd("rocm-smi", "--showuse", "--showtemp", "--showmeminfo", "vram").Output()
	if err2 == nil && len(out2) > 0 {
		m.GPUName = "AMD GPU (rocm-smi)"
	}
}

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
