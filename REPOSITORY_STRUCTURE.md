# Repository Structure

This repository (`spiffe-like`) is the **platform orchestration layer** that brings together all the individual service repositories.

## Repository Organization

The SPIFFE-like platform is organized as follows:

### Platform Repository (This Repo)
**Repository**: `spiffe-like` (or `spiffe-like-platform`)
**Purpose**: Orchestration, integration, and platform-level documentation
**Contains**:
- `docker-compose.yml` - Orchestrates all services
- `Makefile` - Build and deployment automation
- `test-api.sh` - Integration tests
- `README.md` - Main platform documentation
- `ARCHITECTURE.md` - Overall system architecture
- Setup and deployment scripts

### Service Repositories

Each service is in its own repository:

1. **spiffe-common**
   - Repository: `https://github.com/delegation-platform-spiffe/spiffe-common`
   - Purpose: Shared library for authentication and delegation
   - Contains: Common components used by all services

2. **workload-api-service**
   - Repository: `https://github.com/delegation-platform-spiffe/workload-api-service`
   - Purpose: Certificate Authority and Workload API
   - Port: 8080

3. **user-service**
   - Repository: `https://github.com/delegation-platform-spiffe/user-service`
   - Purpose: Authentication and delegation token issuer
   - Port: 8081

4. **photo-service**
   - Repository: `https://github.com/delegation-platform-spiffe/photo-service`
   - Purpose: Photo storage service with mTLS support
   - Port: 8082

5. **print-service**
   - Repository: `https://github.com/delegation-platform-spiffe/print-service`
   - Purpose: Photo printing service with mTLS support
   - Port: 8083

## Why This Structure?

### Benefits of Separate Repositories

1. **Independent Versioning**: Each service can be versioned independently
2. **Team Ownership**: Different teams can own different services
3. **CI/CD**: Each service can have its own CI/CD pipeline
4. **Dependency Management**: Services can depend on specific versions of `spiffe-common`
5. **Deployment Flexibility**: Services can be deployed independently

### Benefits of Platform Repository

1. **Integration Testing**: End-to-end tests that verify all services work together
2. **Orchestration**: Docker Compose configuration for local development
3. **Documentation**: Centralized documentation about the entire platform
4. **Quick Start**: Single repository to clone for getting started
5. **Deployment**: Platform-level deployment scripts and configurations

## Getting Started

### Option 1: Clone Platform Repository (Recommended for Development)

```bash
git clone https://github.com/delegation-platform-spiffe/spiffe-like.git
cd spiffe-like

# Services are referenced as git submodules or cloned separately
# For Docker Compose, services are built from their Dockerfiles
docker-compose up --build
```

### Option 2: Clone Individual Service Repositories

```bash
# Clone each service
git clone https://github.com/delegation-platform-spiffe/spiffe-common.git
git clone https://github.com/delegation-platform-spiffe/workload-api-service.git
git clone https://github.com/delegation-platform-spiffe/user-service.git
git clone https://github.com/delegation-platform-spiffe/photo-service.git
git clone https://github.com/delegation-platform-spiffe/print-service.git

# Use docker-compose from platform repo or configure manually
```

## Development Workflow

1. **Service Development**: Work in individual service repositories
2. **Integration Testing**: Use platform repository for end-to-end tests
3. **Local Development**: Use `docker-compose.yml` from platform repository
4. **Documentation**: Update service-specific docs in service repos, platform docs here

## Repository URLs

All repositories are under the GitHub organization:
- Organization: `delegation-platform-spiffe`
- Base URL: `https://github.com/delegation-platform-spiffe/`

## Future: Git Submodules (Optional)

If you want to link the service repositories as submodules:

```bash
git submodule add https://github.com/delegation-platform-spiffe/spiffe-common.git spiffe-common
git submodule add https://github.com/delegation-platform-spiffe/workload-api-service.git workload-api-service
git submodule add https://github.com/delegation-platform-spiffe/user-service.git user-service
git submodule add https://github.com/delegation-platform-spiffe/photo-service.git photo-service
git submodule add https://github.com/delegation-platform-spiffe/print-service.git print-service
```

This would allow:
- Cloning the platform repo with `git clone --recursive`
- Tracking specific versions of each service
- Easier dependency management

However, for Docker Compose, the current structure (separate repos) works well since Docker builds from Dockerfiles.

