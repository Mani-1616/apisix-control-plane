# Implementation Summary: Single Active Revision with Force Flag

## ✅ Implementation Complete

The backend deploy and undeploy implementation has been updated to enforce the **single active revision policy** with proper force flag handling.

---

## Core Requirement

**Only one API revision can be DEPLOYED to a given environment at any time.**

This is enforced through the force flag mechanism:

### force = false (Default)
- **User must manually undeploy** other revisions before deploying a new one
- Throws error if another revision is already deployed
- Gives users explicit control over the deployment process

### force = true
- **Backend automatically undeploys** other revisions before deploying the new one
- One-step operation for quick revision switching
- Ideal for rollbacks and automated deployments

---

## Implementation Details

### File Modified
**`ApiRevisionService.java`** - Lines 294-343

### Key Changes

#### 1. Check for Same Revision Redeployment (Lines 294-297)
```java
if (envStatus.getStatus() == RevisionState.DEPLOYED && !request.isForce()) {
    throw new BusinessException("This revision is already deployed...");
}
```

#### 2. Check for Other Deployed Revisions (Lines 299-343)
```java
List<ApiRevision> allRevisions = apiRevisionRepository.findByOrgIdAndNameOrderByRevisionNumberDesc(...);
for (ApiRevision otherRevision : allRevisions) {
    if (!otherRevision.getId().equals(revisionId)) {
        EnvironmentDeploymentStatus otherEnvStatus = otherRevision.getEnvironments().get(envId);
        if (otherEnvStatus != null && otherEnvStatus.getStatus() == RevisionState.DEPLOYED) {
            // Conflict detected!
            
            if (!request.isForce()) {
                // force=false: Throw error, user must manually undeploy
                throw new BusinessException("Another revision (Rev X) is already deployed...");
            }
            
            // force=true: Auto-undeploy other revision
            // 1. Delete from APISIX
            apisixIntegrationService.undeployServiceAndRoutes(...);
            
            // 2. Update environment status flag
            otherEnvStatus.setStatus(RevisionState.UNDEPLOYED);
            otherEnvStatus.setLastUndeployedAt(LocalDateTime.now());
            
            // 3. Update parent revision state
            otherRevision.calculateState();
            apiRevisionRepository.save(otherRevision);
        }
    }
}
```

#### 3. Deploy New Revision (Lines 345-357)
```java
// Deploy to APISIX
apisixIntegrationService.deployServiceAndRoutes(environment, revision, upstream);

// Update environment status to DEPLOYED
envStatus.setStatus(RevisionState.DEPLOYED);
envStatus.setLastDeployedAt(LocalDateTime.now());
```

#### 4. Update Parent State (Lines 360-365)
```java
// Recalculate overall revision state
revision.calculateState();

ApiRevision savedRevision = apiRevisionRepository.save(revision);
```

---

## Flow Diagrams

### Deployment Flow with force=false

```
┌─────────────────────────────────────────┐
│ Deploy Rev 2 to "dev" (force=false)     │
└─────────────────┬───────────────────────┘
                  │
                  ▼
         ┌────────────────┐
         │ Is Rev 2       │
         │ already        │───YES──▶ ❌ Error
         │ deployed?      │
         └────────┬───────┘
                  │ NO
                  ▼
         ┌────────────────┐
         │ Is another     │
         │ revision       │───YES──▶ ❌ Error: "Please undeploy first"
         │ deployed?      │
         └────────┬───────┘
                  │ NO
                  ▼
         ┌────────────────┐
         │ Deploy Rev 2   │
         │ to APISIX      │
         └────────┬───────┘
                  │
                  ▼
         ┌────────────────┐
         │ Update status  │
         │ to DEPLOYED    │
         └────────┬───────┘
                  │
                  ▼
              ✅ Success
```

### Deployment Flow with force=true

```
┌─────────────────────────────────────────┐
│ Deploy Rev 2 to "dev" (force=true)      │
└─────────────────┬───────────────────────┘
                  │
                  ▼
         ┌────────────────┐
         │ Is Rev 2       │
         │ already        │───YES──▶ Redeploy (update APISIX)
         │ deployed?      │
         └────────┬───────┘
                  │ NO
                  ▼
         ┌────────────────┐
         │ Is another     │───NO───▶ (Skip to deploy)
         │ revision       │
         │ deployed?      │
         └────────┬───────┘
                  │ YES (e.g., Rev 1)
                  ▼
         ┌────────────────┐
         │ Auto-undeploy  │
         │ Rev 1 from     │
         │ APISIX         │
         └────────┬───────┘
                  │
                  ▼
         ┌────────────────┐
         │ Update Rev 1   │
         │ status to      │
         │ UNDEPLOYED     │
         └────────┬───────┘
                  │
                  ▼
         ┌────────────────┐
         │ Recalculate    │
         │ Rev 1 parent   │
         │ state          │
         └────────┬───────┘
                  │
                  ▼
         ┌────────────────┐
         │ Deploy Rev 2   │
         │ to APISIX      │
         └────────┬───────┘
                  │
                  ▼
         ┌────────────────┐
         │ Update Rev 2   │
         │ status to      │
         │ DEPLOYED       │
         └────────┬───────┘
                  │
                  ▼
         ┌────────────────┐
         │ Recalculate    │
         │ Rev 2 parent   │
         │ state          │
         └────────┬───────┘
                  │
                  ▼
              ✅ Success
```

