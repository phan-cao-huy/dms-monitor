//go:build !windows

package collector

func collectDiskHealth() []DiskHealth { return nil }

func CollectEvents(cursors map[string]EventCursor) ([]DeviceEvent, map[string]EventCursor, map[string]string) {
	return nil, cursors, map[string]string{"System": "UNSUPPORTED", "Application": "UNSUPPORTED", "Security": "UNSUPPORTED"}
}
