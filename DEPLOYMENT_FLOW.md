# API Revision Deployment Flow

## Overview
This document describes the revised deployment and undeployment flow that enforces the **single active revision policy**: Only one API revision can be active (deployed) per environment at any given time.

## Key Requirements Implemented

### 1. Single Active Revision Policy
- ✅ Only one revision of an API can be deployed to a given environment at any time
- ✅ Automatically enforced during deployment (no manual intervention required)
- ✅ When deploying a new revision, any other deployed revision is automatically undeployed first

### 2. Deployment Flow

#### Step 1: Pre-Deployment Validation
```
1. Validate that the revision exists
2. Validate that upstream is configured for each target environment
3. Validate that upstream belongs to the correct environment
```

#### Step 2: Check for Conflicting Revisions
```
For each target environment:
  1. Find all revisions of the same API
  2. Check if any other revision is DEPLOYED to this environment
  3. If found:
     IF force = false:
       - Throw error: "Another revision is already deployed. Please undeploy it first or use force deploy."
       - User must manually undeploy first
     
     IF force = true:
       a. Force delete service and routes from APISIX
       b. Update environment status flag to UNDEPLOYED
       c. Update last_undeployed_at timestamp
       d. Recalculate parent revision state
       e. Save the other revision
```

#### Step 3: Deploy New Revision
```
For each target environment:
  1. Deploy service and routes to APISIX
  2. Update environment status flag to DEPLOYED
  3. Update last_deployed_at timestamp
```

#### Step 4: Update Parent State
```
1. Recalculate overall revision state based on all environment statuses
2. Save the revision
3. Log completion with final state
```

### 3. Undeployment Flow

#### Step 1: Pre-Undeployment Validation
```
1. Validate that the revision exists
2. For each environment:
   - Check if revision is actually deployed
   - Skip if not deployed (with warning)
```

#### Step 2: Force Delete from APISIX
```
For each target environment:
  1. Delete all routes from APISIX (force delete)
  2. Delete service from APISIX (force delete)
  3. Handle 404 errors gracefully (already deleted)
```

#### Step 3: Update Status
```
For each target environment:
  1. Update environment status flag to UNDEPLOYED
  2. Update last_undeployed_at timestamp
```

#### Step 4: Update Parent State
```
1. Recalculate overall revision state
2. Save the revision
3. Log completion with final state
```

## Status Management

### Environment Status Flags
Each revision maintains a map of environment statuses:
```json
{
  "environments": {
    "env-id-1": {
      "status": "DEPLOYED",
      "upstreamId": "upstream-123",
      "lastDeployedAt": "2026-01-22T10:30:00",
      "lastUndeployedAt": null
    },
    "env-id-2": {
      "status": "UNDEPLOYED",
      "upstreamId": "upstream-456",
      "lastDeployedAt": "2026-01-21T09:00:00",
      "lastUndeployedAt": "2026-01-22T11:00:00"
    }
  }
}
```

### Parent Revision State
The overall revision state is calculated based on all environment statuses:
- **DRAFT**: All environments are DRAFT or no environments configured
- **DEPLOYED**: At least one environment is DEPLOYED
- **UNDEPLOYED**: Was deployed but all environments are now UNDEPLOYED

Priority: `DEPLOYED > UNDEPLOYED > DRAFT`

## Force Flag Behavior

The `force` flag in deployment request controls the deployment behavior when conflicts exist:

### force = false (Manual Mode)
- **Same revision already deployed**: Error - "This revision is already deployed..."
- **Another revision deployed**: Error - "Another revision is already deployed. Please undeploy it first or use force deploy."
- **User must explicitly undeploy** other revisions before deploying new one
- **Safer, more explicit** - gives users full control

### force = true (Automatic Mode)
- **Same revision already deployed**: Allows redeployment (updates in APISIX)
- **Another revision deployed**: **Backend automatically undeploys** it first, then deploys new one
- **Convenient** - one-step operation for switching active revisions
- **Backend handles** the undeploy-then-deploy sequence

### Why This Design?

This gives users explicit control over deployment strategy:
- **Explicit mode (force=false)**: For careful, step-by-step deployments
- **Automatic mode (force=true)**: For quick revision switches and rollbacks

**Only one revision can be active at a time** - enforced in both modes.

## Error Handling

### During Deployment
- If auto-undeploy of another revision fails: Deployment is aborted with error
- If deployment to APISIX fails: Environment status remains in previous state

### During Undeployment
- If undeployment from APISIX fails: Error is thrown, status remains DEPLOYED
- 404 errors from APISIX are handled gracefully (resource already deleted)

## Example Scenarios

