# 🚀 APISIX Control Plane

A comprehensive Control Plane system for Apache APISIX with multi-tenancy support, API revisioning, and deployment management.

## 📋 Features

### Multi-Tenancy
- **Organizations**: Support for multiple organizations (e.g., fintech, peopletech)
- **Environments**: Each organization can have multiple environments (QA, Prod, etc.)
- **Isolation**: Complete isolation between organizations
- **API Name Uniqueness**: API names are unique within each organization

### API Revisioning
- **Single Collection**: All revisions stored in `api-revisions` collection
- **Group by Name**: Retrieve all revisions of an API by grouping by name
- **Revision States**:
  - `DRAFT`: Not yet deployed, can be edited or deleted
  - `DEPLOYED`: Deployed to one or more environments, immutable for auditing
- **Deployment Tracking**: Each revision tracks which environments it's deployed to

### Operations
- ✅ Create organizations
- ✅ Create environments in an organization
- ✅ Create APIs (automatically creates revision 1)
- ✅ Create new revisions of existing APIs
- ✅ Update DRAFT revisions
- ✅ Delete DRAFT revisions
- ✅ Deploy revisions to selected environments
- ✅ Undeploy revisions from environments
- ✅ View all APIs and their revisions
- ✅ Track deployment status

### Validation Rules
- Cannot create an API with a name that already exists in the same organization
- At least one route is required for each API/Service
- Only DRAFT revisions can be edited or deleted
- DEPLOYED revisions are immutable
- API names must be unique within an organization across all environments

## 🏗️ Architecture

```
Control Plane
├── Organizations (Multi-tenancy)
│   ├── Environment 1 (QA) → APISIX Instance 1
│   ├── Environment 2 (Prod) → APISIX Instance 2
│   └── APIs
│       ├── API 1
│       │   ├── Revision 1 (DRAFT)
│       │   ├── Revision 2 (DEPLOYED to QA)
│       │   └── Revision 3 (DEPLOYED to QA, Prod)
│       └── API 2
│           └── Revision 1 (DRAFT)
```

## 🛠️ Tech Stack

- **Backend**: Java 17, Spring Boot 3.2.1
- **Database**: MongoDB
- **APISIX Integration**: WebFlux WebClient
- **Frontend**: HTML, CSS, JavaScript (Vanilla)

## 📦 Prerequisites

1. **Java 17** or higher
2. **Maven 3.6+**
3. **MongoDB 4.4+** running on `localhost:27017`
4. **Apache APISIX** (optional, for testing actual deployments)

## 🚀 Quick Start

### 1. Start MongoDB

```bash
# Using Docker
docker run -d -p 27017:27017 --name mongodb mongo:latest

# Or use your local MongoDB installation
mongod
```

### 2. (Optional) Start APISIX Instances

For testing deployments, you can start multiple APISIX instances:

```bash
# APISIX Instance 1 (QA)
docker run -d \
  --name apisix-qa \
  -p 9180:9180 \
  -p 9080:9080 \
  apache/apisix:latest

# APISIX Instance 2 (Prod)
docker run -d \
  --name apisix-prod \
  -p 9181:9180 \
  -p 9081:9080 \
  apache/apisix:latest
```

### 3. Build and Run the Control Plane

```bash
# Navigate to project directory
cd apisix-control-plane

# Build the project
mvn clean install

# Run the application
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

### 4. Access the Web UI

Open your browser and navigate to:
```
http://localhost:8080
```

## 📖 Usage Guide

### Step 1: Create an Organization

1. Go to the **Organizations** tab
2. Fill in the organization name and description
3. Click "Create Organization"

Example:
- Name: `fintech`
- Description: `Financial Technology Organization`

### Step 2: Create Environments

1. Go to the **Environments** tab
2. Select the organization
3. Fill in environment details:
   - Name: `qa`
   - APISIX Admin URL: `http://localhost:9180`
4. Click "Create Environment"

Repeat for production:
- Name: `prod`
- APISIX Admin URL: `http://localhost:9181`

### Step 3: Create an API

1. Go to the **APIs** tab
2. Select the organization
3. Fill in API details:
   - Name: `hello-world`
   - Description: `Hello World API`
   - Upstream: `httpbin.org:80`
4. Add routes:
   - Route Name: `get-anything`
   - Methods: `GET,POST`
   - URIs: `/anything,/api/anything`
