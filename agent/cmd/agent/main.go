// DMS Agent - entrypoint.
// Luong: load config -> jitter -> moi 60s: collect -> POST /heartbeat (retry) -> GET /commands -> execute -> POST result.
package main

import "log"

func main() {
	// TODO(Dev2): config.Load() -> collector.Collect() -> sender.Heartbeat() -> executor.Run()
	log.Println("dms-agent starting...")
}
