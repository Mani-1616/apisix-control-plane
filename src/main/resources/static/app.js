// Global state
let selectedOrgId = null;
let selectedEnvId = null;
let environments = [];
let upstreams = [];

// API Base URL
const API_BASE = '/api/v1';

// ===== TAB MANAGEMENT =====
function showTab(tabName, targetButton) {
    document.querySelectorAll('.tab-content').forEach(tab => tab.classList.remove('active'));
    document.querySelectorAll('.tab-button').forEach(btn => btn.classList.remove('active'));
    
    document.getElementById(tabName).classList.add('active');
    
    // If called from onclick, targetButton will be the event target
    // If called programmatically, find the button by tab name
    if (targetButton) {
        targetButton.classList.add('active');
    } else {
        const button = Array.from(document.querySelectorAll('.tab-button'))
            .find(btn => btn.onclick && btn.onclick.toString().includes(tabName));
        if (button) {
            button.classList.add('active');
        }
    }
    
    // Auto-load data when switching tabs
    if (tabName === 'organizations') loadOrganizations();
    if (tabName === 'environments') loadEnvironments();
    if (tabName === 'upstreams') loadUpstreams();
    if (tabName === 'apis') loadApis();
}

// ===== ORGANIZATIONS =====
    document.getElementById('createOrgForm').addEventListener('submit', async (e) => {
        e.preventDefault();
    const name = document.getElementById('orgName').value;
    const description = document.getElementById('orgDescription').value;
        
        try {
            const response = await fetch(`${API_BASE}/organizations`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name, description })
            });
            
            if (response.ok) {
            alert('Organization created successfully!');
                e.target.reset();
                loadOrganizations();
            } else {
            const error = await response.text();
            alert('Failed to create organization: ' + error);
        }
    } catch (error) {
        alert('Error: ' + error.message);
    }
});

async function loadOrganizations() {
    try {
        const response = await fetch(`${API_BASE}/organizations`);
        const orgs = await response.json();
        
        displayOrganizations(orgs);
        updateOrgDropdowns(orgs);
    } catch (error) {
        console.error('Error loading organizations:', error);
    }
}

function displayOrganizations(orgs) {
    const list = document.getElementById('orgsList');
    list.innerHTML = orgs.map(org => `
        <div class="card">
            <h3>${org.name}</h3>
            <p>${org.description || 'No description'}</p>
            <small>ID: ${org.id}</small>
        </div>
    `).join('');
}

function updateOrgDropdowns(orgs) {
    const dropdowns = ['envOrgId', 'envOrgFilter', 'upstreamOrgId', 'upstreamOrgFilter', 
                       'apiOrgId', 'apiOrgFilter', 'deployOrgId'];
    
    dropdowns.forEach(id => {
        const select = document.getElementById(id);
            const currentValue = select.value;
            select.innerHTML = '<option value="">Select Organization</option>' +
            orgs.map(org => `<option value="${org.id}">${org.name}</option>`).join('');
            if (currentValue) select.value = currentValue;
    });
}

// ===== ENVIRONMENTS =====
document.getElementById('createEnvForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const orgId = document.getElementById('envOrgId').value;
    const name = document.getElementById('envName').value;
    const description = document.getElementById('envDescription').value;
    const apisixAdminUrl = document.getElementById('envApisixUrl').value;
    const active = document.getElementById('envActive').checked;
    
    try {
        const response = await fetch(`${API_BASE}/organizations/${orgId}/environments`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name, description, apisixAdminUrl, active })
        });
        
        if (response.ok) {
            alert('Environment created successfully!');
            e.target.reset();
            loadEnvironments();
        } else {
            const error = await response.text();
            alert('Failed to create environment: ' + error);
        }
    } catch (error) {
        alert('Error: ' + error.message);
    }
});

async function loadEnvironments() {
    const orgId = document.getElementById('envOrgFilter').value;
    if (!orgId) {
        document.getElementById('envsList').innerHTML = '<p>Please select an organization</p>';
        return;
    }
    
    try {
        const response = await fetch(`${API_BASE}/organizations/${orgId}/environments`);
        environments = await response.json();
        
        displayEnvironments(environments);
    } catch (error) {
        console.error('Error loading environments:', error);
    }
}

function displayEnvironments(envs) {
    const list = document.getElementById('envsList');
    list.innerHTML = envs.map(env => `
        <div class="card">
            <h3>${env.name}</h3>
            <p>${env.description || 'No description'}</p>
            <p><strong>APISIX URL:</strong> ${env.apisixAdminUrl}</p>
            <p><strong>Status:</strong> ${env.active ? '✅ Active' : '❌ Inactive'}</p>
            <small>ID: ${env.id}</small>
        </div>
    `).join('');
}

