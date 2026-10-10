#!/usr/bin/env bash
# Cách dùng: ./push_to_github.sh <github-username> [ten-repo]
# Yêu cầu: git + (tuỳ chọn) GitHub CLI `gh` đã `gh auth login`
set -euo pipefail
USER="${1:?Nhập GitHub username/org}"; REPO="${2:-dms-monitor}"

sed -i "s#YOUR_ORG#${USER}#g" agent/go.mod

git init -b main
git add .
git commit -m "chore: initial monorepo scaffold (backend, agent, mobile, database, docs)"

if command -v gh >/dev/null; then
  gh repo create "${USER}/${REPO}" --private --source=. --remote=origin --push
else
  echo ">> Chưa có gh. Tạo repo rỗng '${REPO}' trên github.com rồi chạy:"
  echo "   git remote add origin https://github.com/${USER}/${REPO}.git && git push -u origin main"
fi

git checkout -b develop && git push -u origin develop
echo "Xong. Nhớ bật Branch protection cho main trong Settings > Branches."
