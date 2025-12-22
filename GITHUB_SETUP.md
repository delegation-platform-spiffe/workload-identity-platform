# GitHub Repository Setup Instructions

This document provides instructions for pushing all projects to the GitHub organization `delegation-platform-spiffe`.

## Prerequisites

1. **GitHub Access**: You must have access to the `delegation-platform-spiffe` organization
2. **SSH Keys**: Configure SSH keys for GitHub authentication
3. **Git Configuration**: Ensure your git user name and email are configured

## Projects to Push

The following projects will be pushed as separate repositories:

1. **spiffe-common** - Shared authentication and delegation components
2. **workload-api-service** - Certificate Authority and Workload API
3. **user-service** - Authentication and delegation token issuer
4. **photo-service** - Photo storage service with mTLS support
5. **print-service** - Photo printing service with mTLS support

## Option 1: Automated Push (Recommended)

### Using the Push Script

1. **Run the push script**:
   ```bash
   ./push-to-github.sh
   ```

2. The script will:
   - Check if repositories exist
   - Create them if GitHub CLI is installed
   - Push all code to GitHub

### Using GitHub CLI (if installed)

If you have GitHub CLI (`gh`) installed, you can create repositories automatically:

```bash
# Authenticate with GitHub
gh auth login

# Run the push script (it will create repos automatically)
./push-to-github.sh
```

## Option 2: Manual Setup

### Step 1: Create Repositories on GitHub

For each project, create a repository on GitHub:

1. Go to: https://github.com/organizations/delegation-platform-spiffe/repositories/new
2. Create repositories with these names:
   - `spiffe-common`
   - `workload-api-service`
   - `user-service`
   - `photo-service`
   - `print-service`
3. **Important**: Do NOT initialize with README, .gitignore, or license (we already have these)

### Step 2: Push Each Repository

For each project, run these commands:

```bash
# Navigate to project directory
cd spiffe-common  # (or other project)

# Add remote
git remote add origin git@github.com:delegation-platform-spiffe/spiffe-common.git

# Push to GitHub
git push -u origin main
```

Repeat for each project:
- `workload-api-service`
- `user-service`
- `photo-service`
- `print-service`

## Option 3: Individual Push Commands

Here are the exact commands for each repository:

### spiffe-common
```bash
cd spiffe-common
git remote add origin git@github.com:delegation-platform-spiffe/spiffe-common.git
git push -u origin main
```

### workload-api-service
```bash
cd workload-api-service
git remote add origin git@github.com:delegation-platform-spiffe/workload-api-service.git
git push -u origin main
```

### user-service
```bash
cd user-service
git remote add origin git@github.com:delegation-platform-spiffe/user-service.git
git push -u origin main
```

### photo-service
```bash
cd photo-service
git remote add origin git@github.com:delegation-platform-spiffe/photo-service.git
git push -u origin main
```

### print-service
```bash
cd print-service
git remote add origin git@github.com:delegation-platform-spiffe/print-service.git
git push -u origin main
```

## Verify Push

After pushing, verify all repositories are available:

- https://github.com/delegation-platform-spiffe/spiffe-common
- https://github.com/delegation-platform-spiffe/workload-api-service
- https://github.com/delegation-platform-spiffe/user-service
- https://github.com/delegation-platform-spiffe/photo-service
- https://github.com/delegation-platform-spiffe/print-service

## Troubleshooting

### SSH Key Issues

If you get authentication errors:

1. **Check SSH key**:
   ```bash
   ssh -T git@github.com
   ```

2. **Add SSH key to GitHub**:
   - Go to: https://github.com/settings/keys
   - Add your public key

### Permission Issues

If you get permission denied:

1. Verify you have access to the organization
2. Check organization settings: https://github.com/organizations/delegation-platform-spiffe/settings/members
3. Ensure you have repository creation permissions

### Repository Already Exists

If a repository already exists:

1. **Update remote URL**:
   ```bash
   git remote set-url origin git@github.com:delegation-platform-spiffe/<repo-name>.git
   ```

2. **Force push** (if needed, be careful!):
   ```bash
   git push -u origin main --force
   ```

## Next Steps

After pushing all repositories:

1. **Add descriptions** to each repository on GitHub
2. **Add topics/tags** for better discoverability
3. **Set up branch protection** rules if needed
4. **Configure CI/CD** workflows if desired
5. **Add README badges** showing build status, etc.