// ===== UPSTREAMS (Environment-Scoped) =====
async function loadEnvironmentsForUpstream() {
    const orgId = document.getElementById('upstreamOrgId').value;
    if (!orgId) return;
    
    try {
        const response = await fetch(`${API_BASE}/organizations/${orgId}/environments`);
        const envs = await response.json();
        
        const select = document.getElementById('upstreamEnvId');
        select.innerHTML = '<option value="">Select Environment</option>' +
            envs.map(env => `<option value="${env.id}">${env.name}</option>`).join('');
    } catch (error) {
        console.error('Error loading environments:', error);
    }
}

async function loadEnvironmentsForUpstreamFilter() {
    const orgId = document.getElementById('upstreamOrgFilter').value;
    if (!orgId) {
        document.getElementById('upstreamEnvFilter').innerHTML = '<option value="">Select Environment</option>';
        document.getElementById('upstreamsList').innerHTML = '';
        return;
    }
    
    try {
        const response = await fetch(`${API_BASE}/organizations/${orgId}/environments`);
        const envs = await response.json();
        
        const select = document.getElementById('upstreamEnvFilter');
        select.innerHTML = '<option value="">Select Environment</option>' +
            envs.map(env => `<option value="${env.id}">${env.name}</option>`).join('');
    } catch (error) {
        console.error('Error loading environments:', error);
    }
}

document.getElementById('createUpstreamForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const orgId = document.getElementById('upstreamOrgId').value;
    const envId = document.getElementById('upstreamEnvId').value;
    const name = document.getElementById('upstreamName').value;
    const description = document.getElementById('upstreamDescription').value;
    const targetUrl = document.getElementById('upstreamTargetUrl').value;
    const type = document.getElementById('upstreamType').value;
    
    try {
        const response = await fetch(`${API_BASE}/organizations/${orgId}/environments/${envId}/upstreams`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name, description, targetUrl, type })
        });
        
        if (response.ok) {
            alert('Upstream created successfully in APISIX!');
            e.target.reset();
            loadUpstreams();
        } else {
            const error = await response.text();
            alert('Failed to create upstream: ' + error);
        }
    } catch (error) {
        alert('Error: ' + error.message);
    }
});

async function loadUpstreams() {
    const orgId = document.getElementById('upstreamOrgFilter').value;
    const envId = document.getElementById('upstreamEnvFilter').value;
    
    if (!orgId || !envId) {
        document.getElementById('upstreamsList').innerHTML = '<p>Please select organization and environment</p>';
        return;
    }
    
    try {
        const response = await fetch(`${API_BASE}/organizations/${orgId}/environments/${envId}/upstreams`);
        upstreams = await response.json();
        
        displayUpstreams(upstreams, orgId, envId);
    } catch (error) {
        console.error('Error loading upstreams:', error);
    }
}

function displayUpstreams(upstreams, orgId, envId) {
    const list = document.getElementById('upstreamsList');
    list.innerHTML = upstreams.map(upstream => `
        <div class="card">
            <h3>${upstream.name}</h3>
            <p><strong>Target URL:</strong> ${upstream.targetUrl}</p>
            <p><strong>Type:</strong> ${upstream.type}</p>
            <p><strong>Status:</strong> ${upstream.apisixStatus}</p>
            <p><strong>In Use:</strong> ${upstream.inUse ? 'Yes' : 'No'}</p>
            <small>APISIX ID: ${upstream.apisixId}</small>
            ${!upstream.inUse ? `
                <button onclick="deleteUpstream('${orgId}', '${envId}', '${upstream.id}')">Delete</button>
            ` : ''}
        </div>
    `).join('');
}

async function deleteUpstream(orgId, envId, upstreamId) {
    if (!confirm('Are you sure you want to delete this upstream?')) return;
    
    try {
        const response = await fetch(`${API_BASE}/organizations/${orgId}/environments/${envId}/upstreams/${upstreamId}`, {
            method: 'DELETE'
        });
        
        if (response.ok) {
            alert('Upstream deleted successfully!');
            loadUpstreams();
        } else {
            const error = await response.text();
            alert('Failed to delete upstream: ' + error);
        }
    } catch (error) {
        alert('Error: ' + error.message);
    }
}

