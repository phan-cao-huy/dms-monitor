//go:build windows

// wmi_windows.go — truy van thong tin GPU va CPU Temp truc tiep qua Windows WMI/COM.
// KHONG chay chuong trinh con (PowerShell/CMD), KHONG tao tien trinh, KHONG nhap nhay cua so.
package collector

import (
	"os/exec"
	"regexp"
	"strconv"

	"github.com/yusufpapurcu/wmi"
)

type win32VideoController struct {
	Name       string
	AdapterRAM uint32
}

type msAcpiThermalZoneTemperature struct {
	CurrentTemperature uint32
}

// getWindowsWmiGPU lay ten GPU va dung luong VRAM (MB) tu WMI ma khong can goi PowerShell.
func getWindowsWmiGPU() (name string, ramMB uint32, err error) {
	var dst []win32VideoController
	q := "SELECT Name, AdapterRAM FROM Win32_VideoController"
	if err := wmi.Query(q, &dst); err != nil || len(dst) == 0 {
		return "", 0, err
	}
	return dst[0].Name, dst[0].AdapterRAM / (1024 * 1024), nil
}

// getWindowsWmiCPUTemp lay nhiet do CPU (do C) qua WMI root/wmi ma khong can goi PowerShell.
func getWindowsWmiCPUTemp() (tempC float64, err error) {
	var dst []msAcpiThermalZoneTemperature
	q := "SELECT CurrentTemperature FROM MSAcpi_ThermalZoneTemperature"
	if err := wmi.QueryNamespace(q, &dst, "root/wmi"); err != nil || len(dst) == 0 {
		return 0, err
	}
	// MSAcpi tra ve nhiet do theo 1/10 Kelvin: C = (K / 10) - 273.15
	rawK := float64(dst[0].CurrentTemperature)
	if rawK <= 0 {
		return 0, nil
	}
	celsius := (rawK / 10.0) - 273.15
	if celsius < -50 || celsius > 150 {
		return 0, nil
	}
	return celsius, nil
}

var (
	nvidiaSmiPathChecked bool
	nvidiaSmiPathFound   bool
)

// hasNvidiaSmi kiem tra nhanh xem nvidia-smi co tren may khong (chi kiem tra 1 lan).
func hasNvidiaSmi() bool {
	if !nvidiaSmiPathChecked {
		_, err := exec.LookPath("nvidia-smi")
		nvidiaSmiPathFound = (err == nil)
		nvidiaSmiPathChecked = true
	}
	return nvidiaSmiPathFound
}

// getProcessGPUUsage uses Windows' per-engine GPU counters; unavailable counters stay unknown.
// As in Task Manager, report the busiest engine for each process, not a sum of engines.
func getProcessGPUUsage() map[int32]float64 {
	var rows []struct {
		Name                  string
		UtilizationPercentage uint64
	}
	if err := wmi.Query("SELECT Name, UtilizationPercentage FROM Win32_PerfFormattedData_GPUPerformanceCounters_GPUEngine", &rows); err != nil {
		return nil
	}
	result := map[int32]float64{}
	pattern := regexp.MustCompile(`^pid_(\d+)_`)
	for _, row := range rows {
		match := pattern.FindStringSubmatch(row.Name)
		if len(match) != 2 {
			continue
		}
		pid, err := strconv.ParseInt(match[1], 10, 32)
		if err != nil {
			continue
		}
		value := float64(row.UtilizationPercentage)
		if value > 100 {
			value = 100
		}
		if prior, exists := result[int32(pid)]; !exists || value > prior {
			result[int32(pid)] = value
		}
	}
	return result
}
