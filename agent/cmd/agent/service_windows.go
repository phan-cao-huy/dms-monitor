//go:build windows

// service_windows.go — ho tro chay duoi dang Windows Service chuan thong qua SCM.
package main

import (
	"log"
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