5. Click "Create API"

This creates **Revision 1** in DRAFT state.

### Step 4: Deploy to QA

1. Go to the **Deployment** tab
2. Select the organization
3. Select the API (`hello-world`)
4. Select the revision (Revision 1)
5. Check the `qa` environment
6. Click "🚀 Deploy"

The API is now deployed to the QA APISIX instance!

### Step 5: Create a New Revision

1. Go to the **APIs** tab
2. Modify the configuration as needed
3. Click "Create Revision"

This creates **Revision 2** in DRAFT state while Revision 1 remains deployed to QA.

### Step 6: Deploy to Production

1. Go to the **Deployment** tab
2. Select Revision 2
3. Check both `qa` and `prod` environments
4. Click "🚀 Deploy"

## 🔌 API Endpoints

### Organizations

```
POST   /api/v1/organizations
GET    /api/v1/organizations
GET    /api/v1/organizations/{id}
DELETE /api/v1/organizations/{id}
```

### Environments

```
POST   /api/v1/organizations/{orgId}/environments
GET    /api/v1/organizations/{orgId}/environments
GET    /api/v1/organizations/{orgId}/environments/{envId}
DELETE /api/v1/organizations/{orgId}/environments/{envId}
```

### APIs & Revisions

```
POST   /api/v1/organizations/{orgId}/apis
POST   /api/v1/organizations/{orgId}/apis/{apiName}/revisions
GET    /api/v1/organizations/{orgId}/apis
GET    /api/v1/organizations/{orgId}/apis/{apiName}/revisions
GET    /api/v1/organizations/{orgId}/apis/revisions/{revisionId}
PUT    /api/v1/organizations/{orgId}/apis/revisions/{revisionId}
DELETE /api/v1/organizations/{orgId}/apis/revisions/{revisionId}
```

### Deployment

```
POST   /api/v1/organizations/{orgId}/apis/revisions/{revisionId}/deploy
POST   /api/v1/organizations/{orgId}/apis/revisions/{revisionId}/undeploy
```

## 📝 Example API Requests

### Create Organization

```bash
curl -X POST http://localhost:8080/api/v1/organizations \
  -H "Content-Type: application/json" \
  -d '{
    "name": "fintech",
    "description": "Financial Technology Org"
  }'
```

### Create Environment

```bash
curl -X POST http://localhost:8080/api/v1/organizations/{orgId}/environments \
  -H "Content-Type: application/json" \
  -d '{
    "name": "qa",
    "description": "QA Environment",
    "apisixAdminUrl": "http://localhost:9180",
    "active": true
  }'
```

### Create API

```bash
curl -X POST http://localhost:8080/api/v1/organizations/{orgId}/apis \
  -H "Content-Type: application/json" \
  -d '{
    "name": "hello-world",
    "description": "Hello World API",
    "serviceConfig": {
      "upstream": "httpbin.org:80",
      "plugins": {},
      "metadata": {}
    },
    "routes": [
      {
        "name": "get-anything",
        "methods": ["GET", "POST"],
        "uris": ["/anything"],
        "plugins": {},
        "metadata": {}
      }
    ]
  }'
```

### Deploy Revision

```bash
curl -X POST http://localhost:8080/api/v1/organizations/{orgId}/apis/revisions/{revisionId}/deploy \
  -H "Content-Type: application/json" \
  -d '{
    "environmentId": "env-id-1"
  }'
```

## 🧪 Testing Workflow

1. **Create Organization**: `fintech`
2. **Create Environments**: `qa`, `prod`
3. **Create API**: `user-service` (creates Revision 1 in DRAFT)
4. **Deploy to QA**: Deploy Revision 1 to `qa` environment
5. **Test in QA**: Verify the API works correctly
6. **Deploy to Prod**: Deploy Revision 1 to `prod` environment
7. **Create New Revision**: Create Revision 2 with updated configuration
8. **Deploy to QA**: Deploy Revision 2 to `qa` for testing
9. **Deploy to Prod**: After testing, deploy Revision 2 to `prod`
10. **Rollback if needed**: Undeploy Revision 2, redeploy Revision 1

## 🔧 Configuration

Edit `src/main/resources/application.yml`:

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/apisix-control-plane

server:
  port: 8080

