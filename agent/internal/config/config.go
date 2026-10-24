// Package config doc cau hinh tu flags CLI, file config.json hoac bien moi truong.
package config

import (
	"bytes"
	"encoding/json"
	"flag"
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

// Config chua toan bo cau hinh Agent can.
type Config struct {
	BackendURL                 string `json:"backend_url"`
	SecretToken                string `json:"secret_token"`
	DeviceID                   int    `json:"device_id"`
	HeartbeatIntervalSeconds   int    `json:"heartbeat_interval_seconds"`
	CommandPollIntervalSeconds int    `json:"command_poll_interval_seconds"`
	ProcessTopN                int    `json:"process_top_n"`
	LogLevel                   string `json:"log_level"`
}

// Load doc config tu CLI flags, file config.json hoac bien moi truong.
func Load() (*Config, error) {
	var flagServer string
	var flagToken string
	var flagID int

	flag.StringVar(&flagServer, "server", "", "Backend URL (e.g. http://10.37.9.0:8000)")
	flag.StringVar(&flagToken, "token", "", "Secret pairing token")
	flag.IntVar(&flagID, "id", 0, "Device ID")
	flag.IntVar(&flagID, "device", 0, "Device ID (alias)")
	flag.Parse()

	// Lay thu muc chua file exe (quan trong khi chay duoi dang Windows Service hoac tu thu muc khac)
	var exeDir string
	if exePath, err := os.Executable(); err == nil {
		exeDir = filepath.Dir(exePath)
	}

	// 1. Neu co du tham so CLI thi uu tien chay ngay
	if flagServer != "" && flagToken != "" && flagID > 0 {
		server := strings.TrimRight(flagServer, "/")
		if !strings.HasSuffix(server, "/api/v1") {
			server = server + "/api/v1"
		}
		cfg := &Config{
			BackendURL:                 server,
			SecretToken:                flagToken,
			DeviceID:                   flagID,
			HeartbeatIntervalSeconds:   10,
			CommandPollIntervalSeconds: 5,
			ProcessTopN:                15,
			LogLevel:                   "info",
		}
		// Tu dong luu config.json vao cung thu muc voi exe de lan sau khoi dong khong can go lai tham so
		if data, err := json.MarshalIndent(cfg, "", "  "); err == nil {
			if exeDir != "" {
				_ = os.WriteFile(filepath.Join(exeDir, "config.json"), data, 0644)
			}
			_ = os.WriteFile("config.json", data, 0644)
		}
		fmt.Printf("[CONFIG] Configured via CLI flags (server=%s, id=%d)\n", cfg.BackendURL, cfg.DeviceID)
		return cfg, nil
	}

	// 2. Thu tim file config.json o nhieu vi tri (uu tien thu muc cung file exe)
	candidates := []string{}
	if exeDir != "" {
		candidates = append(candidates, filepath.Join(exeDir, "config.json"))
	}
	candidates = append(candidates,
		"config.json",
		"configs/config.json",
		filepath.Join("configs", "config.json"),
	)

	if envPath := os.Getenv("DMS_CONFIG_PATH"); envPath != "" {
		candidates = append([]string{envPath}, candidates...)
	}

	var data []byte
	var loadedPath string
	var err error

	for _, path := range candidates {
		data, err = os.ReadFile(path)
		if err == nil {
			loadedPath = path
			break
		}
	}

	if data == nil {
		return nil, fmt.Errorf("chua co cau hinh! Hay chay: dms-agent.exe -server <url> -token <token> -id <id>")
	}

	// Loai bo UTF-8 BOM neu co (do PowerShell Set-Content -Encoding UTF8 tren Windows tao ra)
	data = bytes.TrimPrefix(data, []byte("\xef\xbb\xbf"))

	cfg := &Config{
		HeartbeatIntervalSeconds:   60,
		CommandPollIntervalSeconds: 10,
		ProcessTopN:                15,
		LogLevel:                   "info",
	}

	if err := json.Unmarshal(data, cfg); err != nil {
		return nil, fmt.Errorf("loi parse %s: %w", loadedPath, err)
	}

	// Chuan hoa BackendURL
	cfg.BackendURL = strings.TrimRight(cfg.BackendURL, "/")
	if !strings.HasSuffix(cfg.BackendURL, "/api/v1") {
		cfg.BackendURL = cfg.BackendURL + "/api/v1"
	}

	// Validate cac truong bat buoc
	if cfg.BackendURL == "" {
		return nil, fmt.Errorf("backend_url khong duoc de trong")
	}
	if cfg.SecretToken == "" {
		return nil, fmt.Errorf("secret_token khong duoc de trong")
	}
	if cfg.DeviceID <= 0 {
		return nil, fmt.Errorf("device_id khong hop le: %d", cfg.DeviceID)
	}

	return cfg, nil
}
