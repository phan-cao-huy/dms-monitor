//go:build !windows

package collector

func getWindowsWmiGPU() (string, uint32, error) {
	return "", 0, nil
}

func getWindowsWmiCPUTemp() (float64, error) {
	return 0, nil
}

func hasNvidiaSmi() bool {
	return false
}
