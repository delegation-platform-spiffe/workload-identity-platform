.PHONY: build up down logs restart clean

# Build all services
build:
	docker-compose build

# Start all services
up:
	docker-compose up -d

# Start all services with logs
up-logs:
	docker-compose up

# Stop all services
down:
	docker-compose down

# Stop and remove volumes
clean:
	docker-compose down -v

# View logs
logs:
	docker-compose logs -f

# Restart all services
restart:
	docker-compose restart

# Rebuild and restart
rebuild:
	docker-compose up -d --build

# Check status
status:
	docker-compose ps

# Health check
health:
	@echo "Checking service health..."
	@curl -s http://localhost:8080/workload/v1/health || echo "Workload API: DOWN"
	@curl -s -X POST http://localhost:8081/auth/validate -H "Content-Type: application/json" -d '{"token":"test"}' || echo "User Service: DOWN"
	@curl -s http://localhost:8082/photos/users/test || echo "Photo Service: DOWN"
	@curl -s http://localhost:8083/print/status/test || echo "Print Service: DOWN"

# Run API tests
test:
	@./test-api.sh




