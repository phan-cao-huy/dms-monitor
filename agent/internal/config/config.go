// Package config doc cau hinh tu flags CLI, file config.json hoac bien moi truong.
package config

import (
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
		// Tu dong luu config.json de lan sau khoi dong khong can go lai tham so
		if data, err := json.MarshalIndent(cfg, "", "  "); err == nil {
			_ = os.WriteFile("config.json", data, 0644)
		}
		fmt.Printf("[CONFIG] Configured via CLI flags (server=%s, id=%d)\n", cfg.BackendURL, cfg.DeviceID)
		return cfg, nil
	}

	// 2. Thu tim file config.json o nhieu vi tri
	candidates := []string{
		"config.json",
		"configs/config.json",
		filepath.Join("configs", "config.json"),
	}

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

	// Validate
	if cfg.BackendURL == "" {
		return nil, fmt.Errorf("backend_url chua duoc cau hinh trong %s", loadedPath)
	}
	if cfg.SecretToken == "" || cfg.SecretToken == "PASTE_TOKEN_FROM_APP" {
		return nil, fmt.Errorf("secret_token chua duoc cau hinh trong %s", loadedPath)
	}
	if cfg.DeviceID <= 0 {
		return nil, fmt.Errorf("device_id phai > 0 trong %s", loadedPath)
	}

	fmt.Printf("[CONFIG] Loaded from: %s\n", loadedPath)
	return cfg, nil
}
