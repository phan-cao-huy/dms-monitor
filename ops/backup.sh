#!/bin/sh
set -eu
umask 077
# One UTC-dated archive per day. A missed run is caught up on next startup.
while :; do
    day=$(date -u +%F)
    archive="/archives/dms-${day}.dump"
    if [ ! -s "$archive" ]; then
        tmp=$(mktemp /archives/.dms-XXXXXX)
        if pg_dump --format=custom --no-owner --file="$tmp" &&
           pg_restore --file=/dev/null "$tmp" && mv "$tmp" "$archive"; then
            echo "Daily database backup verified: $day"
            find /archives -maxdepth 1 -type f -name 'dms-????-??-??.dump' -mtime +13 -delete
        else
            rm -f "$tmp"
            printf '{"status":"failed","checked_at":"%s"}\n' "$(date -u +%FT%TZ)" >/state/backup.tmp
            mv /state/backup.tmp /state/backup.json
            sleep 300
            continue
        fi
    fi
    printf '{"status":"ready","date":"%s","bytes":%s,"checked_at":"%s"}\n' "$day" "$(wc -c <"$archive")" "$(date -u +%FT%TZ)" >/state/backup.tmp
    mv /state/backup.tmp /state/backup.json
    sleep 300
done
