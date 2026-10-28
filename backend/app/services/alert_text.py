"""Human-readable alert copy, shared by push and API consumers."""

def alert_text(kind, device_name, value=None, threshold=None, condition=">=", duration=1,
               service_name=None, process_name=None, processes=None):
    def num(v):
        return "—" if v is None else (f"{v:g}" if lang == "en" else f"{v:g}".replace(".", ","))
    labels = {"CPU": ("CPU", "CPU"), "RAM": ("RAM", "RAM"), "GPU": ("GPU (VGA)", "GPU (graphics)"), "DISK": ("Ổ đĩa", "Disk"),
              "NET_IN": ("Mạng tải xuống", "Download"), "NET_OUT": ("Mạng tải lên", "Upload")}
    result = {}
    for lang in ("vi", "en"):
        en = lang == "en"
        if kind == "OFFLINE":
            title = f"Device offline · {device_name}" if en else f"Máy mất kết nối · {device_name}"
            body = (f"No heartbeat for {num(value)} min.\nOffline threshold: {num(threshold)} min."
                    if en else f"Không nhận tín hiệu trong {num(value)} phút.\nNgưỡng mất kết nối: {num(threshold)} phút.")
        elif kind in ("SERVICE", "PROCESS_MISSING"):
            target = service_name or process_name or "—"
            title = f"Service stopped · {device_name}" if en else f"Dịch vụ ngừng chạy · {device_name}"
            if kind == "PROCESS_MISSING":
                title = f"Process missing · {device_name}" if en else f"Tiến trình ngừng chạy · {device_name}"
            body = f"{target}\nNot running for at least {duration} min." if en else f"{target}\nKhông hoạt động ít nhất {duration} phút."
        elif kind in labels:
            label = labels[kind][1 if en else 0]
            unit = "%" if kind in ("CPU", "RAM", "DISK", "GPU") else " KB/s"
            title = f"{label} alert · {device_name}" if en else f"Cảnh báo {label} · {device_name}"
            relation_vi = {">": "vượt ngưỡng", ">=": "đã đạt ngưỡng cảnh báo", "<": "thấp hơn ngưỡng", "<=": "bằng/thấp hơn ngưỡng"}.get(condition,"đạt ngưỡng")
            relation_en = {">": "above", ">=": "at or above", "<": "below", "<=": "at or below"}.get(condition,"at")
            body = (f"{label} is at {num(value)}{unit}, {relation_en} the {num(threshold)}{unit} threshold for {duration} min."
                    if en else f"{label} đang ở mức {num(value)}{unit}, {relation_vi} {num(threshold)}{unit} trong {duration} phút.")
            field = {"CPU": "cpu_pct", "RAM": "ram_mb", "GPU": "gpu_usage_pct"}.get(kind)
            if field:
                def usage(p, field=field):
                    v = p.get(field, p.get("cpu_percent") if kind == "CPU" else None)
                    try:
                        return float(v) if v is not None else -1
                    except (ValueError, TypeError):
                        return -1
                candidates = sorted((p for p in (processes or []) if isinstance(p,dict) and usage(p)>=0), key=usage, reverse=True)[:2]
                if candidates:
                    unit_process = " MB" if kind == "RAM" else "%"
                    details = "; ".join(f"{str(p.get('name','?'))[:60]} (PID {p.get('pid','?')}): {num(usage(p))}{unit_process}" for p in candidates)
                    body += ("\nHighest " + label + " in latest sample: " if en else "\nDùng " + label + " nhiều nhất trong mẫu gần nhất: ") + details
                else:
                    body += "\nPer-process usage is unavailable." if en else "\nChưa có số liệu theo tiến trình."

        else:
            title = f"Device alert · {device_name}" if en else f"Cảnh báo thiết bị · {device_name}"
            body = "Open DMS Monitor for details." if en else "Mở DMS Monitor để xem chi tiết."
        result[f"title_{lang}"] = title
        result[f"body_{lang}"] = body
    return result