apisix:
  admin:
    key: edd1c9f034335f136f87ad84b625c8f1  # APISIX admin key
    timeout: 30000
```

## 📊 Database Collections

### organizations
```json
{
  "_id": "org-id",
  "name": "fintech",
  "description": "Financial Technology Organization",
  "createdAt": "2026-01-20T10:00:00",
  "updatedAt": "2026-01-20T10:00:00"
}
```

### environments
```json
{
  "_id": "env-id",
  "orgId": "org-id",
  "name": "qa",
  "description": "QA Environment",
  "apisixAdminUrl": "http://localhost:9180",
  "active": true,
  "createdAt": "2026-01-20T10:00:00",
  "updatedAt": "2026-01-20T10:00:00"
}
```

### api-revisions
```json
{
  "_id": "revision-id",
  "orgId": "org-id",
  "name": "hello-world",
  "revisionNumber": 1,
  "description": "Hello World API",
  "state": "DEPLOYED",
  "deployedEnvironments": ["env-id-1", "env-id-2"],
  "serviceConfig": {
    "upstream": "httpbin.org:80",
    "plugins": {},
    "metadata": {}
  },
  "routes": [
    {
      "name": "get-anything",
      "methods": ["GET", "POST"],
      "uris": ["/anything"],
      "plugins": {},
      "metadata": {}
    }
  ],
  "createdAt": "2026-01-20T10:00:00",
  "updatedAt": "2026-01-20T10:00:00"
}
```

## 🎯 Key Business Rules

1. **API Name Uniqueness**: Within an organization, API names must be unique across all environments
2. **Route Requirement**: Every API/Service must have at least one route
3. **Draft Mutability**: Only DRAFT revisions can be edited or deleted
4. **Deployed Immutability**: DEPLOYED revisions are immutable for rollback and auditing
5. **Multi-Deployment**: A single revision can be deployed to multiple environments
6. **Automatic State Change**: When a DRAFT revision is deployed, it becomes DEPLOYED
7. **State Rollback**: When all environments are undeployed, state reverts to DRAFT

## 🐛 Troubleshooting

### MongoDB Connection Issues
```bash
# Check if MongoDB is running
mongosh --eval "db.adminCommand('ping')"

# Check connection string in application.yml
spring.data.mongodb.uri: mongodb://localhost:27017/apisix-control-plane
```

### APISIX Deployment Failures
- Verify APISIX is running: `curl http://localhost:9180/apisix/admin/services`
- Check APISIX admin key matches the configuration
- Ensure the upstream is reachable

### Port Already in Use
```bash
# Change the port in application.yml
server:
  port: 8081
```

## 📚 Project Structure

```
apisix-control-plane/
├── src/main/java/com/apisix/controlplane/
│   ├── ControlPlaneApplication.java
│   ├── config/
│   │   └── WebClientConfig.java
│   ├── controller/
│   │   ├── OrganizationController.java
│   │   ├── EnvironmentController.java
│   │   ├── ApiRevisionController.java
│   │   └── HealthController.java
│   ├── dto/
│   │   ├── CreateOrgRequest.java
│   │   ├── CreateEnvironmentRequest.java
│   │   ├── CreateApiRequest.java
│   │   └── DeploymentRequest.java
│   ├── entity/
│   │   ├── Organization.java
│   │   ├── Environment.java
│   │   └── ApiRevision.java
│   ├── enums/
│   │   └── RevisionState.java
│   ├── exception/
│   │   ├── BusinessException.java
│   │   ├── ResourceNotFoundException.java
│   │   └── GlobalExceptionHandler.java
│   ├── repository/
│   │   ├── OrganizationRepository.java
│   │   ├── EnvironmentRepository.java
│   │   └── ApiRevisionRepository.java
│   └── service/
│       ├── OrganizationService.java
│       ├── EnvironmentService.java
│       ├── ApiRevisionService.java
│       └── ApisixIntegrationService.java
├── src/main/resources/
│   ├── application.yml
│   └── static/
│       ├── index.html
│       ├── styles.css
│       └── app.js
├── pom.xml
└── README.md
```

## 🤝 Contributing

Feel free to submit issues and enhancement requests!

## 📄 License

This project is for educational and demonstration purposes.

## 🎉 Happy API Management!

For questions or support, please create an issue in the repository.

