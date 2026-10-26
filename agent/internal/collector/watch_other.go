//go:build !windows

package collector

import (
	"context"
	"os/exec"
	"strings"
	"time"
)

func watchedServiceRunning(name string) (bool, bool) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	out, err := exec.CommandContext(ctx, "systemctl", "is-active", "--", name).Output()
	state := strings.TrimSpace(string(out))
	if err != nil && state != "inactive" && state != "failed" && state != "unknown" {
		return false, false
	}
	return state == "active", true
}
