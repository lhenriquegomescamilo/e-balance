.PHONY: up down restart backend build-backend logs

# Start everything (Postgres + Backend)
up: build-backend
	docker compose up -d

# Start/rebuild only the backend
backend: build-backend
	docker compose up -d --build backend

# Build the backend fat JAR (required before docker compose build)
build-backend:
	cd backend && ./gradlew :server:buildFatJar --no-daemon

# Stop all services
down:
	docker compose down

# Restart backend only (rebuild JAR + image)
restart: build-backend
	docker compose up -d --build backend

# Tail backend logs
logs:
	docker compose logs -f backend
