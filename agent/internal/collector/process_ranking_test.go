package collector

import "testing"

func TestRankingsKeepRAMAndGPUHeavyProcesses(t *testing.T) {
	gpu := 90.0
	result := selectTopProcesses([]ProcessInfo{{PID: 1, CPUPct: 90, RAMMB: 10}, {PID: 2, CPUPct: 1, RAMMB: 4096}, {PID: 3, CPUPct: 0, RAMMB: 20, GPUUsagePct: &gpu}}, 1)
	seen := map[int32]bool{}
	for _, p := range result {
		seen[p.PID] = true
	}
	if len(seen) != 3 {
		t.Fatalf("RAM/GPU processes were dropped: %v", result)
	}
	if gpuValue(nil, 1) != nil {
		t.Fatal("missing GPU must remain unknown")
	}
}
func TestCPUUsesSampleDeltaAndRejectsPIDReuse(t *testing.T) {
	p := processSample{created: 1, total: 10}
	c := processSample{created: 1, total: 14}
	if got := processCPUPercent(p, c, 2, 4); got != 50 {
		t.Fatalf("got %v, want 50", got)
	}
	c.created = 2
	if got := processCPUPercent(p, c, 2, 4); got != 0 {
		t.Fatal("PID reuse was counted")
	}
}
