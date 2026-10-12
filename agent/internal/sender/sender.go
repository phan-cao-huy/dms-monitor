// Package sender: HTTP client, retry exponential backoff, header X-Device-Token.
package sender

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"time"

	"github.com/phan-cao-huy/dms-monitor/agent/internal/collector"
	"github.com/phan-cao-huy/dms-monitor/agent/internal/config"
)

// HeartbeatPayload la JSON body gui len Backend POST /agent/heartbeat.
type HeartbeatPayload struct {
	DeviceID    int                    `json:"device_id"`
	SecretToken string                 `json:"secret_token"`
	Metrics     *collector.Metrics     `json:"metrics"`
	StaticInfo  *collector.StaticInfo  `json:"static_info,omitempty"`
}

// CommandResponse la JSON tra ve tu Backend GET /agent/commands.
type CommandResponse struct {
	Commands []Command `json:"commands"`
}

// Command la 1 lenh dieu khien tu Backend.
type Command struct {
	CommandID   int                    `json:"command_id"`
	CommandType string                 `json:"command_type"`
	Parameters  map[string]interface{} `json:"parameters"`
}

// CommandResult la ket qua sau khi thuc thi lenh, gui lai Backend.
type CommandResult struct {
	CommandID    int    `json:"command_id"`
	Status       string `json:"status"`
	Output       string `json:"output,omitempty"`
	ErrorMessage string `json:"error_message,omitempty"`
}

// Client la HTTP client de giao tiep voi Backend.
type Client struct {
	cfg        *config.Config
	httpClient *http.Client
}

// NewClient tao HTTP client moi.
func NewClient(cfg *config.Config) *Client {
	return &Client{
		cfg:        cfg,
		httpClient: &http.Client{Timeout: 30 * time.Second},
	}
}

// SendHeartbeat gui du lieu metric len Backend.
// Tu dong retry toi da 3 lan voi exponential backoff.
func (c *Client) SendHeartbeat(metrics *collector.Metrics, staticInfo *collector.StaticInfo) error {
	payload := HeartbeatPayload{
		DeviceID:    c.cfg.DeviceID,
		SecretToken: c.cfg.SecretToken,
		Metrics:     metrics,
		StaticInfo:  staticInfo,
	}

	body, err := json.Marshal(payload)
	if err != nil {
		return fmt.Errorf("marshal payload: %w", err)
	}

	url := c.cfg.BackendURL + "/agent/heartbeat"

	var lastErr error
	for attempt := 0; attempt < 3; attempt++ {
		if attempt > 0 {
			wait := time.Duration(1<<uint(attempt)) * 2 * time.Second
			log.Printf("[SENDER] Retry lan %d sau %v...", attempt, wait)
			time.Sleep(wait)
		}

		req, err := http.NewRequest("POST", url, bytes.NewReader(body))
		if err != nil {
			return fmt.Errorf("tao request: %w", err)
		}
		req.Header.Set("Content-Type", "application/json")
		req.Header.Set("X-Device-Token", c.cfg.SecretToken)

		resp, err := c.httpClient.Do(req)
		if err != nil {
			lastErr = fmt.Errorf("gui request: %w", err)
			log.Printf("[SENDER] Loi ket noi: %v", err)
			continue
		}

		respBody, _ := io.ReadAll(resp.Body)
		resp.Body.Close()

		if resp.StatusCode >= 200 && resp.StatusCode < 300 {
			log.Printf("[SENDER] OK Heartbeat gui thanh cong (HTTP %d)", resp.StatusCode)
			return nil
		}

		lastErr = fmt.Errorf("HTTP %d: %s", resp.StatusCode, string(respBody))
		log.Printf("[SENDER] Phan hoi loi: %v", lastErr)

		if resp.StatusCode >= 400 && resp.StatusCode < 500 {
			return lastErr
		}
	}

	return fmt.Errorf("het retry: %w", lastErr)
}

// FetchCommands lay danh sach lenh dieu khien dang cho tu Backend.
func (c *Client) FetchCommands() ([]Command, error) {
	url := fmt.Sprintf("%s/agent/commands?device_id=%d", c.cfg.BackendURL, c.cfg.DeviceID)

	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return nil, fmt.Errorf("tao request: %w", err)
	}
	req.Header.Set("X-Device-Token", c.cfg.SecretToken)

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("goi API: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != 200 {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("HTTP %d: %s", resp.StatusCode, string(body))
	}

	var cmdResp CommandResponse
	if err := json.NewDecoder(resp.Body).Decode(&cmdResp); err != nil {
		return nil, fmt.Errorf("decode response: %w", err)
	}
	return cmdResp.Commands, nil
}

// ReportCommandResult bao ket qua thuc thi lenh ve Backend.
func (c *Client) ReportCommandResult(result CommandResult) error {
	body, err := json.Marshal(result)
	if err != nil {
		return err
	}

	url := fmt.Sprintf("%s/agent/commands/%d/result", c.cfg.BackendURL, result.CommandID)
	req, err := http.NewRequest("PUT", url, bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Device-Token", c.cfg.SecretToken)

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode >= 300 {
		respBody, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("HTTP %d: %s", resp.StatusCode, string(respBody))
	}

	log.Printf("[SENDER] OK Da bao ket qua lenh #%d: %s", result.CommandID, result.Status)
	return nil
}
