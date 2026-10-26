//go:build !windows

package main

import "fmt"

func isWindowsService() bool {
	return false
}

func runWindowsService() error {
	return nil
}

func installService() error   { return fmt.Errorf("service installation is only supported on Windows") }
func uninstallService() error { return fmt.Errorf("service removal is only supported on Windows") }