// ===== APIs =====
async function loadEnvironmentsForApi() {
    const orgId = document.getElementById('apiOrgId').value;
    if (!orgId) {
        document.getElementById('environmentUpstreamsContainer').innerHTML = '';
        return;
    }
    
    try {
        const response = await fetch(`${API_BASE}/organizations/${orgId}/environments`);
        const envs = await response.json();
        
        // Display environment-upstream selection
        const container = document.getElementById('environmentUpstreamsContainer');
        container.innerHTML = envs.map(env => `
            <div class="env-upstream-row">
                <label>${env.name}:</label>
                <select id="upstream_${env.id}" data-env-id="${env.id}">
                    <option value="">No upstream (set later)</option>
                </select>
            </div>
        `).join('');
        
        // Load upstreams for each environment
        for (const env of envs) {
            const upstreamsResponse = await fetch(`${API_BASE}/organizations/${orgId}/environments/${env.id}/upstreams`);
            const upstreams = await upstreamsResponse.json();
            
            const select = document.getElementById(`upstream_${env.id}`);
            upstreams.forEach(upstream => {
                const option = document.createElement('option');
                option.value = upstream.id;
                option.textContent = `${upstream.name} (${upstream.targetUrl})`;
                select.appendChild(option);
            });
        }
    } catch (error) {
        console.error('Error loading environments for API:', error);
    }
}

let routeCount = 0;

function addRoute() {
    routeCount++;
    const container = document.getElementById('routesContainer');
    const routeDiv = document.createElement('div');
    routeDiv.className = 'route-item';
    routeDiv.innerHTML = `
        <h4>Route ${routeCount}</h4>
        <input type="text" placeholder="Route Name" data-route-name required>
        <input type="text" placeholder="Methods (comma-separated, e.g., GET,POST)" data-route-methods required>
        <input type="text" placeholder="URIs (comma-separated, e.g., /api/users)" data-route-uris required>
        <button type="button" onclick="this.parentElement.remove()">Remove Route</button>
    `;
    container.appendChild(routeDiv);
}

document.getElementById('createApiForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    
    const editingRevisionId = document.getElementById('editingRevisionId').value;
    if (editingRevisionId) {
        alert('You are in edit mode. Use "Update Revision" button or cancel to create new API.');
        return;
    }
    
    await createApi();
});

async function createApi() {
    const orgId = document.getElementById('apiOrgId').value;
    const name = document.getElementById('apiName').value;
    const description = document.getElementById('apiDescription').value;
    
    // Collect environment-upstream mappings
    const environmentUpstreams = {};
    const envUpstreamSelects = document.querySelectorAll('#environmentUpstreamsContainer select');
    envUpstreamSelects.forEach(select => {
        const envId = select.dataset.envId;
        const upstreamId = select.value;
        if (upstreamId) {
            environmentUpstreams[envId] = upstreamId;
        }
    });
    
    // Collect routes
    const routes = [];
    document.querySelectorAll('#routesContainer .route-item').forEach(routeDiv => {
        const name = routeDiv.querySelector('[data-route-name]').value;
        const methods = routeDiv.querySelector('[data-route-methods]').value.split(',').map(m => m.trim());
        const uris = routeDiv.querySelector('[data-route-uris]').value.split(',').map(u => u.trim());
        routes.push({ name, methods, uris, plugins: {}, metadata: {} });
    });
    
    if (routes.length === 0) {
        alert('Please add at least one route');
        return;
    }
    
    try {
        const response = await fetch(`${API_BASE}/organizations/${orgId}/apis`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                name,
                description,
                environmentUpstreams,
                serviceConfig: { plugins: {}, metadata: {} },
                routes
            })
        });
        
        if (response.ok) {
            alert('API created successfully!');
            document.getElementById('createApiForm').reset();
            document.getElementById('routesContainer').innerHTML = '';
            routeCount = 0;
            loadApis();
        } else {
            const error = await response.text();
            alert('Failed to create API: ' + error);
        }
    } catch (error) {
        alert('Error: ' + error.message);
    }
}

