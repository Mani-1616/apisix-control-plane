# Deployment UI - Quick Reference Guide

## Upstream Configuration Display

### Case 1: No Upstream Configured Yet (First Time)

```
╔═══════════════════════════════════════════════╗
║ Prod                          📝 Draft        ║
╠═══════════════════════════════════════════════╣
║ Upstream:                                     ║
║ ┌─────────────────────────────────────────┐   ║
║ │ Select Upstream                       ▼│   ║
║ │   - Dummy (https://prod-dummy.com)     │   ║
║ │   - TODO (https://prod-upstream.free..│   ║
║ └─────────────────────────────────────────┘   ║
║                                               ║
║ ☐ Force deploy (undeploy other revisions)    ║
║ ┌─────────────────────────────────────────┐   ║
║ │           🚀 Deploy                     │   ║
║ └─────────────────────────────────────────┘   ║
╚═══════════════════════════════════════════════╝
```
**User Action**: Select upstream from dropdown, then deploy

---

### Case 2: Upstream Already Configured (DRAFT State)

```
╔═══════════════════════════════════════════════╗
║ Prod                          📝 Draft        ║
╠═══════════════════════════════════════════════╣
║ Upstream:                                     ║
║ ╔═══════════════════════════════════════════╗ ║
║ ║ Dummy                                     ║ ║
║ ║ https://prod-dummy.com                    ║ ║
║ ║                         [ 🔄 Change ]     ║ ║
║ ╚═══════════════════════════════════════════╝ ║
║    ↑                                          ║
║    └─ Green border = Configured upstream      ║
║                                               ║
║ ☐ Force deploy (undeploy other revisions)    ║
║ ┌─────────────────────────────────────────┐   ║
║ │           🚀 Deploy                     │   ║
║ └─────────────────────────────────────────┘   ║
╚═══════════════════════════════════════════════╝
```
**User Action**: 
- Deploy with existing upstream, OR
- Click "Change" to select different upstream

---

### Case 3: Change Mode (After Clicking "Change")

```
╔═══════════════════════════════════════════════╗
║ Prod                          📝 Draft        ║
╠═══════════════════════════════════════════════╣
║ Upstream:                                     ║
║ ┌─────────────────────────────────────────┐   ║
║ │ Dummy (https://prod-dummy.com)        ▼│   ║ ← Currently selected
║ │   - TODO (https://prod-upstream.free.. │   ║
║ └─────────────────────────────────────────┘   ║
║ ┌─────────────────────────────────────────┐   ║
║ │              Cancel                     │   ║
║ └─────────────────────────────────────────┘   ║
║                                               ║
║ ☐ Force deploy (undeploy other revisions)    ║
║ ┌─────────────────────────────────────────┐   ║
║ │           🚀 Deploy                     │   ║
║ └─────────────────────────────────────────┘   ║
╚═══════════════════════════════════════════════╝
```
**User Action**: 
- Select different upstream, then deploy, OR
- Click "Cancel" to go back to display mode

---

### Case 4: Deployed State (Locked)

```
╔═══════════════════════════════════════════════╗
║ Prod                          ✅ Deployed     ║
╠═══════════════════════════════════════════════╣
║ Upstream:                                     ║
║ ╔═══════════════════════════════════════════╗ ║
║ ║ Dummy                                     ║ ║
║ ║ https://prod-dummy.com                    ║ ║
║ ║                  (Locked - Deployed)      ║ ║
║ ╚═══════════════════════════════════════════╝ ║
║                                               ║
║ Last deployed: 1/22/2026, 2:30:00 PM         ║
║                                               ║
║ ┌─────────────────────────────────────────┐   ║
║ │          ⏹️ Undeploy                     │   ║
║ └─────────────────────────────────────────┘   ║
╚═══════════════════════════════════════════════╝
```
**User Action**: 
- Must undeploy first to change anything
- No "Change" button available
- No force deploy checkbox

---

### Case 5: Undeployed State (Locked)

```
╔═══════════════════════════════════════════════╗
║ QA                            ⏹️ Undeployed   ║
╠═══════════════════════════════════════════════╣
║ Upstream:                                     ║
║ ╔═══════════════════════════════════════════╗ ║
║ ║ Dummy                                     ║ ║
║ ║ https://qa-dummy.com                      ║ ║
║ ║                (Locked - Undeployed)      ║ ║
║ ╚═══════════════════════════════════════════╝ ║
║                                               ║
║ Last deployed: 1/22/2026, 1:00:00 PM         ║
║                                               ║
║ ☐ Force deploy (undeploy other revisions)    ║
║ ┌─────────────────────────────────────────┐   ║
║ │           🚀 Deploy                     │   ║
║ └─────────────────────────────────────────┘   ║
╚═══════════════════════════════════════════════╝
```
**User Action**: 
- Can redeploy with same upstream
- Cannot change upstream (locked)
- Upstream configuration persists from previous deployment