### Scenario 1a: Deploying a New Revision (force=false)
```
Initial State:
- API "user-service" Rev 1: DEPLOYED to dev, prod
- API "user-service" Rev 2: DRAFT

Action: Deploy Rev 2 to dev with force=false

Flow:
1. Find Rev 1 is deployed to dev
2. Throw error: "Another revision (Rev 1) is already deployed to environment 'dev'. 
   Please undeploy it first or use force deploy."
3. Operation aborted

Final State:
- API "user-service" Rev 1: DEPLOYED to dev, prod (unchanged)
- API "user-service" Rev 2: DRAFT (unchanged)

User must: Manually undeploy Rev 1 from dev, then deploy Rev 2
```

### Scenario 1b: Deploying a New Revision (force=true)
```
Initial State:
- API "user-service" Rev 1: DEPLOYED to dev, prod
- API "user-service" Rev 2: DRAFT

Action: Deploy Rev 2 to dev with force=true

Flow:
1. Find Rev 1 is deployed to dev
2. Auto-undeploy Rev 1 from APISIX dev environment
3. Update Rev 1 env status: dev=UNDEPLOYED
4. Recalculate Rev 1 state: DEPLOYED (still in prod)
5. Deploy Rev 2 to APISIX dev environment
6. Update Rev 2 env status: dev=DEPLOYED
7. Recalculate Rev 2 state: DEPLOYED

Final State:
- API "user-service" Rev 1: DEPLOYED to prod only
- API "user-service" Rev 2: DEPLOYED to dev
```

### Scenario 2: Deploying to Multiple Environments (force=true)
```
Initial State:
- API "payment-api" Rev 1: DEPLOYED to dev
- API "payment-api" Rev 2: DRAFT

Action: Deploy Rev 2 to [dev, staging, prod] with force=true

Flow:
For dev:
  1. Auto-undeploy Rev 1 from dev
  2. Deploy Rev 2 to dev
  
For staging:
  1. No other revision deployed
  2. Deploy Rev 2 to staging
  
For prod:
  1. No other revision deployed
  2. Deploy Rev 2 to prod

Final State:
- API "payment-api" Rev 1: UNDEPLOYED
- API "payment-api" Rev 2: DEPLOYED to dev, staging, prod
```

### Scenario 3: Rollback Scenario (force=true)
```
Initial State:
- API "order-api" Rev 3: DEPLOYED to prod (has issues)
- API "order-api" Rev 2: UNDEPLOYED (previous stable)

Action: Deploy Rev 2 to prod with force=true (rollback)

Flow:
1. Auto-undeploy Rev 3 from prod
2. Deploy Rev 2 to prod

Final State:
- API "order-api" Rev 3: UNDEPLOYED
- API "order-api" Rev 2: DEPLOYED to prod (rolled back)

Note: With force=false, user would need to:
  1. Manually undeploy Rev 3 from prod
  2. Then deploy Rev 2 to prod
```

## Technical Implementation

### Modified Methods

#### `ApiRevisionService.deployRevision()`
- **Lines 237-350**: Complete rewrite of deployment logic
- **Key Changes**:
  - Removed conditional check for force flag before undeploying other revisions
  - Now ALWAYS undeploys other revisions automatically
  - Enhanced logging for better tracking
  - Clearer error messages

#### `ApiRevisionService.undeployRevision()`
- **Lines 347-390**: Enhanced undeployment logic
- **Key Changes**:
  - Enhanced logging for tracking
  - Better error messages
  - Explicit status updates

### APISIX Integration
The `ApisixIntegrationService` already had proper force delete implementation:
- `undeployServiceAndRoutes()`: Deletes routes first, then service
- Handles 404 errors gracefully (resources already deleted)
- 500ms delay between route deletion and service deletion for APISIX processing

## Logging

Enhanced logging at each step:
- Deployment start: Revision ID, number, and target environments
- Auto-undeploy: Which revision is being undeployed and from which environment
- APISIX operations: Success/failure of each operation
- Status updates: State transitions
- Deployment complete: Final state of the revision

## Monitoring Points

Key events to monitor:
1. **Auto-undeploy triggers**: When one revision replaces another
2. **Deployment failures**: Failed to undeploy or deploy
3. **State transitions**: DRAFT → DEPLOYED → UNDEPLOYED
4. **Force redeployments**: Same revision redeployed with force flag

## Benefits

1. **Automatic Enforcement**: No manual steps required to ensure single active revision
2. **Consistency**: Same behavior across all environments
3. **Audit Trail**: All status changes are timestamped and logged
4. **Rollback Support**: Easy to rollback by deploying a previous revision
5. **Error Recovery**: Proper error handling ensures consistent state
6. **Zero Downtime**: New revision deployed immediately after old one is removed

