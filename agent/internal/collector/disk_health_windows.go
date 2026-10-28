//go:build windows

package collector

import (
	"fmt"
	"time"

	"github.com/yusufpapurcu/wmi"
)

var diskHealthCache []DiskHealth
var diskHealthTime time.Time

func healthState(value uint16) *bool {
	if value > 2 {
		return nil
	}
	healthy := value == 0
	return &healthy
}

func collectDiskHealth() []DiskHealth {
	if !diskHealthTime.IsZero() && time.Since(diskHealthTime) < time.Minute {
		return diskHealthCache
	}
	diskHealthTime = time.Now()
	diskHealthCache = nil
	var disks []struct {
		FriendlyName string
		DeviceId     string
		HealthStatus *uint16
	}
	if err := (&wmi.Client{PtrNil: true}).Query("SELECT FriendlyName, DeviceId, HealthStatus FROM MSFT_PhysicalDisk", &disks, nil, `root\Microsoft\Windows\Storage`); err == nil && len(disks) > 0 {
		for _, d := range disks {
			diskHealthCache = append(diskHealthCache, DiskHealth{Name: truncateText(fmt.Sprintf("%s (Disk %s)", d.FriendlyName, d.DeviceId), 255), Healthy: nil, Source: "Windows Storage HealthStatus"})
			if d.HealthStatus != nil {
				diskHealthCache[len(diskHealthCache)-1].Healthy = healthState(*d.HealthStatus)
			}
			if len(diskHealthCache) >= 32 {
				break
			}
		}
		return diskHealthCache
	}
	var legacy []struct {
		InstanceName   string
		PredictFailure *bool
	}
	if err := (&wmi.Client{PtrNil: true}).Query("SELECT InstanceName, PredictFailure FROM MSStorageDriver_FailurePredictStatus", &legacy, nil, `root\wmi`); err == nil {
		for _, d := range legacy {
			var healthy *bool
			if d.PredictFailure != nil {
				v := !*d.PredictFailure
				healthy = &v
			}
			diskHealthCache = append(diskHealthCache, DiskHealth{Name: truncateText(d.InstanceName, 255), Healthy: healthy, Source: "SMART FailurePredictStatus"})
			if len(diskHealthCache) >= 32 {
				break
			}
		}
	}
	return diskHealthCache
}
