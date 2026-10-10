up:
	docker compose up -d --build
down:
	docker compose down
logs:
	docker compose logs -f backend
db-reset:
	docker compose down -v && docker compose up -d db
agent:
	cd agent && go run ./cmd/agent
test-be:
	cd backend && pytest -q
