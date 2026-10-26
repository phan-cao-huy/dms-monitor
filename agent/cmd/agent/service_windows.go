//go:build windows

// service_windows.go — ho tro chay duoi dang Windows Service chuan thong qua SCM.
package main

import (
	"fmt"
	"log"
	"os"
	"os/exec"
	"path/filepath"
	"syscall"
	"time"

	"golang.org/x/sys/windows/svc"
)

type dmsAgentService struct {
	stopCh chan struct{}
}

func (s *dmsAgentService) Execute(args []string, r <-chan svc.ChangeRequest, changes chan<- svc.Status) (ssec bool, errno uint32) {
	const cmdsAccepted = svc.AcceptStop | svc.AcceptShutdown
	changes <- svc.Status{State: svc.StartPending}

	// Khoi dong cac goroutines cua Agent
	go runAgent(s.stopCh)

	changes <- svc.Status{State: svc.Running, Accepts: cmdsAccepted}
	log.Println("[SERVICE] DMS Agent Windows Service is now RUNNING in Session 0")

	for {
		select {
		case c := <-r:
			switch c.Cmd {
			case svc.Interrogate:
				changes <- c.CurrentStatus
			case svc.Stop, svc.Shutdown:
				log.Println("[SERVICE] Nhan lenh Stop/Shutdown tu Service Control Manager...")
				changes <- svc.Status{State: svc.StopPending}
				close(s.stopCh)
				time.Sleep(1 * time.Second)
				changes <- svc.Status{State: svc.Stopped}
				return
			default:
				log.Printf("[SERVICE] Lenh khong ho tro: #%d", c.Cmd)
			}
		}
	}
}

// isWindowsService kiem tra xem process co phai do Service Control Manager khoi dong khong.
func isWindowsService() bool {
	isSvc, err := svc.IsWindowsService()
	return err == nil && isSvc
}

// runWindowsService chay loop cua Windows Service.
func runWindowsService() error {
	stopCh := make(chan struct{})
	return svc.Run(serviceName, &dmsAgentService{stopCh: stopCh})
}

// installService cai DMS Agent lam Windows Service dung sc.exe.
func installService() error {
	exePath, err := os.Executable()
	if err != nil {
		return fmt.Errorf("khong lay duoc duong dan exe: %w", err)
	}
	exePath, err = filepath.Abs(exePath)
	if err != nil {
		return err
	}

	log.Printf("[SERVICE] Cai service '%s' tu: %s", serviceName, exePath)

	cmd := exec.Command("sc", "create", serviceName,
		"binPath=", fmt.Sprintf(`"%s"`, exePath),
		"DisplayName=", serviceDisplayName,
		"start=", "auto",
		"obj=", "LocalSystem",
	)
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: 0x08000000}
	out, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("sc create that bai: %s — %w", string(out), err)
	}

	descCmd := exec.Command("sc", "description", serviceName, serviceDesc)
	descCmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: 0x08000000}
	_ = descCmd.Run()

	failCmd := exec.Command("sc", "failure", serviceName, "reset=", "86400", "actions=", "restart/5000/restart/5000/restart/5000")
	failCmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: 0x08000000}
	_ = failCmd.Run()

	startCmd := exec.Command("sc", "start", serviceName)
	startCmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: 0x08000000}
	startOut, startErr := startCmd.CombinedOutput()
	if startErr != nil {
		log.Printf("[SERVICE] Canh bao: Khong the khoi dong ngay: %s", string(startOut))
	} else {
		log.Printf("[SERVICE] Service da khoi dong: %s", string(startOut))
	}

	return nil
}

// uninstallService go cai DMS Agent Windows Service.
func uninstallService() error {
	log.Printf("[SERVICE] Dung service '%s'...", serviceName)
	stopCmd := exec.Command("sc", "stop", serviceName)
	stopCmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: 0x08000000}
	_ = stopCmd.Run()
	time.Sleep(2 * time.Second)

	log.Printf("[SERVICE] Xoa service '%s'...", serviceName)
	cmd := exec.Command("sc", "delete", serviceName)
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: 0x08000000}
	out, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("sc delete that bai: %s — %w", string(out), err)
	}
	return nil
}
