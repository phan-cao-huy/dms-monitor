package collector

import (
	"github.com/shirou/gopsutil/v3/process"
	"strings"
	"sync"
)

type WatchService struct {
	Name string `json:"name"`
}
type WatchConfig struct {
	Services  []WatchService `json:"services"`
	Processes []string       `json:"processes"`
}

var watchMu sync.RWMutex
var watchConfig WatchConfig

func SetWatchConfig(config WatchConfig) {
	watchMu.Lock()
	defer watchMu.Unlock()
	watchConfig = config
}
func collectWatchStatus() map[string]map[string]bool {
	watchMu.RLock()
	config := watchConfig
	watchMu.RUnlock()
	status := map[string]map[string]bool{"services": {}, "processes": {}}
	for _, service := range config.Services {
		if running, known := watchedServiceRunning(service.Name); known {
			status["services"][service.Name] = running
		}
	}
	if len(config.Processes) > 0 {
		procs, err := process.Processes()
		if err == nil {
			names := map[string]bool{}
			for _, p := range procs {
				if name, err := p.Name(); err == nil {
					names[strings.ToLower(name)] = true
				}
			}
			for _, name := range config.Processes {
				status["processes"][name] = names[strings.ToLower(name)]
			}
		}
	}
	return status
}
