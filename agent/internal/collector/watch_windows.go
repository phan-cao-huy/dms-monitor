//go:build windows

package collector

import (
	"errors"
	"golang.org/x/sys/windows"
	"golang.org/x/sys/windows/svc"
	"golang.org/x/sys/windows/svc/mgr"
)

func watchedServiceRunning(name string) (bool, bool) {
	manager, err := mgr.Connect()
	if err != nil {
		return false, false
	}
	defer manager.Disconnect()
	service, err := manager.OpenService(name)
	if err != nil {
		return false, errors.Is(err, windows.ERROR_SERVICE_DOES_NOT_EXIST)
	}
	defer service.Close()
	status, err := service.Query()
	if err != nil {
		return false, false
	}
	return status.State == svc.Running, true
}
