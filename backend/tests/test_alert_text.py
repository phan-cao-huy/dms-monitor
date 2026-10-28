from app.services.alert_text import alert_text


def test_ram_copy_explains_measurement_threshold_and_duration_in_both_languages():
    copy = alert_text("RAM", "PC-01", 88, 50.3, ">=", 2)
    assert "PC-01" in copy["title_vi"]
    assert "88%" in copy["body_vi"] and "ngưỡng cảnh báo 50,3%" in copy["body_vi"]
    assert "2 phút" in copy["body_vi"]
    assert "RAM alert" in copy["title_en"] and "2 min" in copy["body_en"]


def test_low_threshold_and_offline_copy_are_not_described_as_high_usage():
    copy = alert_text("CPU", "PC", 10, 20, "<")
    assert "below the 20%" in copy["body_en"]
    offline = alert_text("OFFLINE", "PC", 7, 5)
    assert "7 min" in offline["body_en"]
    assert "mất kết nối" in offline["title_vi"]

def test_alert_names_actual_top_memory_processes_and_does_not_blame_missing_gpu():
    processes=[{"pid":1,"name":"small.exe","ram_mb":20,"cpu_pct":70},
               {"pid":2,"name":"browser.exe","ram_mb":4096,"cpu_pct":1}]
    copy=alert_text("RAM","PC",88,80,processes=processes)
    assert "browser.exe (PID 2): 4096 MB" in copy["body_en"]
    assert copy["body_en"].index("browser.exe")<copy["body_en"].index("small.exe")
    assert "Per-process usage is unavailable" in alert_text("GPU","PC",95,80,processes=processes)["body_en"]

def test_gpu_observation_reads_live_snapshot_and_preserves_missing_values():
    from datetime import UTC, datetime
    from types import SimpleNamespace

    from app.workers.rules import rule_observation
    now=datetime.now(UTC).replace(tzinfo=None)
    rule=SimpleNamespace(metric_type="GPU",condition=">=",threshold=80)
    device=SimpleNamespace(archived_at=None,is_monitoring=True,last_heartbeat=now,static_info={"gpu_usage_pct":90})
    assert rule_observation(rule,device,None,now)==(True,90.0)
    device.static_info={}
    assert rule_observation(rule,device,None,now)==(None,None)
