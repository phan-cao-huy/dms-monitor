"""Bounded probes, with DNS pinning and validation at every redirect hop."""
import asyncio
import ipaddress
import socket
import ssl
import time
from datetime import UTC, datetime
from urllib.parse import quote, urljoin

from app.core.config import settings
from app.schemas.monitoring import target_parts


class TargetBlocked(ValueError):
    pass


def permitted_ip(value):
    address = ipaddress.ip_address(value)
    if address.version == 6 and address.ipv4_mapped:
        address = address.ipv4_mapped
    # Even an allowlist must never grant loopback, link-local or metadata access.
    if address.is_loopback or address.is_link_local or address.is_multicast or address.is_unspecified or address.is_reserved:
        return False
    if str(address) == "100.100.100.200":
        return False
    if address.version == 6 and address in ipaddress.ip_network("64:ff9b::/96"):
        return permitted_ip(str(ipaddress.IPv4Address(int(address) & 0xffffffff)))
    if address.version == 6 and address.sixtofour:
        return permitted_ip(str(address.sixtofour))
    if address.is_global:
        return True
    return any(address in ipaddress.ip_network(cidr.strip()) for cidr in settings.SYNTHETIC_ALLOWED_CIDRS.split(",") if cidr.strip())


async def resolve_target(host, port):
    rows = await asyncio.get_running_loop().getaddrinfo(host, port, type=socket.SOCK_STREAM)
    ips = list(dict.fromkeys(row[4][0] for row in rows))
    if not ips or any(not permitted_ip(ip) for ip in ips):
        raise TargetBlocked("TARGET_BLOCKED")
    return ips[0]  # Connect directly to this validated address; never re-resolve in the client.


async def stream(host, port, ip, tls):
    return await asyncio.open_connection(ip, port, ssl=ssl.create_default_context() if tls else None,
                                         server_hostname=host if tls else None, limit=65536)


async def http_probe(target):
    for hop in range(4):
        host, port, url = target_parts("HTTP", target)
        ip = await resolve_target(host, port)
        reader, writer = await stream(host, port, ip, url.scheme == "https")
        try:
            hostname = host.encode("idna").decode()
            authority = f"[{hostname}]:{port}" if ":" in hostname else f"{hostname}:{port}"
            path = quote(url.path or "/", safe="/%:@!$&'()*+,;=-._~") + ("?" + quote(url.query, safe="%=&/?@:+,;!$'()*-._~") if url.query else "")
            request = f"GET {path} HTTP/1.1\r\nHost: {authority}\r\nUser-Agent: DMS-Check/1.0\r\nConnection: close\r\n\r\n"
            writer.write(request.encode("ascii"))
            await writer.drain()
            header = await reader.readuntil(b"\r\n\r\n")
            lines = header.decode("iso-8859-1").split("\r\n")
            status = int(lines[0].split()[1])
            location = next((x.split(":", 1)[1].strip() for x in lines[1:] if x.lower().startswith("location:")), None)
        finally:
            writer.close()
        if status in (301, 302, 303, 307, 308) and location:
            if hop == 3:
                return False, "TOO_MANY_REDIRECTS"
            target = urljoin(target, location)
            if any(ord(c) <= 32 or ord(c) == 127 for c in target) or "\\" in target:
                raise TargetBlocked("TARGET_BLOCKED")
            continue
        return 200 <= status < 400, f"HTTP_{status}"
    return False, "TOO_MANY_REDIRECTS"


async def probe(check):
    started = time.monotonic()
    try:
        async with asyncio.timeout(check.timeout_seconds):
            if check.type == "HTTP":
                ok, detail = await http_probe(check.target)
            else:
                host, port, _ = target_parts(check.type, check.target)
                ip = await resolve_target(host, port)
                if check.type == "PING":
                    process = await asyncio.create_subprocess_exec("ping", "-n", "-c", "1", "-W", str(check.timeout_seconds), ip,
                                                                    stdout=asyncio.subprocess.DEVNULL, stderr=asyncio.subprocess.DEVNULL)
                    try:
                        ok = await process.wait() == 0
                    finally:
                        if process.returncode is None:
                            process.kill()
                            await process.wait()
                    detail = "PING_OK" if ok else "PING_FAILED"
                else:
                    _, writer = await stream(host, port, ip, check.type == "SSL")
                    try:
                        if check.type == "SSL":
                            certificate = writer.get_extra_info("ssl_object").getpeercert()
                            expiry = ssl.cert_time_to_seconds(certificate["notAfter"])
                            days = (expiry - datetime.now(UTC).timestamp()) / 86400
                            ok, detail = days > 14, f"SSL_DAYS:{max(0, int(days))}"
                        else:
                            ok, detail = True, "TCP_OK"
                    finally:
                        writer.close()
    except TargetBlocked:
        ok, detail = False, "TARGET_BLOCKED"
    except TimeoutError:
        ok, detail = False, "TIMEOUT"
    except ssl.SSLError:
        ok, detail = False, "TLS_VALIDATION_FAILED"
    except FileNotFoundError:
        ok, detail = False, "PING_UNAVAILABLE"
    except (OSError, ValueError, KeyError, UnicodeError, asyncio.IncompleteReadError, asyncio.LimitOverrunError):
        ok, detail = False, "CONNECTION_FAILED"
    return ok, round((time.monotonic() - started) * 1000, 2), detail
