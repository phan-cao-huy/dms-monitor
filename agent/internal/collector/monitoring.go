package collector

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"

	gnet "github.com/shirou/gopsutil/v3/net"
	"github.com/shirou/gopsutil/v3/process"
)

type DiskHealth struct {
	Name    string   `json:"name"`
	Healthy *bool    `json:"healthy"`
	TempC   *float64 `json:"temp_c,omitempty"`
	WearPct *float64 `json:"wear_pct,omitempty"`
	Source  string   `json:"source"`
}

type Connection struct {
	PID     int32  `json:"pid"`
	Process string `json:"process"`
	LAddr   string `json:"laddr"`
	RAddr   string `json:"raddr"`
	Status  string `json:"status"`
}

type DeviceEvent struct {
	LogName    string    `json:"log_name"`
	RecordID   uint64    `json:"record_id"`
	EventID    int       `json:"event_id"`
	Level      string    `json:"level"`
	Source     string    `json:"source"`
	Message    string    `json:"message"`
	OccurredAt time.Time `json:"occurred_at"`
}

type EventCursor struct {
	RecordID uint64    `json:"record_id"`
	Time     time.Time `json:"time"`
}

func truncateText(s string, limit int) string {
	runes := []rune(s)
	if len(runes) > limit {
		return string(runes[:limit])
	}
	return s
}

func selectConnections(rows []gnet.ConnectionStat, limit int) []Connection {
	sort.Slice(rows, func(i, j int) bool { return rows[i].Pid < rows[j].Pid })
	result := []Connection{}
	names := map[int32]string{}
	for _, r := range rows {
		if r.Status != "ESTABLISHED" && r.Status != "LISTEN" {
			continue
		}
		name, exists := names[r.Pid]
		if !exists {
			if p, err := process.NewProcess(r.Pid); err == nil {
				name, _ = p.Name()
			}
			names[r.Pid] = name
		}
		result = append(result, Connection{PID: r.Pid, Process: truncateText(name, 255),
			LAddr: fmt.Sprintf("%s:%d", r.Laddr.IP, r.Laddr.Port), RAddr: fmt.Sprintf("%s:%d", r.Raddr.IP, r.Raddr.Port), Status: r.Status})
		if len(result) >= limit {
			break
		}
	}
	return result
}

func collectConnections() []Connection {
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	rows, err := gnet.ConnectionsWithContext(ctx, "inet")
	if err != nil {
		return nil
	}
	return selectConnections(rows, 50)
}

func LoadEventCursors(path string) map[string]EventCursor {
	result := map[string]EventCursor{}
	if data, err := os.ReadFile(path); err == nil {
		_ = json.Unmarshal(data, &result)
	}
	return result
}

func SaveEventCursors(path string, cursors map[string]EventCursor) error {
	data, err := json.Marshal(cursors)
	if err != nil {
		return err
	}
	tmp := filepath.Join(filepath.Dir(path), "event-cursors.tmp")
	if err = os.WriteFile(tmp, data, 0600); err != nil {
		return err
	}
	return os.Rename(tmp, path)
}

func eventFallback(source string, id int, data []string) string {
	return truncateText(fmt.Sprintf("%s · Event %d\n%s", source, id, strings.Join(data, " · ")), 1000)
}
