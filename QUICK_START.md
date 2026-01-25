# ⚡ Quick Start Guide

Get up and running with APISIX Control Plane in 5 minutes!

## 🎯 Option 1: Using Docker Compose (Recommended)

### Start all services at once

```bash
# Start MongoDB and APISIX instances
docker-compose up -d

# Wait a few seconds for services to start
sleep 10

# Run the Control Plane
mvn spring-boot:run
```

### Access the application
- **Web UI**: http://localhost:8080
- **APISIX QA**: http://localhost:9080 (Admin: http://localhost:9180)
- **APISIX Prod**: http://localhost:9081 (Admin: http://localhost:9181)
- **MongoDB**: localhost:27017

### Stop all services
```bash
docker-compose down
```

---

## 🎯 Option 2: Manual Setup

### 1. Start MongoDB
```bash
docker run -d -p 27017:27017 --name mongodb mongo:latest
```

### 2. (Optional) Start APISIX instances
```bash
# QA Environment
docker run -d \
  --name apisix-qa \
  -p 9180:9180 \
  -p 9080:9080 \
  apache/apisix:latest

# Production Environment
docker run -d \
  --name apisix-prod \
  -p 9181:9180 \
  -p 9081:9080 \
  apache/apisix:latest
```

### 3. Run Control Plane
```bash
mvn spring-boot:run
```

---

## 🧪 Test the System

### Step 1: Create Organization
Navigate to http://localhost:8080

1. Go to **Organizations** tab
2. Create organization: `fintech`

### Step 2: Create Environments
1. Go to **Environments** tab
2. Create QA environment:
   - Name: `qa`
   - APISIX URL: `http://host.docker.internal:9180` (or `http://localhost:9180`)
3. Create Prod environment:
   - Name: `prod`
   - APISIX URL: `http://host.docker.internal:9181` (or `http://localhost:9181`)

### Step 3: Create API
1. Go to **APIs** tab
2. Create API:
   - Name: `hello-world`
   - Upstream: `httpbin.org:80`
   - Route: GET /anything

### Step 4: Deploy
1. Go to **Deployment** tab
2. Select the API and revision
3. Select QA environment
4. Click **Deploy**

### Step 5: Test the API
```bash
# Test on QA
curl http://localhost:9080/anything

# Test on Prod (if deployed)
curl http://localhost:9081/anything
```

---

## 🎉 You're all set!

Explore the features:
- ✅ Create multiple organizations
- ✅ Set up different environments per org
- ✅ Create APIs with multiple routes
- ✅ Create revisions for API updates
- ✅ Deploy to selected environments
- ✅ Track deployment status

---

## 🐛 Troubleshooting

### MongoDB connection error
```bash
# Check if MongoDB is running
docker ps | grep mongodb

# Check connection
mongosh --eval "db.adminCommand('ping')"
```

### APISIX deployment fails
```bash
# Check APISIX is running
curl http://localhost:9180/apisix/admin/services \
  -H "X-API-KEY: edd1c9f034335f136f87ad84b625c8f1"

# For Docker Desktop on Mac/Windows, use:
# http://host.docker.internal:9180 instead of http://localhost:9180
```

### Port already in use
```bash
# Check what's using the port
lsof -i :8080

# Change port in application.yml
server:
  port: 8081
```

---

## 📚 Next Steps

- Read the full [README.md](README.md) for detailed documentation
- Explore the [API endpoints](README.md#-api-endpoints)
- Learn about [API revisioning workflow](README.md#-testing-workflow)

Happy API Management! 🎉