async function createRevision() {
    const orgId = document.getElementById('apiOrgId').value;
    const apiName = document.getElementById('apiName').value;
    const description = document.getElementById('apiDescription').value;
    
    if (!apiName) {
        alert('Please enter API name');
        return;
    }
    
    // Collect environment-upstream mappings
    const environmentUpstreams = {};
    const envUpstreamSelects = document.querySelectorAll('#environmentUpstreamsContainer select');
    envUpstreamSelects.forEach(select => {
        const envId = select.dataset.envId;
        const upstreamId = select.value;
        if (upstreamId) {
            environmentUpstreams[envId] = upstreamId;
        }
    });
    
    // Collect routes
    const routes = [];
    document.querySelectorAll('#routesContainer .route-item').forEach(routeDiv => {
        const name = routeDiv.querySelector('[data-route-name]').value;
        const methods = routeDiv.querySelector('[data-route-methods]').value.split(',').map(m => m.trim());
        const uris = routeDiv.querySelector('[data-route-uris]').value.split(',').map(u => u.trim());
        routes.push({ name, methods, uris, plugins: {}, metadata: {} });
    });
    
    if (routes.length === 0) {
        alert('Please add at least one route');
        return;
    }
    
    try {
        const response = await fetch(`${API_BASE}/organizations/${orgId}/apis/${apiName}/revisions`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                name: apiName, // Include the API name in the request body
                description,
                environmentUpstreams,
                serviceConfig: { plugins: {}, metadata: {} },
                routes
            })
        });
        
        if (response.ok) {
            alert('New revision created successfully!');
            document.getElementById('createApiForm').reset();
            document.getElementById('routesContainer').innerHTML = '';
            routeCount = 0;
            loadApis();
        } else {
            const error = await response.text();
            alert('Failed to create revision: ' + error);
        }
    } catch (error) {
        alert('Error: ' + error.message);
    }
}

