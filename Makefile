.PHONY: up down restart backend build-backend test-backend logs

# Start everything (Postgres + Backend)
up: build-backend
	docker compose up -d

# Start/rebuild only the backend (test → build JAR → deploy)
backend: build-backend
	docker compose up -d flyway-migrator
	docker compose up -d --build backend

# Run backend unit and integration tests
test-backend:
	cd backend && ./gradlew :server:test --no-daemon

# Build the backend fat JAR (runs tests first)
build-backend: test-backend
	cd backend && ./gradlew :server:buildFatJar --no-daemon

# Stop all services
down:
	docker compose down

# Restart backend only (test → rebuild JAR + image, runs flyway-migrator first)
restart: build-backend
	docker compose up -d flyway-migrator
	docker compose up -d --build backend

# Tail backend logs
logs:
	docker compose logs -f backend
