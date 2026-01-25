# Force Flag Deployment Guide

## Overview

The `force` flag in deployment requests controls how the system handles conflicts when deploying API revisions.

## Rule: Only One Active Revision Per Environment

**At any time, only ONE revision of an API can be DEPLOYED to a given environment.**

This rule is **always enforced**, regardless of the force flag value.

## Force Flag Behavior

### force = false (Default, Explicit Mode)

**Philosophy**: User has full control and must explicitly manage deployments.

#### Behavior

| Scenario | Action | Result |
|----------|--------|--------|
| No other revision deployed | ✅ Deploy | Success |
| Same revision already deployed | ❌ Error | "This revision is already deployed..." |
| Another revision deployed | ❌ Error | "Another revision (Rev X) is already deployed. Please undeploy it first or use force deploy." |

#### Use Cases
- **Production deployments** where you want explicit control
- **Staged deployments** where you want to verify undeployment first
- **Cautious deployments** where you want to be aware of what's being replaced
- **Compliance scenarios** where explicit actions are required

#### Example Workflow
```bash
# Step 1: Check what's deployed
GET /api/orgs/{orgId}/apis/{apiName}/revisions

# Step 2: Manually undeploy old revision
POST /api/revisions/{oldRevisionId}/undeploy
{
  "environmentIds": ["prod"]
}

# Step 3: Deploy new revision
POST /api/revisions/{newRevisionId}/deploy
{
  "environmentIds": ["prod"],
  "force": false
}
```

---

### force = true (Automatic Mode)

**Philosophy**: Backend handles the undeploy-deploy sequence automatically.

#### Behavior

| Scenario | Action | Result |
|----------|--------|--------|
| No other revision deployed | ✅ Deploy | Success |
| Same revision already deployed | ✅ Redeploy | Success (updates APISIX) |
| Another revision deployed | ✅ Auto-undeploy then deploy | Success (automatic switch) |

#### Use Cases
- **Quick rollbacks** when you need to switch revisions fast
- **Development environments** where speed matters
- **Automated deployments** via CI/CD pipelines
- **Revision switching** without manual steps

#### Example Workflow
```bash
# Single step: Deploy new revision (backend handles everything)
POST /api/revisions/{newRevisionId}/deploy
{
  "environmentIds": ["prod"],
  "force": true
}

# Backend automatically:
# 1. Finds Rev 1 is deployed to prod
# 2. Undeploys Rev 1 from APISIX
# 3. Updates Rev 1 status to UNDEPLOYED
# 4. Deploys new revision to APISIX
# 5. Updates new revision status to DEPLOYED
```

---

## Comparison Table

| Aspect | force = false | force = true |
|--------|---------------|--------------|
| **Control** | User has full control | Backend automates |
| **Steps** | 2 steps (undeploy + deploy) | 1 step (deploy) |
| **Safety** | More explicit, safer | More convenient |
| **Error on conflict** | Yes, user must resolve | No, auto-resolves |
| **Redeployment** | Not allowed | Allowed |
| **Best for** | Production, compliance | Dev, CI/CD, rollbacks |

---

## Detailed Scenarios

### Scenario 1: First Deployment (No Conflict)

```
State: No revision deployed to "dev"
Action: Deploy Rev 1 to "dev"
```

| force = false | force = true |
|---------------|--------------|
| ✅ Success | ✅ Success |
| Rev 1 → DEPLOYED | Rev 1 → DEPLOYED |

**Outcome**: Same for both - no conflict exists.

---

### Scenario 2: Redeploying Same Revision

```
State: Rev 1 already DEPLOYED to "dev"
Action: Deploy Rev 1 to "dev" again
```

| force = false | force = true |
|---------------|--------------|
| ❌ Error | ✅ Success |
| "This revision is already deployed..." | Rev 1 redeployed (APISIX updated) |
| No changes | Timestamps updated |

**Use case for force=true**: Reapply configuration, update APISIX state.

---

### Scenario 3: Deploying Different Revision (Conflict)

```
State: Rev 1 DEPLOYED to "dev"
Action: Deploy Rev 2 to "dev"
```

| force = false | force = true |
|---------------|--------------|
| ❌ Error | ✅ Success |
| "Another revision (Rev 1) is already deployed..." | 1. Auto-undeploy Rev 1 |
| User must manually undeploy Rev 1 first | 2. Deploy Rev 2 |
| | Rev 1 → UNDEPLOYED |
| | Rev 2 → DEPLOYED |

**Key difference**: force=true automates the undeploy-deploy sequence.

---

### Scenario 4: Multi-Environment Deployment

```
State: 
  - Rev 1 DEPLOYED to "dev"
  - No revision in "staging"
  - No revision in "prod"
Action: Deploy Rev 2 to ["dev", "staging", "prod"]
```