async function updateRevision() {
    const revisionId = document.getElementById('editingRevisionId').value;
    if (!revisionId) {
        alert('No revision selected for editing');
        return;
    }
    
    const orgId = document.getElementById('apiOrgId').value;
    const name = document.getElementById('apiName').value; // Get name even though field is disabled
    const description = document.getElementById('apiDescription').value;
    
    // Collect environment-upstream mappings
    const environmentUpstreams = {};
    const envUpstreamSelects = document.querySelectorAll('#environmentUpstreamsContainer select');
    envUpstreamSelects.forEach(select => {
        const envId = select.dataset.envId;
        const upstreamId = select.value;
        if (upstreamId) {
            environmentUpstreams[envId] = upstreamId;
        }
    });
    
    // Collect routes
    const routes = [];
    document.querySelectorAll('#routesContainer .route-item').forEach(routeDiv => {
        const name = routeDiv.querySelector('[data-route-name]').value;
        const methods = routeDiv.querySelector('[data-route-methods]').value.split(',').map(m => m.trim());
        const uris = routeDiv.querySelector('[data-route-uris]').value.split(',').map(u => u.trim());
        routes.push({ name, methods, uris, plugins: {}, metadata: {} });
    });
    
    if (routes.length === 0) {
        alert('Please add at least one route');
        return;
    }
    
    try {
        const response = await fetch(`${API_BASE}/organizations/${orgId}/apis/revisions/${revisionId}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                name, // Include the name explicitly
                description,
                environmentUpstreams,
                serviceConfig: { plugins: {}, metadata: {} },
                routes
            })
        });
        
        if (response.ok) {
            alert('Revision updated successfully!');
            cancelEdit();
            loadApis();
        } else {
            const error = await response.text();
            alert('Failed to update revision: ' + error);
        }
    } catch (error) {
        alert('Error: ' + error.message);
    }
}

function cancelEdit() {
    document.getElementById('editingRevisionId').value = '';
    document.getElementById('createApiForm').reset();
    document.getElementById('routesContainer').innerHTML = '';
    document.getElementById('apiName').disabled = false;
    document.getElementById('createButtons').style.display = 'flex';
    document.getElementById('editButtons').style.display = 'none';
    document.getElementById('apiFormTitle').textContent = 'Create API / Revision';
    routeCount = 0;
}

async function loadApis() {
    const orgId = document.getElementById('apiOrgFilter').value;
    if (!orgId) {
        document.getElementById('apisList').innerHTML = '<p>Please select an organization</p>';
        return;
    }
    
    try {
        const response = await fetch(`${API_BASE}/organizations/${orgId}/apis`);
        const data = await response.json();
        
        // Backend returns { totalApis, totalRevisions, apis: { apiName: [revisions] } }
        // Flatten to array of all revisions
        const allRevisions = [];
        if (data.apis) {
            Object.values(data.apis).forEach(revisions => {
                allRevisions.push(...revisions);
            });
        }
        
        displayApis(allRevisions, orgId);
    } catch (error) {
        console.error('Error loading APIs:', error);
        document.getElementById('apisList').innerHTML = '<p>Error loading APIs. Check console.</p>';
    }
}

function displayApis(allRevisions, orgId) {
    const list = document.getElementById('apisList');
    
    // Group revisions by API name
    const apiGroups = {};
    allRevisions.forEach(revision => {
        if (!apiGroups[revision.name]) {
            apiGroups[revision.name] = [];
        }
        apiGroups[revision.name].push(revision);
    });
    
    // Sort revisions within each group by revision number (descending)
    Object.keys(apiGroups).forEach(apiName => {
        apiGroups[apiName].sort((a, b) => b.revisionNumber - a.revisionNumber);
    });
    
    if (Object.keys(apiGroups).length === 0) {
        list.innerHTML = '<p>No APIs found. Create your first API above!</p>';
        return;
    }
    
    list.innerHTML = Object.entries(apiGroups).map(([apiName, revisions]) => {
        const revisionsHtml = revisions.map(revision => {
            const envStatus = Object.entries(revision.environments || {}).map(([envId, status]) => 
                `<span class="env-badge env-${status.status.toLowerCase()}">${envId.substring(0, 8)}: ${status.status}</span>`
            ).join(' ');
            
            const stateClass = revision.state === 'DRAFT' ? 'state-draft' : 
                              revision.state === 'DEPLOYED' ? 'state-deployed' : 'state-undeployed';
            
            return `
                <div class="revision-item">
                    <div class="revision-info">
                        <div class="revision-number">Rev ${revision.revisionNumber}</div>
                        <div class="revision-details">
                            <span class="state-badge ${stateClass}">${revision.state}</span>
                            <div class="env-badges">${envStatus || '<span class="env-badge env-none">Not configured</span>'}</div>
                            ${revision.description ? `<p class="revision-description">${revision.description}</p>` : ''}
                        </div>
                    </div>
                    ${revision.state === 'DRAFT' ? `
                        <div class="revision-actions">
                            <button class="action-btn edit-btn" onclick="editRevision('${orgId}', '${revision.id}')" title="Edit this DRAFT revision">✏️ Edit</button>
                            <button class="action-btn delete-btn" onclick="deleteRevision('${orgId}', '${revision.id}')" title="Delete this DRAFT revision">🗑️ Delete</button>
                        </div>
                    ` : ''}
                </div>
            `;
        }).join('');
        
        return `
            <div class="api-group">
                <div class="api-header">
                    <h3>${apiName}</h3>
                    <span class="revision-count">${revisions.length} revision${revisions.length > 1 ? 's' : ''}</span>
                </div>
                <div class="revisions-list">
                    ${revisionsHtml}
                </div>
            </div>
        `;
    }).join('');
}

async function editRevision(orgId, revisionId) {
    try {
        const response = await fetch(`${API_BASE}/organizations/${orgId}/apis/revisions/${revisionId}`);
        
        if (!response.ok) {
            const errorText = await response.text();
            throw new Error(`Failed to load revision: ${response.status} - ${errorText}`);
        }
        
        const revision = await response.json();
        
        // Switch to APIs tab programmatically
        showTab('apis');
        
        // Populate form
        document.getElementById('editingRevisionId').value = revisionId;
        document.getElementById('apiOrgId').value = orgId;
        await loadEnvironmentsForApi();
        
        document.getElementById('apiName').value = revision.name;
        document.getElementById('apiName').disabled = true;
        document.getElementById('apiDescription').value = revision.description || '';
        
        // Populate environment upstreams
        if (revision.environments) {
            Object.entries(revision.environments).forEach(([envId, status]) => {
                const select = document.getElementById(`upstream_${envId}`);
                if (select && status.upstreamId) {
                    select.value = status.upstreamId;
                }
            });
        }
        
        // Populate routes
    document.getElementById('routesContainer').innerHTML = '';
        routeCount = 0;
        if (revision.routes && revision.routes.length > 0) {
            revision.routes.forEach(route => {
                addRoute();
                const routeDiv = document.querySelector('#routesContainer .route-item:last-child');
                if (routeDiv) {
                    routeDiv.querySelector('[data-route-name]').value = route.name;
                    routeDiv.querySelector('[data-route-methods]').value = route.methods.join(', ');
                    routeDiv.querySelector('[data-route-uris]').value = route.uris.join(', ');
                }
            });
        }
        
        // Switch to edit mode
        document.getElementById('createButtons').style.display = 'none';
        document.getElementById('editButtons').style.display = 'flex';
        document.getElementById('apiFormTitle').textContent = 'Edit DRAFT Revision';
        
    } catch (error) {
        console.error('Error loading revision:', error);
        alert('Error loading revision: ' + error.message);
    }
}

async function deleteRevision(orgId, revisionId) {
    if (!confirm('Are you sure you want to delete this DRAFT revision?')) return;
    
    try {
        const response = await fetch(`${API_BASE}/organizations/${orgId}/apis/revisions/${revisionId}`, {
            method: 'DELETE'
        });
        
        if (response.ok) {
            alert('Revision deleted successfully!');
            loadApis();
        } else {
            const error = await response.text();
            alert('Failed to delete revision: ' + error);
        }
    } catch (error) {
        alert('Error: ' + error.message);
    }
}

// ===== DEPLOYMENT =====
async function loadApisForDeployment() {
    const orgId = document.getElementById('deployOrgId').value;
    if (!orgId) return;
    
    try {
        const response = await fetch(`${API_BASE}/organizations/${orgId}/apis`);
        const data = await response.json();
        
        // Backend returns { totalApis, totalRevisions, apis: { apiName: [revisions] } }
        // Extract unique API names
        const apiNames = data.apis ? Object.keys(data.apis) : [];
        
        const select = document.getElementById('deployApiName');
        select.innerHTML = '<option value="">Select API</option>' +
            apiNames.map(name => `<option value="${name}">${name}</option>`).join('');
    } catch (error) {
        console.error('Error loading APIs:', error);
    }
}

async function loadApiRevisions() {
    const orgId = document.getElementById('deployOrgId').value;
    const apiName = document.getElementById('deployApiName').value;
    
    if (!orgId || !apiName) return;
    
    try {
        // Use the specific endpoint for getting revisions of an API
        const response = await fetch(`${API_BASE}/organizations/${orgId}/apis/${apiName}/revisions`);
        const revisions = await response.json();
        
        const select = document.getElementById('deployRevisionId');
        select.innerHTML = '<option value="">Select Revision</option>' +
            revisions.map(rev => `<option value="${rev.id}">Rev ${rev.revisionNumber}</option>`).join('');
    } catch (error) {
        console.error('Error loading revisions:', error);
    }
}

async function loadDeploymentData(providedOrgId, providedRevisionId) {
    // Use provided params or fall back to form values
    const orgId = providedOrgId || document.getElementById('deployOrgId').value;
    const revisionId = providedRevisionId || document.getElementById('deployRevisionId').value;
    
    if (!orgId || !revisionId) {
        document.getElementById('deploymentEnvironments').innerHTML = '';
        return;
    }
    
    try {
        // Load revision details
        const response = await fetch(`${API_BASE}/organizations/${orgId}/apis/revisions/${revisionId}`);
        const revision = await response.json();
        
        // Load environments
        const envsResponse = await fetch(`${API_BASE}/organizations/${orgId}/environments`);
        const envs = await envsResponse.json();
        
        if (envs.length === 0) {
            document.getElementById('deploymentEnvironments').innerHTML = 
                '<p class="info-text">No environments found. Please create environments first.</p>';
            return;
        }
        
        // Build environment cards
        let html = '<h3 style="margin-top: 20px;">Environments</h3>';
        html += '<div class="deployment-environments-grid">';
        
        for (const env of envs) {
            const envStatus = revision.environments ? revision.environments[env.id] : null;
            const currentStatus = envStatus ? envStatus.status : 'DRAFT';
            const currentUpstreamId = envStatus ? envStatus.upstreamId : null;
            
            // Load upstreams for this environment
            const upstreamsResponse = await fetch(`${API_BASE}/organizations/${orgId}/environments/${env.id}/upstreams`);
            const upstreams = await upstreamsResponse.json();
            
            // Determine status color and icon
            let statusClass = 'status-draft';
            let statusIcon = '📝';
            let statusText = 'Draft';
            if (currentStatus === 'DEPLOYED') {
                statusClass = 'status-deployed';
                statusIcon = '✅';
                statusText = 'Deployed';
            } else if (currentStatus === 'UNDEPLOYED') {
                statusClass = 'status-undeployed';
                statusIcon = '⏹️';
                statusText = 'Undeployed';
            }
            
            // Find the configured upstream details
            const configuredUpstream = currentUpstreamId 
                ? upstreams.find(u => u.id === currentUpstreamId) 
                : null;
            
            html += `
                <div class="deployment-env-card ${statusClass}">
                    <div class="env-card-header">
                        <h4>${env.name}</h4>
                        <span class="env-status-badge ${statusClass}">${statusIcon} ${statusText}</span>
                    </div>
                    
                    <div class="env-card-body">
                        <div class="env-upstream-select">
                            <label>Upstream:</label>
                            
                            ${configuredUpstream ? `
                                <!-- Show configured upstream -->
                                <div id="upstream_display_${env.id}" class="upstream-display">
                                    <div class="upstream-info">
                                        <strong>${configuredUpstream.name}</strong>
                                        <small style="color: var(--text-secondary);">${configuredUpstream.targetUrl}</small>
                                    </div>
                                    ${currentStatus === 'DRAFT' ? `
                                        <button type="button" 
                                                class="btn-change-upstream" 
                                                onclick="showUpstreamDropdown('${env.id}')">
                                            🔄 Change
                                        </button>
                                    ` : `
                                        <small style="color: var(--text-secondary); font-size: 0.85rem;">
                                            <em>(Locked - ${statusText})</em>
                                        </small>
                                    `}
                                </div>
                                
                                <!-- Hidden dropdown for changing upstream -->
                                <div id="upstream_dropdown_${env.id}" class="upstream-dropdown" style="display: none;">
                                    <select id="deploy_upstream_${env.id}">
                                        <option value="">Select Upstream</option>
                                        ${upstreams.map(u => `
                                            <option value="${u.id}" 
                                                    ${currentUpstreamId === u.id ? 'selected' : ''}>
                                                ${u.name} (${u.targetUrl})
                                            </option>
                                        `).join('')}
                                    </select>
                                    <button type="button" 
                                            class="btn-cancel-change" 
                                            onclick="hideUpstreamDropdown('${env.id}')">
                                        Cancel
                                    </button>
                                </div>
                            ` : `
                                <!-- No upstream configured yet - show dropdown -->
                                <select id="deploy_upstream_${env.id}" 
                                        ${currentStatus !== 'DRAFT' ? 'disabled title="Upstream can only be changed when status is DRAFT"' : ''}>
                                    <option value="">Select Upstream</option>
                                    ${upstreams.map(u => `
                                        <option value="${u.id}">
                                            ${u.name} (${u.targetUrl})
                                        </option>
                                    `).join('')}
                                </select>
                                ${currentStatus !== 'DRAFT' ? `
                                    <small style="color: var(--text-secondary); font-size: 0.85rem;">
                                        <em>Upstream locked (status: ${statusText})</em>
                                    </small>
                                ` : ''}
                            `}
                        </div>
                        
                        ${envStatus && envStatus.lastDeployedAt ? `
                            <p class="env-last-action">Last deployed: ${new Date(envStatus.lastDeployedAt).toLocaleString()}</p>
                        ` : ''}
                    </div>
                    
                    <div class="env-card-actions">
                        ${currentStatus === 'DEPLOYED' ? `
                            <button class="btn-undeploy" onclick="undeploySingleEnv('${orgId}', '${revisionId}', '${env.id}', '${env.name}')">
                                ⏹️ Undeploy
                            </button>
                        ` : `
                            <div style="display: flex; flex-direction: column; gap: 8px; width: 100%;">
                                <label style="display: flex; align-items: center; gap: 8px; font-size: 0.9rem; color: var(--text-secondary);">
                                    <input type="checkbox" id="force_deploy_${env.id}" style="cursor: pointer;">
                                    <span>Force deploy (undeploy other revisions)</span>
                                </label>
                                <button class="btn-deploy" 
                                        onclick="deploySingleEnv('${orgId}', '${revisionId}', '${env.id}', '${env.name}')"
                                        ${upstreams.length === 0 ? 'disabled title="No upstreams available in this environment"' : ''}>
                                    🚀 Deploy
                                </button>
                            </div>
                        `}
                    </div>
                </div>
            `;
        }
        
        html += '</div>';
        document.getElementById('deploymentEnvironments').innerHTML = html;
        
    } catch (error) {
        console.error('Error loading deployment data:', error);
        document.getElementById('deploymentEnvironments').innerHTML = 
            '<p class="error-text">Error loading deployment data. Please try again.</p>';
    }
}

// Helper functions for upstream display/dropdown toggle
function showUpstreamDropdown(envId) {
    console.log('showUpstreamDropdown called for envId:', envId);
    const display = document.getElementById(`upstream_display_${envId}`);
    const dropdown = document.getElementById(`upstream_dropdown_${envId}`);
    
    console.log('Display element:', display);
    console.log('Dropdown element:', dropdown);
    
    if (display && dropdown) {
        display.style.display = 'none';
        dropdown.style.display = 'flex';
        console.log('Successfully toggled to dropdown view');
    } else {
        console.error('Elements not found! Display:', display, 'Dropdown:', dropdown);
    }
}

function hideUpstreamDropdown(envId) {
    console.log('hideUpstreamDropdown called for envId:', envId);
    const display = document.getElementById(`upstream_display_${envId}`);
    const dropdown = document.getElementById(`upstream_dropdown_${envId}`);
    
    if (display && dropdown) {
        display.style.display = 'flex';
        dropdown.style.display = 'none';
        console.log('Successfully toggled back to display view');
    } else {
        console.error('Elements not found! Display:', display, 'Dropdown:', dropdown);
    }
}

async function deploySingleEnv(orgId, revisionId, envId, envName) {
    const upstreamSelect = document.getElementById(`deploy_upstream_${envId}`);
    
    if (!upstreamSelect) {
        showNotification('error', 'Error', `Upstream selector not found for ${envName}`);
        return;
    }
    
    const upstreamId = upstreamSelect.value;
    
    if (!upstreamId) {
        showNotification('error', 'Upstream Required', `Please select an upstream for ${envName}`);
        return;
    }
    
    // Get force deploy checkbox value
    const forceCheckbox = document.getElementById(`force_deploy_${envId}`);
    const forceValue = forceCheckbox ? forceCheckbox.checked : false;
    
    const confirmMessage = forceValue 
        ? `Force deploy to ${envName}? This will undeploy any other revisions in this environment.`
        : `Deploy to ${envName}?`;
    
    if (!confirm(confirmMessage)) return;
    
    // Disable the button during deployment
    const deployBtn = event.target;
    const originalText = deployBtn.textContent;
    deployBtn.disabled = true;
    deployBtn.textContent = '⏳ Deploying...';
    
    try {
        const response = await fetch(`${API_BASE}/organizations/${orgId}/apis/revisions/${revisionId}/deploy`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                environmentIds: [envId],
                environmentUpstreams: { [envId]: upstreamId },
                force: forceValue
            })
        });
        
        if (response.ok) {
            showNotification('success', 'Deployment Successful', `Successfully deployed to ${envName}`);
            
            // Refresh the deployment data
            await loadDeploymentData(orgId, revisionId);
        } else {
            const errorText = await response.text();
            showNotification('error', 'Deployment Failed', `Failed to deploy to ${envName}: ${errorText}`);
        }
    } catch (error) {
        showNotification('error', 'Deployment Error', `Error deploying to ${envName}: ${error.message}`);
    } finally {
        // Re-enable button
        if (deployBtn) {
            deployBtn.disabled = false;
            deployBtn.textContent = originalText;
        }
    }
}

async function undeploySingleEnv(orgId, revisionId, envId, envName) {
    if (!confirm(`Undeploy from ${envName}? The API will no longer be accessible in this environment.`)) return;
    
    // Disable the button during undeployment
    const undeployBtn = event.target;
    const originalText = undeployBtn.textContent;
    undeployBtn.disabled = true;
    undeployBtn.textContent = '⏳ Undeploying...';
    
    try {
        const response = await fetch(`${API_BASE}/organizations/${orgId}/apis/revisions/${revisionId}/undeploy`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                environmentIds: [envId]
            })
        });
        
        if (response.ok) {
            showNotification('success', 'Undeployment Successful', `Successfully undeployed from ${envName}`);
            
            // Refresh the deployment data
            await loadDeploymentData(orgId, revisionId);
        } else {
            const errorText = await response.text();
            showNotification('error', 'Undeployment Failed', `Failed to undeploy from ${envName}: ${errorText}`);
        }
    } catch (error) {
        showNotification('error', 'Undeployment Error', `Error undeploying from ${envName}: ${error.message}`);
    } finally {
        // Re-enable button
        if (undeployBtn) {
            undeployBtn.disabled = false;
            undeployBtn.textContent = originalText;
        }
    }
}

// Notification System
function showNotification(type, title, message) {
    const notificationArea = document.getElementById('notificationArea');
    
    const notification = document.createElement('div');
    notification.className = `notification ${type}`;
    
    const icon = type === 'success' ? '✅' : type === 'error' ? '❌' : '⚠️';
    
    notification.innerHTML = `
        <div class="notification-icon">${icon}</div>
        <div class="notification-content">
            <div class="notification-title">${title}</div>
            <div class="notification-message">${message}</div>
        </div>
        <button class="notification-close" onclick="this.parentElement.remove()">×</button>
    `;
    
    notificationArea.appendChild(notification);
    
    // Auto-remove after 5 seconds
    setTimeout(() => {
        if (notification.parentElement) {
            notification.style.animation = 'slideOut 0.3s ease-out';
            setTimeout(() => notification.remove(), 300);
        }
    }, 5000);
}

// Initialize on page load
window.addEventListener('load', () => {
    loadOrganizations();
});

