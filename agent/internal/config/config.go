// Package config doc cau hinh tu file config.json hoac bien moi truong.
package config

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
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

// Load doc config tu file config.json (tim trong thu muc hien tai hoac configs/).
func Load() (*Config, error) {
	// Thu tim file config.json o nhieu vi tri
	candidates := []string{
		"config.json",
		"configs/config.json",
		filepath.Join("configs", "config.json"),
	}

	// Neu co bien moi truong chi dinh duong dan
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
		return nil, fmt.Errorf("khong tim thay config.json. Tao file configs/config.json tu config.example.json")
	}

	cfg := &Config{
		// Gia tri mac dinh
		HeartbeatIntervalSeconds:   60,
		CommandPollIntervalSeconds: 10,
		ProcessTopN:                15,
		LogLevel:                   "info",
	}

	if err := json.Unmarshal(data, cfg); err != nil {
		return nil, fmt.Errorf("loi parse %s: %w", loadedPath, err)
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