---

## Status Updates

### Environment Status Map Updates

**During Auto-Undeploy (force=true):**
```java
// Other revision's environment status
otherEnvStatus.setStatus(RevisionState.UNDEPLOYED);
otherEnvStatus.setLastUndeployedAt(LocalDateTime.now());
```

**During Deploy:**
```java
// New revision's environment status
envStatus.setStatus(RevisionState.DEPLOYED);
envStatus.setLastDeployedAt(LocalDateTime.now());
```

**During Manual Undeploy:**
```java
// Revision's environment status
envStatus.setStatus(RevisionState.UNDEPLOYED);
envStatus.setLastUndeployedAt(LocalDateTime.now());
```

### Parent Revision State Updates

After every operation, parent state is recalculated:

```java
revision.calculateState();
apiRevisionRepository.save(revision);
```

**Calculation Logic (ApiRevision.java lines 88-113):**
- If any environment is DEPLOYED → Parent state = DEPLOYED
- Else if any environment is UNDEPLOYED → Parent state = UNDEPLOYED
- Else → Parent state = DRAFT

---

## Error Messages

### force = false Errors

**Same revision already deployed:**
```
"This revision is already deployed to environment 'dev'. Use force deploy to redeploy."
```

**Another revision deployed:**
```
"Another revision (Rev 1) is already deployed to environment 'dev'. 
Please undeploy it first or use force deploy to automatically undeploy and deploy."
```

### force = true Errors

**Auto-undeploy failed:**
```
"Failed to auto-undeploy existing revision (Rev 1): [error details]"
```

**Deployment failed:**
```
"Deployment failed for environment 'dev': [error details]"
```

---

## Testing Scenarios

### ✅ Test Case 1: Deploy to Empty Environment
- **force=false**: ✅ Success
- **force=true**: ✅ Success
- **Result**: Same behavior (no conflict)

### ✅ Test Case 2: Redeploy Same Revision
- **force=false**: ❌ Error
- **force=true**: ✅ Success (redeploys)

### ✅ Test Case 3: Deploy Different Revision
- **force=false**: ❌ Error (manual undeploy required)
- **force=true**: ✅ Success (auto-undeploys other revision)

### ✅ Test Case 4: Multi-Environment Deployment
- **force=false**: ❌ Error if any environment has conflict
- **force=true**: ✅ Success (auto-undeploys conflicts)

### ✅ Test Case 5: Rollback Scenario
- **force=false**: Requires 2 steps (undeploy + deploy)
- **force=true**: Single step (auto-undeploy + deploy)

---

## Benefits of This Design

### 1. **Flexibility**
- Users can choose explicit control (force=false) or automation (force=true)
- Supports different deployment strategies for different environments

### 2. **Safety**
- force=false prevents accidental overwrites
- Requires explicit acknowledgment of what's being replaced

### 3. **Convenience**
- force=true enables quick rollbacks and automated deployments
- Single-step operation for revision switching

### 4. **Consistency**
- Single active revision rule is always enforced
- No ambiguity about which revision is active

### 5. **Auditability**
- All status changes are timestamped
- Clear logging of auto-undeploy operations
- Full history of deployments and undeployments

---

## Use Case Recommendations

| Scenario | Recommended Force Flag |
|----------|----------------------|
| Production deployment (planned) | force=false |
| Production rollback (emergency) | force=true |
| Development environment | force=true |
| Staging environment | Either (depends on process) |
| CI/CD pipeline | force=true |
| Manual deployment | force=false |
| A/B testing | force=true |
| Compliance-required deployments | force=false |

---

## Documentation Created

1. **DEPLOYMENT_FLOW.md** - Complete deployment/undeployment flow
2. **DEPLOYMENT_VERIFICATION.md** - Verification checklist with test cases
3. **FORCE_FLAG_GUIDE.md** - Comprehensive guide on force flag usage
4. **IMPLEMENTATION_SUMMARY.md** - This document

---

## Code Quality

✅ **Transaction Safety**: All operations are `@Transactional`
✅ **Error Handling**: Comprehensive try-catch blocks
✅ **Logging**: Detailed logging at each step
✅ **Null Safety**: Proper null checks
✅ **Resource Cleanup**: APISIX resources properly deleted
✅ **State Consistency**: Parent state always recalculated
✅ **No Linter Errors**: Code passes all linter checks

---

## Summary

The implementation correctly enforces the **single active revision policy** with two deployment modes:

- **force=false**: User has full control, must manually undeploy before deploying
- **force=true**: Backend automates the undeploy-deploy sequence

**This design provides the best of both worlds: safety through explicit control AND convenience through automation.**

The logic is sound, well-documented, and production-ready! ✅

