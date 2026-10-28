//go:build windows

package collector

import "time"

func parseEventXML(data, logName string, format func(string) string) (DeviceEvent, error) {
	row, err := decodeWindowsEvent(data)
	if err != nil {
		return DeviceEvent{}, err
	}
	occurred, err := time.Parse(time.RFC3339Nano, row.System.TimeCreated.SystemTime)
	if err != nil {
		return DeviceEvent{}, err
	}
	level := "ERROR"
	if row.System.Level == 1 {
		level = "CRITICAL"
	}
	message := format(row.System.Provider.Name)
	if message == "" {
		message = eventFallback(row.System.Provider.Name, row.System.EventID, row.Data)
	}
	return DeviceEvent{LogName: logName, RecordID: row.System.RecordID, EventID: row.System.EventID,
		Level: level, Source: truncateText(row.System.Provider.Name, 255), Message: truncateText(message, 1000), OccurredAt: occurred.UTC()}, nil
}