#### With force = false
```
❌ Error when processing "dev"
"Another revision (Rev 1) is already deployed to environment 'dev'..."
Operation aborted
No changes to any environment
```

#### With force = true
```
✅ Success
For "dev":    Auto-undeploy Rev 1, deploy Rev 2
For "staging": Deploy Rev 2 (no conflict)
For "prod":   Deploy Rev 2 (no conflict)

Final state:
- Rev 1: UNDEPLOYED
- Rev 2: DEPLOYED to dev, staging, prod
```

---

### Scenario 5: Rollback to Previous Revision

```
State: Rev 3 DEPLOYED to "prod" (has bugs)
Goal: Rollback to Rev 2
Action: Deploy Rev 2 to "prod"
```

#### With force = false (2 steps required)
```bash
# Step 1: Undeploy current version
POST /api/revisions/{rev3Id}/undeploy
{
  "environmentIds": ["prod"]
}

# Step 2: Deploy previous version
POST /api/revisions/{rev2Id}/deploy
{
  "environmentIds": ["prod"],
  "force": false
}
```

#### With force = true (1 step)
```bash
# Single step rollback
POST /api/revisions/{rev2Id}/deploy
{
  "environmentIds": ["prod"],
  "force": true
}

# Backend automatically:
# - Undeploys Rev 3
# - Deploys Rev 2
```

**Recommendation**: Use force=true for quick rollbacks in incidents.

---

## Best Practices

### When to Use force = false

✅ **Production deployments**
- You want to verify what's being replaced
- You need explicit approval for each step
- Compliance requires separate undeploy/deploy actions

✅ **Staged rollouts**
- Undeploy from dev, verify
- Deploy to dev, test
- Repeat for staging, prod

✅ **Learning/Training**
- Understand the deployment process
- See each step explicitly

### When to Use force = true

✅ **Emergency rollbacks**
- Quick switch to previous stable revision
- Minimize downtime during incidents

✅ **Development environments**
- Rapid iteration and testing
- Convenience over explicit control

✅ **CI/CD pipelines**
- Automated deployments
- Single-step operations
- Idempotent deployments

✅ **Revision switching**
- Testing different revisions
- A/B testing scenarios
- Feature flag implementations

---

## API Request Examples

### Deploy with force = false
```json
POST /api/revisions/{revisionId}/deploy
{
  "environmentIds": ["dev", "staging"],
  "force": false,
  "environmentUpstreams": {
    "dev": "upstream-dev-123",
    "staging": "upstream-staging-456"
  }
}
```

### Deploy with force = true
```json
POST /api/revisions/{revisionId}/deploy
{
  "environmentIds": ["dev", "staging"],
  "force": true,
  "environmentUpstreams": {
    "dev": "upstream-dev-123",
    "staging": "upstream-staging-456"
  }
}
```

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
"Failed to auto-undeploy existing revision (Rev 1): [APISIX error details]"
```

**Deployment failed:**
```
"Deployment failed for environment 'dev': [APISIX error details]"
```

---

## Summary

| Question | Answer |
|----------|--------|
| **Can multiple revisions be active?** | No, only 1 revision per environment |
| **Is this enforced?** | Yes, always |
| **What does force=false do?** | Requires manual undeploy before deploy |
| **What does force=true do?** | Backend auto-undeploys then deploys |
| **Which is safer?** | force=false (more explicit) |
| **Which is faster?** | force=true (one step) |
| **Default value?** | force=false |
| **Best for production?** | Depends on your process (both valid) |
| **Best for dev?** | force=true (convenience) |
| **Best for rollbacks?** | force=true (speed) |

---

## Decision Tree

```
Are you deploying to an environment?
│
├─ Is another revision already deployed?
│  │
│  ├─ NO → Deploy succeeds (force flag doesn't matter)
│  │
│  └─ YES → Is it the same revision you're deploying?
│     │
│     ├─ YES (redeploying same revision)
│     │  ├─ force=false → ❌ Error
│     │  └─ force=true  → ✅ Redeploy
│     │
│     └─ NO (different revision)
│        ├─ force=false → ❌ Error (manual undeploy required)
│        └─ force=true  → ✅ Auto-undeploy then deploy
```

---

## Conclusion

The force flag gives you **control over the deployment strategy**:

- **force=false**: Explicit, step-by-step, user-controlled
- **force=true**: Automatic, one-step, backend-controlled

Both modes enforce the **single active revision rule**. Choose based on your:
- Environment (dev vs prod)
- Process (manual vs automated)
- Urgency (planned vs emergency)
- Compliance requirements

**The logic is sound and provides flexibility for different deployment scenarios!** ✅

