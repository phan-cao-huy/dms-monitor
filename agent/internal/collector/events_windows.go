//go:build windows

package collector

import (
	"encoding/xml"
	"fmt"
	"unsafe"

	"golang.org/x/sys/windows"
)

var eventDLL = windows.NewLazySystemDLL("wevtapi.dll")
var evtQuery = eventDLL.NewProc("EvtQuery")
var evtNext = eventDLL.NewProc("EvtNext")
var evtRender = eventDLL.NewProc("EvtRender")
var evtClose = eventDLL.NewProc("EvtClose")
var evtOpenPublisher = eventDLL.NewProc("EvtOpenPublisherMetadata")
var evtFormat = eventDLL.NewProc("EvtFormatMessage")

type windowsEventXML struct {
	System struct {
		Provider struct {
			Name string `xml:"Name,attr"`
		} `xml:"Provider"`
		EventID     int    `xml:"EventID"`
		Level       int    `xml:"Level"`
		RecordID    uint64 `xml:"EventRecordID"`
		TimeCreated struct {
			SystemTime string `xml:"SystemTime,attr"`
		} `xml:"TimeCreated"`
	} `xml:"System"`
	Data []string `xml:"EventData>Data"`
}

func queryEvents(logName, xpath string, reverse bool, limit int) ([]DeviceEvent, error) {
	path, _ := windows.UTF16PtrFromString(logName)
	query, _ := windows.UTF16PtrFromString(xpath)
	flags := uintptr(0x101)
	if reverse {
		flags = 0x201
	}
	handle, _, err := evtQuery.Call(0, uintptr(unsafe.Pointer(path)), uintptr(unsafe.Pointer(query)), flags)
	if handle == 0 {
		return nil, err
	}
	defer evtClose.Call(handle)
	events := []DeviceEvent{}
	for i := 0; i < limit; i++ {
		var event uintptr
		var count uint32
		ok, _, err := evtNext.Call(handle, 1, uintptr(unsafe.Pointer(&event)), 0, 0, uintptr(unsafe.Pointer(&count)))
		if ok == 0 {
			if err == windows.ERROR_NO_MORE_ITEMS {
				break
			}
			return events, err
		}
		parsed, parseErr := renderEvent(event, logName)
		evtClose.Call(event)
		if parseErr == nil {
			events = append(events, parsed)
		}
	}
	return events, nil
}

func renderEvent(handle uintptr, logName string) (DeviceEvent, error) {
	var needed, count uint32
	evtRender.Call(0, handle, 1, 0, 0, uintptr(unsafe.Pointer(&needed)), uintptr(unsafe.Pointer(&count)))
	if needed == 0 || needed > 1024*1024 {
		return DeviceEvent{}, fmt.Errorf("event XML unavailable or too large")
	}
	buffer := make([]uint16, needed/2+1)
	ok, _, err := evtRender.Call(0, handle, 1, uintptr(needed), uintptr(unsafe.Pointer(&buffer[0])), uintptr(unsafe.Pointer(&needed)), uintptr(unsafe.Pointer(&count)))
	if ok == 0 {
		return DeviceEvent{}, err
	}
	return parseEventXML(windows.UTF16ToString(buffer), logName, func(source string) string { return formatEvent(handle, source) })
}

func formatEvent(event uintptr, source string) string {
	provider, err := windows.UTF16PtrFromString(source)
	if err != nil {
		return ""
	}
	metadata, _, _ := evtOpenPublisher.Call(0, uintptr(unsafe.Pointer(provider)), 0, 0, 0)
	if metadata == 0 {
		return ""
	}
	defer evtClose.Call(metadata)
	var needed uint32
	evtFormat.Call(metadata, event, 0, 0, 0, 1, 0, 0, uintptr(unsafe.Pointer(&needed)))
	if needed == 0 || needed > 65536 {
		return ""
	}
	buffer := make([]uint16, needed+1)
	ok, _, _ := evtFormat.Call(metadata, event, 0, 0, 0, 1, uintptr(len(buffer)), uintptr(unsafe.Pointer(&buffer[0])), uintptr(unsafe.Pointer(&needed)))
	if ok == 0 {
		return ""
	}
	return truncateText(windows.UTF16ToString(buffer), 1000)
}

func CollectEvents(cursors map[string]EventCursor) ([]DeviceEvent, map[string]EventCursor, map[string]string) {
	next := map[string]EventCursor{}
	for k, v := range cursors {
		next[k] = v
	}
	events := []DeviceEvent{}
	statuses := map[string]string{}
	for _, name := range []string{"System", "Application", "Security"} {
		latest, err := queryEvents(name, "*", true, 1)
		if err != nil {
			statuses[name] = "UNAVAILABLE"
			if err == windows.ERROR_ACCESS_DENIED {
				statuses[name] = "ACCESS_DENIED"
			}
			continue
		}
		statuses[name] = "OK"
		prior, exists := cursors[name]
		if len(latest) == 0 {
			next[name] = EventCursor{}
			continue
		}
		last := latest[0]
		if !exists || last.RecordID < prior.RecordID || (last.RecordID == prior.RecordID && !last.OccurredAt.Equal(prior.Time)) {
			// First use or a cleared log: begin at its current tail, never replay historical log data.
			next[name] = EventCursor{RecordID: last.RecordID, Time: last.OccurredAt}
			continue
		}
		filter := "(Level=1 or Level=2)"
		if name == "Security" {
			filter = "EventID=4625"
		}
		xpath := fmt.Sprintf("*[System[(EventRecordID > %d) and %s]]", prior.RecordID, filter)
		rows, err := queryEvents(name, xpath, false, 16)
		if err != nil {
			statuses[name] = "UNAVAILABLE"
			continue
		}
		for _, event := range rows {
			if name == "Security" {
				event.Level = "AUDIT_FAILURE"
			}
			events = append(events, event)
			next[name] = EventCursor{RecordID: event.RecordID, Time: event.OccurredAt}
		}
	}
	if len(events) > 50 {
		events = events[:50]
	}
	return events, next, statuses
}

// xml.Unmarshal is kept in a platform-specific wrapper so no subprocess is needed.
func decodeWindowsEvent(data string) (windowsEventXML, error) {
	var event windowsEventXML
	err := xml.Unmarshal([]byte(data), &event)
	return event, err
}