---

## Status-Based Behavior Matrix

| Environment Status | Upstream Display | Can Change? | Deploy Action |
|-------------------|------------------|-------------|---------------|
| **DRAFT** (No upstream) | Dropdown | ✅ Yes | Select & Deploy |
| **DRAFT** (Has upstream) | Configured (green box) | ✅ Yes (via "Change") | Deploy or Change & Deploy |
| **DEPLOYED** | Configured (locked) | ❌ No | Must Undeploy first |
| **UNDEPLOYED** | Configured (locked) | ❌ No | Can redeploy |

## User Workflows

### Workflow 1: First Deployment
```
1. Select Organization → API → Revision
2. See environment card with dropdown
3. Select upstream from dropdown
4. Optionally check "Force deploy"
5. Click "Deploy"
```

### Workflow 2: Deploy Different Revision (force=false)
```
1. Select Organization → API → Different Revision
2. See environment shows another revision is deployed
3. Cannot deploy (error expected)
4. Must manually undeploy other revision first
5. Then deploy new revision
```

### Workflow 3: Deploy Different Revision (force=true)
```
1. Select Organization → API → Different Revision
2. See environment shows configured upstream
3. Check "Force deploy" checkbox
4. Click "Deploy"
5. Backend automatically undeploys other revision
6. Then deploys new revision
```

### Workflow 4: Change Upstream Configuration
```
1. Select a revision in DRAFT state
2. See configured upstream with "Change" button
3. Click "🔄 Change"
4. Dropdown appears with all upstreams
5. Select different upstream
6. Click "Deploy" OR "Cancel" to revert
```

### Workflow 5: Undeploy and Redeploy
```
1. See deployed revision
2. Click "Undeploy"
3. Status changes to UNDEPLOYED
4. Upstream still configured (locked)
5. Click "Deploy" to redeploy with same upstream
```

## Key Visual Indicators

### Green Border
```
╔═══════════════════════════════════╗
║ Upstream Name                     ║
║ https://target-url.com            ║
╚═══════════════════════════════════╝
```
**Meaning**: This upstream is currently configured for this environment

### Status Badges
- 📝 Draft - Can deploy, can change upstream
- ✅ Deployed - Active, cannot change
- ⏹️ Undeployed - Was deployed, can redeploy

### Lock Indicators
- `(Locked - Deployed)` - Cannot change because currently active
- `(Locked - Undeployed)` - Cannot change because configuration persists

## Benefits Summary

### Before
❌ Always showed dropdown with all upstreams
❌ Not clear which upstream is configured
❌ Could accidentally change upstream without realizing
❌ Same UI for "new" and "existing" configuration

### After
✅ Shows configured upstream prominently
✅ Clear visual indicator (green border)
✅ Explicit "Change" action required to modify
✅ Different UI for "configured" vs "not configured"
✅ Status-based locking (can't change when deployed)

## Tips

💡 **Green box = Already configured** - You can deploy as-is or change it

💡 **"Change" button** - Only visible in DRAFT state, click to modify upstream

💡 **"Cancel" button** - Reverts back to configured upstream without deploying

💡 **Force deploy** - Automatically undeploys other revisions before deploying

💡 **Locked upstream** - Must undeploy first to change configuration

## Common Questions

**Q: Why can't I change the upstream?**
A: Upstream can only be changed when the environment status is DRAFT. If it's DEPLOYED or UNDEPLOYED, you need to undeploy first.

**Q: What if I want to deploy with a different upstream?**
A: If status is DRAFT, click the "🔄 Change" button, select a new upstream, then deploy.

**Q: Will changing upstream affect other revisions?**
A: No. Each revision has its own upstream configuration per environment. Changing it here only affects this revision.

**Q: What happens to the upstream when I undeploy?**
A: The upstream configuration persists. When you undeploy, the service is removed from APISIX, but the configuration remains in the control plane database.

**Q: Can I deploy to multiple environments with different upstreams?**
A: Yes! Each environment has its own upstream configuration. Select/change upstream for each environment independently.

**Q: What does the green border mean?**
A: The green border indicates that an upstream is already configured for this environment. You can deploy with it as-is or click "Change" to select a different one.

