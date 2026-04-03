.PHONY: up down restart backend build-backend test-backend logs train-model backup restore

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

# Dump full database to a timestamped SQL file under backups/
backup:
	mkdir -p backups
	docker compose exec -T postgres pg_dump -U ebalance ebalance > backups/ebalance_$$(date +%Y%m%d_%H%M%S).sql
	@echo "Backup saved to backups/"

# Restore database from a SQL backup file: make restore FILE=backups/ebalance_20240101_020000.sql
restore:
ifndef FILE
	$(error FILE is required. Usage: make restore FILE=backups/ebalance_YYYYMMDD_HHMMSS.sql)
endif
	@echo "Restoring from $(FILE)..."
	docker compose exec -T postgres psql -U ebalance -d ebalance < $(FILE)
	@echo "Restore complete."

# Build CLI distribution and train the neural-network model (saves nn-model.bin)
train-model:
	./gradlew installDist
	./build/install/e-balance/bin/e-balance --model nn-model.bin train --engine neural-network
