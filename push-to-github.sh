#!/bin/bash

# Script to push all projects to GitHub organization: delegation-platform-spiffe
# 
# Prerequisites:
# 1. You must have access to the GitHub organization: delegation-platform-spiffe
# 2. You must have authentication configured (SSH keys or GitHub CLI)
# 3. The repositories must be created on GitHub first (see instructions below)

set -e

ORG="delegation-platform-spiffe"
BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== Pushing Projects to GitHub Organization: $ORG ===${NC}\n"

# Array of projects
# Note: Root platform repository should be pushed separately as 'spiffe-like' or 'spiffe-like-platform'
projects=(
    "spiffe-common:SPIFFE Common Library - Shared authentication and delegation components"
    "workload-api-service:Workload API Service - Certificate Authority and Workload API"
    "user-service:User Service - Authentication and delegation token issuer"
    "photo-service:Photo Service - Photo storage service with mTLS support"
    "print-service:Print Service - Photo printing service with mTLS support"
)

# Root platform repository (orchestration layer)
ROOT_REPO="spiffe-like"
ROOT_DESCRIPTION="SPIFFE-like Platform - Orchestration and integration layer for all services"

# Function to create repository on GitHub (requires GitHub CLI or manual creation)
create_repo() {
    local repo_name=$1
    local description=$2
    
    if command -v gh &> /dev/null; then
        echo -e "${YELLOW}Creating repository $repo_name using GitHub CLI...${NC}"
        gh repo create "$ORG/$repo_name" \
            --public \
            --description "$description" \
            --source=. \
            --remote=origin \
            --push || echo -e "${YELLOW}Repository may already exist${NC}"
    else
        echo -e "${YELLOW}GitHub CLI not found. Please create the repository manually:${NC}"
        echo -e "  URL: https://github.com/organizations/$ORG/repositories/new"
        echo -e "  Repository name: $repo_name"
        echo -e "  Description: $description"
        echo -e "  Visibility: Public"
        echo -e "  Do NOT initialize with README, .gitignore, or license"
        echo ""
    fi
}

# Function to push repository
push_repo() {
    local repo_name=$1
    local description=$2
    local repo_dir="$BASE_DIR/$repo_name"
    
    if [ ! -d "$repo_dir" ]; then
        echo -e "${RED}Directory $repo_dir does not exist!${NC}"
        return 1
    fi
    
    cd "$repo_dir"
    
    echo -e "${BLUE}Processing: $repo_name${NC}"
    
    # Check if remote already exists
    if git remote get-url origin &> /dev/null; then
        echo -e "${YELLOW}Remote 'origin' already exists: $(git remote get-url origin)${NC}"
        read -p "Do you want to update it? (y/n) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            git remote set-url origin "git@github.com:$ORG/$repo_name.git"
        else
            echo -e "${YELLOW}Skipping $repo_name${NC}\n"
            return 0
        fi
    else
        # Add remote
        git remote add origin "git@github.com:$ORG/$repo_name.git" 2>/dev/null || \
        git remote set-url origin "git@github.com:$ORG/$repo_name.git"
    fi
    
    # Create repository if it doesn't exist (if GitHub CLI is available)
    if ! git ls-remote --heads origin main &> /dev/null; then
        create_repo "$repo_name" "$description"
        sleep 2  # Give GitHub a moment to create the repo
    fi
    
    # Push to GitHub
    echo -e "${YELLOW}Pushing $repo_name to GitHub...${NC}"
    if git push -u origin main; then
        echo -e "${GREEN}✓ Successfully pushed $repo_name${NC}\n"
    else
        echo -e "${RED}✗ Failed to push $repo_name${NC}"
        echo -e "${YELLOW}Make sure:${NC}"
        echo -e "  1. The repository exists on GitHub: https://github.com/$ORG/$repo_name"
        echo -e "  2. You have push access to the organization"
        echo -e "  3. Your SSH keys are configured correctly"
        echo ""
    fi
}

# Main execution
echo -e "${YELLOW}This script will push all projects to: https://github.com/$ORG${NC}"
echo -e "${YELLOW}Make sure you have:${NC}"
echo -e "  1. Access to the GitHub organization"
echo -e "  2. SSH keys configured for GitHub"
echo -e "  3. Repositories created (or GitHub CLI installed to create them automatically)"
echo ""
read -p "Continue? (y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Aborted."
    exit 1
fi

# Process each project
for project in "${projects[@]}"; do
    IFS=':' read -r repo_name description <<< "$project"
    push_repo "$repo_name" "$description"
done

# Push root platform repository
echo -e "${BLUE}Processing root platform repository: $ROOT_REPO${NC}"
cd "$BASE_DIR"
if [ -d ".git" ]; then
    if git remote get-url origin &> /dev/null; then
        echo -e "${YELLOW}Remote 'origin' already exists: $(git remote get-url origin)${NC}"
        read -p "Do you want to update it? (y/n) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            git remote set-url origin "git@github.com:$ORG/$ROOT_REPO.git"
        fi
    else
        git remote add origin "git@github.com:$ORG/$ROOT_REPO.git" 2>/dev/null || \
        git remote set-url origin "git@github.com:$ORG/$ROOT_REPO.git"
    fi
    
    if ! git ls-remote --heads origin main &> /dev/null; then
        create_repo "$ROOT_REPO" "$ROOT_DESCRIPTION"
        sleep 2
    fi
    
    echo -e "${YELLOW}Pushing $ROOT_REPO to GitHub...${NC}"
    if git push -u origin main; then
        echo -e "${GREEN}✓ Successfully pushed $ROOT_REPO${NC}\n"
    else
        echo -e "${RED}✗ Failed to push $ROOT_REPO${NC}\n"
    fi
else
    echo -e "${YELLOW}Root directory is not a git repository. Skipping.${NC}\n"
fi

echo -e "${GREEN}=== All projects processed ===${NC}"
echo -e "${BLUE}Repository URLs:${NC}"
echo -e "  https://github.com/$ORG/$ROOT_REPO (Platform/Orchestration)"
for project in "${projects[@]}"; do
    IFS=':' read -r repo_name description <<< "$project"
    echo -e "  https://github.com/$ORG/$repo_name"
done

