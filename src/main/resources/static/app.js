// Global state
let globalOrgId = null;
let selectedEnvId = null;
let environments = [];
let upstreams = [];
let servicesCache = []; // cache loaded services for lookups
let overviewPage = 1;
let overviewSize = 10;

const API_BASE = '/api';

// Initialize on page load
document.addEventListener('DOMContentLoaded', function() {
    loadOrganizations();
});

// ===== GLOBAL ORG SELECTOR =====
function handleGlobalOrgChange() {
    const selector = document.getElementById('globalOrgSelector');
    globalOrgId = selector.value;

    if (globalOrgId) {
        localStorage.setItem('selectedOrgId', globalOrgId);

        const activeTab = document.querySelector('.tab-content.active');
        if (activeTab) {
            const tabId = activeTab.id;
            if (tabId === 'environments') loadEnvironments();
            if (tabId === 'upstreams') {
                loadEnvironmentsForUpstream();
                loadEnvironmentsForUpstreamFilter();
            }
            if (tabId === 'services') {
                loadServicesDropdown();
                loadEnvironmentsForRevision();
                loadServices();
            }
            if (tabId === 'deployment') loadServicesForDeployment();
        }

        showNotification('success', 'Organization Selected',
            `Organization "${selector.options[selector.selectedIndex].text}" is now active`);
    }
}

function getCurrentOrgId() {
    if (!globalOrgId) {
        showNotification('warning', 'No Organization Selected',
            'Please select an organization from the top right corner');
        return null;
    }
    return globalOrgId;
}

// ===== TAB MANAGEMENT =====
function showTab(tabName, targetButton) {
    document.querySelectorAll('.tab-content').forEach(tab => tab.classList.remove('active'));
    document.querySelectorAll('.tab-button').forEach(btn => btn.classList.remove('active'));

    document.getElementById(tabName).classList.add('active');

    if (targetButton) {
        targetButton.classList.add('active');
    } else {
        const button = Array.from(document.querySelectorAll('.tab-button'))
            .find(btn => btn.onclick && btn.onclick.toString().includes(tabName));
        if (button) button.classList.add('active');
    }

    if (tabName === 'organizations') loadOrganizations();
    if (tabName === 'environments') loadEnvironments();
    if (tabName === 'upstreams') {
        loadEnvironmentsForUpstream();
        loadEnvironmentsForUpstreamFilter();
    }
    if (tabName === 'services-overview') {
        loadServicesOverview();
    }
    if (tabName === 'services') {
        loadServicesDropdown();
        loadEnvironmentsForRevision();
        loadServices();
    }
    if (tabName === 'developers') loadDevelopers();
    if (tabName === 'subscriptions') {
        loadDevelopersForSubscription();
        loadEnvironmentsForSubscription();
        loadDevelopersForFilter();
        loadEnvironmentsForFilter();
        loadSubscriptions();
    }
    if (tabName === 'products') {
        loadServicesForProductSelection();
        loadProducts();
    }
    if (tabName === 'product-subscriptions') {
        loadDevelopersForProductSubscription();
        loadEnvironmentsForProductSubscription();
        loadProductsForSubscription();
        loadProductSubscriptions();
    }
    if (tabName === 'deployment') loadServicesForDeployment();
}

// ===== ORGANIZATIONS =====
document.getElementById('createOrgForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const name = document.getElementById('orgName').value;
    const description = document.getElementById('orgDescription').value;

    try {
        const response = await fetch(`${API_BASE}/orgs`, {
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
        const response = await fetch(`${API_BASE}/orgs`);
        const orgs = await response.json();
        displayOrganizations(orgs);
        updateGlobalOrgSelector(orgs);
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

function updateGlobalOrgSelector(orgs) {
    const selector = document.getElementById('globalOrgSelector');
    const savedOrgId = localStorage.getItem('selectedOrgId');

    selector.innerHTML = '<option value="">Select Organization</option>' +
        orgs.map(org => `<option value="${org.id}">${org.name}</option>`).join('');

    if (savedOrgId && orgs.some(org => org.id === savedOrgId)) {
        selector.value = savedOrgId;
        globalOrgId = savedOrgId;
    }
}

// ===== ENVIRONMENTS =====
document.getElementById('createEnvForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const orgId = getCurrentOrgId();
    if (!orgId) return;

    const name = document.getElementById('envName').value;
    const description = document.getElementById('envDescription').value;
    const apisixAdminUrl = document.getElementById('envApisixUrl').value;
    const active = document.getElementById('envActive').checked;

    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/envs`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name, description, apisixAdminUrl, active })
        });

        if (response.ok) {
            showNotification('success', 'Environment Created', `Environment "${name}" created successfully`);
            e.target.reset();
            document.getElementById('envActive').checked = true;
            loadEnvironments();
        } else {
            const error = await response.text();
            showNotification('error', 'Creation Failed', error);
        }
    } catch (error) {
        showNotification('error', 'Error', error.message);
    }
});

async function loadEnvironments() {
    const orgId = getCurrentOrgId();
    if (!orgId) {
        document.getElementById('envsList').innerHTML =
            '<p class="info-text">Please select an organization from the top right corner</p>';
        return;
    }

    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/envs`);
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
            <p><strong>Status:</strong> ${env.active ? 'Active' : 'Inactive'}</p>
            <small>ID: ${env.id}</small>
        </div>
    `).join('');
}

// ===== UPSTREAMS =====
async function loadEnvironmentsForUpstream() {
    const orgId = getCurrentOrgId();
    const select = document.getElementById('upstreamEnvId');
    if (!select) return;
    if (!orgId) { select.innerHTML = '<option value="">Select org first</option>'; return; }

    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/envs`);
        const envs = await response.json();
        select.innerHTML = '<option value="">Select Environment</option>' +
            envs.map(env => `<option value="${env.id}">${env.name}</option>`).join('');
    } catch (error) {
        console.error('Error loading environments:', error);
    }
}

async function loadEnvironmentsForUpstreamFilter() {
    const orgId = getCurrentOrgId();
    const select = document.getElementById('upstreamEnvFilter');
    if (!select) return;
    if (!orgId) { select.innerHTML = '<option value="">Select Environment</option>'; return; }

    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/envs`);
        const envs = await response.json();
        select.innerHTML = '<option value="">Select Environment</option>' +
            envs.map(env => `<option value="${env.id}">${env.name}</option>`).join('');
    } catch (error) {
        console.error('Error loading environments:', error);
    }
}

document.getElementById('createUpstreamForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const orgId = getCurrentOrgId();
    if (!orgId) return;

    const envId = document.getElementById('upstreamEnvId').value;
    const name = document.getElementById('upstreamName').value;
    const specText = document.getElementById('upstreamSpecification').value.trim();

    let specification;
    try {
        specification = JSON.parse(specText);
    } catch (parseError) {
        showNotification('error', 'Invalid JSON', 'Upstream specification must be valid JSON: ' + parseError.message);
        return;
    }

    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/envs/${envId}/upstreams`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name, specification })
        });

        if (response.ok) {
            showNotification('success', 'Upstream Created', `Upstream "${name}" created in APISIX`);
            e.target.reset();
            loadUpstreams();
        } else {
            const error = await response.text();
            showNotification('error', 'Creation Failed', error);
        }
    } catch (error) {
        showNotification('error', 'Error', error.message);
    }
});

async function loadUpstreams() {
    const orgId = getCurrentOrgId();
    const envId = document.getElementById('upstreamEnvFilter').value;

    if (!orgId) {
        document.getElementById('upstreamsList').innerHTML =
            '<p class="info-text">Please select an organization from the top right corner</p>';
        return;
    }
    if (!envId) {
        document.getElementById('upstreamsList').innerHTML = '<p class="info-text">Please select an environment</p>';
        return;
    }

    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/envs/${envId}/upstreams?size=1000`);
        const data = await response.json();
        upstreams = data.content || [];
        displayUpstreams(upstreams, orgId, envId);
    } catch (error) {
        console.error('Error loading upstreams:', error);
    }
}

function displayUpstreams(upstreams, orgId, envId) {
    const list = document.getElementById('upstreamsList');
    list.innerHTML = upstreams.map(upstream => {
        const spec = upstream.specification || {};
        const specSummary = [spec.type, spec.scheme].filter(Boolean).join(' / ');
        return `
            <div class="card">
                <h3>${upstream.name}</h3>
                ${specSummary ? `<p><strong>Type / Scheme:</strong> ${specSummary}</p>` : ''}
                <small>APISIX ID: ${upstream.apisixId}</small>
                <button onclick="deleteUpstream('${orgId}', '${envId}', '${upstream.id}')">Delete</button>
            </div>
        `;
    }).join('');
}

async function deleteUpstream(orgId, envId, upstreamId) {
    if (!confirm('Are you sure you want to delete this upstream?')) return;

    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/envs/${envId}/upstreams/${upstreamId}`, {
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

// ===== SERVICES & REVISIONS =====

// Load services dropdown for revision creation form
async function loadServicesDropdown() {
    const orgId = getCurrentOrgId();
    const select = document.getElementById('revisionServiceId');
    if (!select) return;
    if (!orgId) { select.innerHTML = '<option value="">Select org first</option>'; return; }

    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/services?size=1000`);
        const data = await response.json();
        const services = data.content || [];
        servicesCache = services;

        select.innerHTML = '<option value="">Select Service</option>' +
            services.map(svc => `<option value="${svc.id}">${svc.name}</option>`).join('');
    } catch (error) {
        console.error('Error loading services:', error);
    }
}

async function loadEnvironmentsForRevision() {
    const orgId = getCurrentOrgId();
    const container = document.getElementById('environmentUpstreamsContainer');
    if (!container) return;
    if (!orgId) {
        container.innerHTML = '<p class="info-text">Please select an organization from the top right corner</p>';
        return;
    }

    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/envs`);
        const envs = await response.json();

        container.innerHTML = envs.map(env => `
            <div class="env-upstream-row">
                <label>${env.name}:</label>
                <select id="upstream_${env.id}" data-env-id="${env.id}">
                    <option value="">No upstream (set later)</option>
                </select>
            </div>
        `).join('');

        for (const env of envs) {
            const upstreamsResponse = await fetch(`${API_BASE}/orgs/${orgId}/envs/${env.id}/upstreams?size=1000`);
            const upstreamsData = await upstreamsResponse.json();
            const upstreams = upstreamsData.content || [];
            const select = document.getElementById(`upstream_${env.id}`);
            upstreams.forEach(upstream => {
                const option = document.createElement('option');
                option.value = upstream.id;
                option.textContent = upstream.name;
                select.appendChild(option);
            });
        }
    } catch (error) {
        console.error('Error loading environments for revision:', error);
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
        <input type="text" placeholder="URI (e.g., /api/users/*)" data-route-uri required>
        <textarea placeholder='Route Plugins JSON (optional), e.g., {"cors": {}}' data-route-plugins rows="2"></textarea>
        <button type="button" onclick="this.parentElement.remove()">Remove Route</button>
    `;
    container.appendChild(routeDiv);
}

// Create Service
document.getElementById('createServiceForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const orgId = getCurrentOrgId();
    if (!orgId) return;

    const name = document.getElementById('serviceName').value;
    const displayName = document.getElementById('serviceDisplayName').value;

    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/services`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name, displayName: displayName || null })
        });

        if (response.ok) {
            showNotification('success', 'Service Created', `Service "${name}" created successfully`);
            e.target.reset();
            loadServicesDropdown();
            loadServices();
        } else {
            const error = await response.text();
            showNotification('error', 'Creation Failed', error);
        }
    } catch (error) {
        showNotification('error', 'Error', error.message);
    }
});

// Create / Update Revision
document.getElementById('createRevisionForm').addEventListener('submit', async (e) => {
    e.preventDefault();

    const editingRevisionId = document.getElementById('editingRevisionId').value;
    if (editingRevisionId) {
        showNotification('warning', 'Edit Mode', 'Use "Update Revision" button or cancel first.');
        return;
    }

    await createRevision();
});

async function createRevision() {
    const orgId = getCurrentOrgId();
    if (!orgId) return;

    const serviceId = document.getElementById('revisionServiceId').value;
    if (!serviceId) {
        showNotification('warning', 'Missing Service', 'Please select a service');
        return;
    }

    // Parse service-level plugins
    const servicePluginsText = document.getElementById('servicePlugins').value.trim();
    let servicePlugins = null;
    if (servicePluginsText) {
        try {
            servicePlugins = JSON.parse(servicePluginsText);
        } catch (e) {
            showNotification('error', 'Invalid JSON', 'Service plugins JSON is invalid: ' + e.message);
            return;
        }
    }

    // Collect environment-upstream mappings as array of objects
    const environmentUpstreams = [];
    document.querySelectorAll('#environmentUpstreamsContainer select').forEach(select => {
        const envId = select.dataset.envId;
        const upstreamId = select.value;
        if (upstreamId) environmentUpstreams.push({ environmentId: envId, upstreamId });
    });

    // Collect routes as RouteSpec objects
    const routeSpecifications = [];
    let hasError = false;
    document.querySelectorAll('#routesContainer .route-item').forEach(routeDiv => {
        const name = routeDiv.querySelector('[data-route-name]').value;
        const methods = routeDiv.querySelector('[data-route-methods]').value.split(',').map(m => m.trim());
        const uri = routeDiv.querySelector('[data-route-uri]').value.trim();

        const routePluginsText = routeDiv.querySelector('[data-route-plugins]').value.trim();
        let routePlugins = null;
        if (routePluginsText) {
            try {
                routePlugins = JSON.parse(routePluginsText);
            } catch (e) {
                showNotification('error', 'Invalid JSON', `Route "${name}" plugins JSON is invalid: ${e.message}`);
                hasError = true;
            }
        }

        const routeSpec = { name, methods, uri, service_id: serviceId };
        if (routePlugins) routeSpec.plugins = routePlugins;

        routeSpecifications.push(routeSpec);
    });

    if (hasError) return;

    if (routeSpecifications.length === 0) {
        showNotification('warning', 'No Routes', 'Please add at least one route');
        return;
    }

    // Build serviceSpecification (ServiceSpec)
    const serviceSpecification = {};
    if (servicePlugins) serviceSpecification.plugins = servicePlugins;

    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/services/${serviceId}/revisions`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                environmentUpstreams,
                serviceSpecification: Object.keys(serviceSpecification).length > 0 ? serviceSpecification : null,
                routeSpecifications
            })
        });

        if (response.ok) {
            showNotification('success', 'Revision Created', 'New revision created successfully');
            document.getElementById('createRevisionForm').reset();
            document.getElementById('routesContainer').innerHTML = '';
            routeCount = 0;
            loadServices();
        } else {
            const error = await response.text();
            showNotification('error', 'Creation Failed', error);
        }
    } catch (error) {
        showNotification('error', 'Error', error.message);
    }
}

async function updateRevision() {
    const revisionId = document.getElementById('editingRevisionId').value;
    if (!revisionId) {
        showNotification('warning', 'No Revision', 'No revision selected for editing');
        return;
    }

    const orgId = getCurrentOrgId();
    if (!orgId) return;

    const serviceId = document.getElementById('revisionServiceId').value;

    const servicePluginsText = document.getElementById('servicePlugins').value.trim();
    let servicePlugins = null;
    if (servicePluginsText) {
        try {
            servicePlugins = JSON.parse(servicePluginsText);
        } catch (e) {
            showNotification('error', 'Invalid JSON', 'Service plugins JSON is invalid: ' + e.message);
            return;
        }
    }

    const routeSpecifications = [];
    let hasError = false;
    document.querySelectorAll('#routesContainer .route-item').forEach(routeDiv => {
        const name = routeDiv.querySelector('[data-route-name]').value;
        const methods = routeDiv.querySelector('[data-route-methods]').value.split(',').map(m => m.trim());
        const uri = routeDiv.querySelector('[data-route-uri]').value.trim();

        const routePluginsText = routeDiv.querySelector('[data-route-plugins]').value.trim();
        let routePlugins = null;
        if (routePluginsText) {
            try {
                routePlugins = JSON.parse(routePluginsText);
            } catch (e) {
                showNotification('error', 'Invalid JSON', `Route "${name}" plugins JSON is invalid: ${e.message}`);
                hasError = true;
            }
        }

        const routeSpec = { name, methods, uri, service_id: serviceId };
        if (routePlugins) routeSpec.plugins = routePlugins;

        routeSpecifications.push(routeSpec);
    });

    if (hasError) return;

    if (routeSpecifications.length === 0) {
        showNotification('warning', 'No Routes', 'Please add at least one route');
        return;
    }

    const serviceSpecification = {};
    if (servicePlugins) serviceSpecification.plugins = servicePlugins;

    try {
        // Step 1: Update specs
        const specsResponse = await fetch(`${API_BASE}/orgs/${orgId}/services/${serviceId}/revisions/${revisionId}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                serviceSpecification: Object.keys(serviceSpecification).length > 0 ? serviceSpecification : null,
                routeSpecifications
            })
        });

        if (!specsResponse.ok) {
            const error = await specsResponse.text();
            showNotification('error', 'Update Failed', error);
            return;
        }

        // Step 2: Update upstream bindings
        const environmentUpstreams = [];
        document.querySelectorAll('#environmentUpstreamsContainer select').forEach(select => {
            const envId = select.dataset.envId;
            const upstreamId = select.value;
            if (upstreamId) environmentUpstreams.push({ environmentId: envId, upstreamId });
        });

        if (environmentUpstreams.length > 0) {
            const bindingsResponse = await fetch(`${API_BASE}/orgs/${orgId}/services/${serviceId}/revisions/${revisionId}/upstream-bindings`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ environmentUpstreams })
            });

            if (!bindingsResponse.ok) {
                const error = await bindingsResponse.text();
                showNotification('error', 'Upstream Bindings Update Failed', error);
                return;
            }
        }

        showNotification('success', 'Revision Updated', 'Revision updated successfully');
        cancelEdit();
        loadServices();
    } catch (error) {
        showNotification('error', 'Error', error.message);
    }
}

function cancelEdit() {
    document.getElementById('editingRevisionId').value = '';
    document.getElementById('createRevisionForm').reset();
    document.getElementById('routesContainer').innerHTML = '';
    document.getElementById('revisionServiceId').disabled = false;
    document.getElementById('createButtons').style.display = 'flex';
    document.getElementById('editButtons').style.display = 'none';
    document.getElementById('revisionFormTitle').textContent = 'Create Revision';
    routeCount = 0;
}

async function loadServices() {
    const orgId = getCurrentOrgId();
    if (!orgId) {
        document.getElementById('servicesList').innerHTML =
            '<p class="info-text">Please select an organization from the top right corner</p>';
        return;
    }

    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/services?size=1000`);
        const data = await response.json();
        const services = data.content || [];
        servicesCache = services;

        if (services.length === 0) {
            document.getElementById('servicesList').innerHTML = '<p>No services found. Create your first service above!</p>';
            return;
        }

        // For each service, load its revisions
        let html = '';
        for (const svc of services) {
            const revResponse = await fetch(`${API_BASE}/orgs/${orgId}/services/${svc.id}/revisions?size=1000`);
            const revData = await revResponse.json();
            const revisions = revData.content || [];

            const revisionsHtml = revisions.map(revision => {
                const envStatus = (revision.deployments || []).map(dep =>
                    `<span class="env-badge env-deployed">${dep.environmentName || dep.environmentId.substring(0, 8)}: DEPLOYED</span>`
                ).join(' ');

                const stateClass = revision.state === 'ACTIVE' ? 'state-active' : 'state-inactive';

                return `
                    <div class="revision-item">
                        <div class="revision-info">
                            <div class="revision-number">Rev ${revision.revisionNumber}</div>
                            <div class="revision-details">
                                <span class="state-badge ${stateClass}">${revision.state}</span>
                                <div class="env-badges">${envStatus || '<span class="env-badge env-none">Not configured</span>'}</div>
                            </div>
                        </div>
                        <div class="revision-actions">
                            <button class="action-btn clone-btn" onclick="cloneRevision('${orgId}', '${svc.id}', '${revision.id}', '${revision.revisionNumber}')" title="Clone">Clone</button>
                            ${revision.state === 'INACTIVE' ? `
                                <button class="action-btn edit-btn" onclick="editRevision('${orgId}', '${svc.id}', '${revision.id}')" title="Edit">Edit</button>
                                <button class="action-btn delete-btn" onclick="deleteRevision('${orgId}', '${svc.id}', '${revision.id}')" title="Delete">Delete</button>
                            ` : ''}
                        </div>
                    </div>
                `;
            }).join('');

            html += `
                <div class="api-group">
                    <div class="api-header">
                        <h3>${svc.name}</h3>
                        <span class="revision-count">${revisions.length} revision${revisions.length !== 1 ? 's' : ''}</span>
                    </div>
                    <div class="revisions-list">
                        ${revisionsHtml || '<p class="info-text">No revisions yet</p>'}
                    </div>
                </div>
            `;
        }

        document.getElementById('servicesList').innerHTML = html;
    } catch (error) {
        console.error('Error loading services:', error);
        document.getElementById('servicesList').innerHTML = '<p>Error loading services. Check console.</p>';
    }
}

async function editRevision(orgId, serviceId, revisionId) {
    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/services/${serviceId}/revisions/${revisionId}`);
        if (!response.ok) throw new Error(await response.text());
        const revision = await response.json();

        // Switch tab without triggering data loads (we handle them below)
        document.querySelectorAll('.tab-content').forEach(tab => tab.classList.remove('active'));
        document.querySelectorAll('.tab-button').forEach(btn => btn.classList.remove('active'));
        document.getElementById('services').classList.add('active');
        const servicesBtn = Array.from(document.querySelectorAll('.tab-button'))
            .find(btn => btn.onclick && btn.onclick.toString().includes("showTab('services'"));
        if (servicesBtn) servicesBtn.classList.add('active');

        document.getElementById('editingRevisionId').value = revisionId;
        await loadEnvironmentsForRevision();
        await loadServicesDropdown();

        document.getElementById('revisionServiceId').value = serviceId;
        document.getElementById('revisionServiceId').disabled = true;

        // Populate service-level plugins
        if (revision.serviceSpecification && revision.serviceSpecification.plugins) {
            document.getElementById('servicePlugins').value = JSON.stringify(revision.serviceSpecification.plugins, null, 2);
        } else {
            document.getElementById('servicePlugins').value = '';
        }

        // Populate environment upstreams from upstream bindings
        if (revision.upstreamBindings) {
            revision.upstreamBindings.forEach(binding => {
                const select = document.getElementById(`upstream_${binding.environmentId}`);
                if (select && binding.upstreamId) select.value = binding.upstreamId;
            });
        }

        // Populate routes
        document.getElementById('routesContainer').innerHTML = '';
        routeCount = 0;
        const routeSpecs = revision.routeSpecifications || [];
        routeSpecs.forEach(route => {
            addRoute();
            const routeDiv = document.querySelector('#routesContainer .route-item:last-child');
            if (routeDiv) {
                routeDiv.querySelector('[data-route-name]').value = route.name || '';
                routeDiv.querySelector('[data-route-methods]').value = (route.methods || []).join(', ');
                routeDiv.querySelector('[data-route-uri]').value = route.uri || '';

                if (route.plugins && Object.keys(route.plugins).length > 0) {
                    routeDiv.querySelector('[data-route-plugins]').value = JSON.stringify(route.plugins, null, 2);
                }
            }
        });

        document.getElementById('createButtons').style.display = 'none';
        document.getElementById('editButtons').style.display = 'flex';
        document.getElementById('revisionFormTitle').textContent = 'Edit Inactive Revision';
    } catch (error) {
        console.error('Error loading revision:', error);
        alert('Error loading revision: ' + error.message);
    }
}

async function deleteRevision(orgId, serviceId, revisionId) {
    if (!confirm('Are you sure you want to delete this revision?')) return;

    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/services/${serviceId}/revisions/${revisionId}`, {
            method: 'DELETE'
        });

        if (response.ok) {
            showNotification('success', 'Deleted', 'Revision deleted successfully!');
            loadServices();
        } else {
            const error = await response.text();
            showNotification('error', 'Failed', 'Failed to delete revision: ' + error);
        }
    } catch (error) {
        showNotification('error', 'Error', error.message);
    }
}

async function cloneRevision(orgId, serviceId, revisionId, revisionNumber) {
    if (!confirm(`Clone Revision ${revisionNumber}? This will create a new INACTIVE revision with the same configuration.`)) return;

    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/services/${serviceId}/revisions/${revisionId}/clone`, {
            method: 'POST'
        });

        if (response.ok) {
            const cloned = await response.json();
            showNotification('success', 'Cloned', `New revision ${cloned.revisionNumber} created!`);
            loadServices();
        } else {
            const error = await response.text();
            showNotification('error', 'Failed', 'Failed to clone revision: ' + error);
        }
    } catch (error) {
        showNotification('error', 'Error', error.message);
    }
}

// ===== SERVICES OVERVIEW =====
async function loadServicesOverview(page) {
    const orgId = getCurrentOrgId();
    if (!orgId) {
        document.getElementById('servicesOverviewList').innerHTML =
            '<p class="info-text">Please select an organization from the top right corner</p>';
        document.getElementById('overviewPagination').innerHTML = '';
        return;
    }

    if (page) overviewPage = page;

    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/services/overview?page=${overviewPage}&size=${overviewSize}`);
        if (!response.ok) {
            const error = await response.text();
            document.getElementById('servicesOverviewList').innerHTML = `<p class="error-text">Error: ${error}</p>`;
            return;
        }

        const data = await response.json();
        displayServicesOverview(data);
        renderOverviewPagination(data.totalPages, data.page);
    } catch (error) {
        console.error('Error loading services overview:', error);
        document.getElementById('servicesOverviewList').innerHTML = '<p class="error-text">Error loading overview.</p>';
    }
}

function displayServicesOverview(data) {
    const list = document.getElementById('servicesOverviewList');

    if (!data.content || data.content.length === 0) {
        list.innerHTML = '<p class="info-text">No services found.</p>';
        return;
    }

    let html = '';
    for (const svc of data.content) {
        const revisions = svc.revisions || [];

        const revisionsHtml = revisions.map(revision => {
            const envStatus = (revision.deployments || []).map(dep =>
                `<span class="env-badge env-deployed">${dep.environmentName || dep.environmentId.substring(0, 8)}: DEPLOYED</span>`
            ).join(' ');

            const bindingsInfo = (revision.upstreamBindings || []).map(b =>
                `<span class="env-badge">${b.environmentName || 'env'} &rarr; ${b.upstreamName || 'upstream'}</span>`
            ).join(' ');

            const stateClass = revision.state === 'ACTIVE' ? 'state-active' : 'state-inactive';

            return `
                <div class="revision-item">
                    <div class="revision-info">
                        <div class="revision-number">Rev ${revision.revisionNumber}</div>
                        <div class="revision-details">
                            <span class="state-badge ${stateClass}">${revision.state}</span>
                            <div class="env-badges">${envStatus || '<span class="env-badge env-none">Not deployed</span>'}</div>
                            ${bindingsInfo ? `<div class="env-badges" style="margin-top: 4px;">${bindingsInfo}</div>` : ''}
                        </div>
                    </div>
                </div>
            `;
        }).join('');

        html += `
            <div class="api-group">
                <div class="api-header">
                    <h3>${svc.displayName || svc.name}</h3>
                    <span class="revision-count">${revisions.length} revision${revisions.length !== 1 ? 's' : ''}</span>
                </div>
                <div class="revisions-list">
                    ${revisionsHtml || '<p class="info-text">No revisions yet</p>'}
                </div>
            </div>
        `;
    }

    list.innerHTML = html;
}

function renderOverviewPagination(totalPages, currentPage) {
    const container = document.getElementById('overviewPagination');

    if (totalPages <= 1) {
        container.innerHTML = '';
        return;
    }

    let html = '<div class="pagination-controls">';

    html += `<button class="pagination-btn" ${currentPage <= 1 ? 'disabled' : ''} onclick="loadServicesOverview(${currentPage - 1})">Previous</button>`;

    const maxVisible = 5;
    let startPage = Math.max(1, currentPage - Math.floor(maxVisible / 2));
    let endPage = Math.min(totalPages, startPage + maxVisible - 1);
    if (endPage - startPage < maxVisible - 1) {
        startPage = Math.max(1, endPage - maxVisible + 1);
    }

    if (startPage > 1) {
        html += `<button class="pagination-btn" onclick="loadServicesOverview(1)">1</button>`;
        if (startPage > 2) html += '<span class="pagination-ellipsis">...</span>';
    }

    for (let i = startPage; i <= endPage; i++) {
        html += `<button class="pagination-btn ${i === currentPage ? 'active' : ''}" onclick="loadServicesOverview(${i})">${i}</button>`;
    }

    if (endPage < totalPages) {
        if (endPage < totalPages - 1) html += '<span class="pagination-ellipsis">...</span>';
        html += `<button class="pagination-btn" onclick="loadServicesOverview(${totalPages})">${totalPages}</button>`;
    }

    html += `<button class="pagination-btn" ${currentPage >= totalPages ? 'disabled' : ''} onclick="loadServicesOverview(${currentPage + 1})">Next</button>`;

    html += '</div>';
    container.innerHTML = html;
}

// ===== DEPLOYMENT =====
async function loadServicesForDeployment() {
    const orgId = getCurrentOrgId();
    const select = document.getElementById('deployServiceId');
    if (!select) return;
    if (!orgId) { select.innerHTML = '<option value="">Select org first</option>'; return; }

    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/services?size=1000`);
        const data = await response.json();
        const services = data.content || [];
        servicesCache = services;

        select.innerHTML = '<option value="">Select Service</option>' +
            services.map(svc => `<option value="${svc.id}">${svc.name}</option>`).join('');
    } catch (error) {
        console.error('Error loading services:', error);
    }
}

async function loadServiceRevisions() {
    const orgId = getCurrentOrgId();
    if (!orgId) return;

    const serviceId = document.getElementById('deployServiceId').value;
    if (!serviceId) return;

    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/services/${serviceId}/revisions?size=1000`);
        const data = await response.json();
        const revisions = data.content || [];

        const select = document.getElementById('deployRevisionId');
        select.innerHTML = '<option value="">Select Revision</option>' +
            revisions.map(rev => `<option value="${rev.id}">Rev ${rev.revisionNumber} (${rev.state})</option>`).join('');
    } catch (error) {
        console.error('Error loading revisions:', error);
    }
}

async function loadDeploymentData(providedOrgId, providedRevisionId) {
    const orgId = providedOrgId || getCurrentOrgId();
    const revisionId = providedRevisionId || document.getElementById('deployRevisionId').value;
    const serviceId = document.getElementById('deployServiceId').value;

    if (!orgId) {
        document.getElementById('deploymentEnvironments').innerHTML =
            '<p class="info-text">Please select an organization</p>';
        return;
    }
    if (!revisionId || !serviceId) {
        document.getElementById('deploymentEnvironments').innerHTML = '';
        return;
    }

    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/services/${serviceId}/revisions/${revisionId}`);
        const revision = await response.json();

        const envsResponse = await fetch(`${API_BASE}/orgs/${orgId}/envs`);
        const envs = await envsResponse.json();

        if (envs.length === 0) {
            document.getElementById('deploymentEnvironments').innerHTML =
                '<p class="info-text">No environments found. Create environments first.</p>';
            return;
        }

        let html = '<h3 style="margin-top: 20px;">Environments</h3>';
        html += '<div class="deployment-environments-grid">';

        for (const env of envs) {
            // Find deployment and upstream binding for this environment
            const deployment = (revision.deployments || []).find(d => d.environmentId === env.id);
            const binding = (revision.upstreamBindings || []).find(b => b.environmentId === env.id);
            const isDeployed = !!deployment;
            const currentUpstreamId = binding ? binding.upstreamId : null;

            const upstreamsResponse = await fetch(`${API_BASE}/orgs/${orgId}/envs/${env.id}/upstreams?size=1000`);
            const upstreamsPageData = await upstreamsResponse.json();
            const upstreams = upstreamsPageData.content || [];

            const statusClass = isDeployed ? 'status-active' : 'status-inactive';
            const statusText = isDeployed ? 'Active' : 'Inactive';

            const configuredUpstream = currentUpstreamId
                ? upstreams.find(u => u.id === currentUpstreamId)
                : null;

            html += `
                <div class="deployment-env-card ${statusClass}">
                    <div class="env-card-header">
                        <h4>${env.name}</h4>
                        <span class="env-status-badge ${statusClass}">${statusText}</span>
                    </div>
                    <div class="env-card-body">
                        <div class="env-upstream-select">
                            <label>Upstream:</label>
                            ${configuredUpstream ? `
                                <div id="upstream_display_${env.id}" class="upstream-display">
                                    <div class="upstream-info">
                                        <strong>${configuredUpstream.name}</strong>
                                    </div>
                                    ${!isDeployed ? `
                                        <button type="button" class="btn-change-upstream"
                                                onclick="showUpstreamDropdown('${env.id}')">Change</button>
                                    ` : `<small style="color: var(--text-secondary);"><em>(Locked - Deployed)</em></small>`}
                                </div>
                                <div id="upstream_dropdown_${env.id}" class="upstream-dropdown" style="display: none;">
                                    <select id="deploy_upstream_${env.id}">
                                        <option value="">Select Upstream</option>
                                        ${upstreams.map(u => `
                                            <option value="${u.id}" ${currentUpstreamId === u.id ? 'selected' : ''}>
                                                ${u.name}
                                            </option>
                                        `).join('')}
                                    </select>
                                    <button type="button" class="btn-save-upstream"
                                            onclick="saveUpstreamBinding('${orgId}', '${serviceId}', '${revisionId}', '${env.id}')">Save</button>
                                    <button type="button" class="btn-cancel-change"
                                            onclick="hideUpstreamDropdown('${env.id}')">Cancel</button>
                                </div>
                            ` : `
                                <select id="deploy_upstream_${env.id}"
                                        ${isDeployed ? 'disabled' : ''}>
                                    <option value="">Select Upstream</option>
                                    ${upstreams.map(u => `<option value="${u.id}">${u.name}</option>`).join('')}
                                </select>
                                ${!isDeployed ? `
                                    <button type="button" class="btn-save-upstream" style="margin-top: 8px;"
                                            onclick="saveUpstreamBinding('${orgId}', '${serviceId}', '${revisionId}', '${env.id}')">Save Upstream</button>
                                ` : ''}
                            `}
                        </div>
                        ${deployment && deployment.deployedAt ? `
                            <p class="env-last-action">Last deployed: ${new Date(deployment.deployedAt).toLocaleString()}</p>
                        ` : ''}
                    </div>
                    <div class="env-card-actions">
                        ${isDeployed ? `
                            <button type="button" class="btn-undeploy" onclick="undeploySingleEnv('${orgId}', '${serviceId}', '${revisionId}', '${env.id}', '${env.name}')">
                                Undeploy
                            </button>
                        ` : `
                            <div style="display: flex; flex-direction: column; gap: 8px; width: 100%;">
                                <label style="display: flex; align-items: center; gap: 8px; font-size: 0.9rem; color: var(--text-secondary);">
                                    <input type="checkbox" id="force_deploy_${env.id}" style="cursor: pointer;">
                                    <span>Force deploy (undeploy other revisions)</span>
                                </label>
                                <button type="button" class="btn-deploy"
                                        onclick="deploySingleEnv('${orgId}', '${serviceId}', '${revisionId}', '${env.id}', '${env.name}')"
                                        ${upstreams.length === 0 ? 'disabled title="No upstreams available"' : ''}>
                                    Deploy
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
            '<p class="error-text">Error loading deployment data.</p>';
    }
}

function showUpstreamDropdown(envId) {
    const display = document.getElementById(`upstream_display_${envId}`);
    const dropdown = document.getElementById(`upstream_dropdown_${envId}`);
    if (display && dropdown) {
        display.style.display = 'none';
        dropdown.style.display = 'flex';
    }
}

function hideUpstreamDropdown(envId) {
    const display = document.getElementById(`upstream_display_${envId}`);
    const dropdown = document.getElementById(`upstream_dropdown_${envId}`);
    if (display && dropdown) {
        display.style.display = 'flex';
        dropdown.style.display = 'none';
    }
}

async function saveUpstreamBinding(orgId, serviceId, revisionId, envId) {
    const upstreamSelect = document.getElementById(`deploy_upstream_${envId}`);
    if (!upstreamSelect) return;

    const upstreamId = upstreamSelect.value;
    if (!upstreamId) {
        showNotification('error', 'Upstream Required', 'Please select an upstream');
        return;
    }

    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/services/${serviceId}/revisions/${revisionId}/upstream-bindings`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                environmentUpstreams: [{ environmentId: envId, upstreamId }]
            })
        });

        if (response.ok) {
            showNotification('success', 'Upstream Saved', 'Upstream binding saved successfully');
            await loadDeploymentData(orgId, revisionId);
        } else {
            const errorText = await response.text();
            showNotification('error', 'Save Failed', errorText);
        }
    } catch (error) {
        showNotification('error', 'Error', error.message);
    }
}

async function deploySingleEnv(orgId, serviceId, revisionId, envId, envName) {
    const upstreamSelect = document.getElementById(`deploy_upstream_${envId}`);
    if (!upstreamSelect) return;

    const upstreamId = upstreamSelect.value;
    if (!upstreamId) {
        showNotification('error', 'Upstream Required', `Please select an upstream for ${envName}`);
        return;
    }

    const forceCheckbox = document.getElementById(`force_deploy_${envId}`);
    const forceValue = forceCheckbox ? forceCheckbox.checked : false;

    const msg = forceValue
        ? `Force deploy to ${envName}? This will undeploy any other revisions.`
        : `Deploy to ${envName}?`;
    if (!confirm(msg)) return;

    try {
        // Set upstream binding first when not force-deploying (deploy uses existing bindings only).
        // When force-deploying we may already be deployed to this env, so binding updates are not allowed.
        if (!forceValue) {
            const bindingsResponse = await fetch(`${API_BASE}/orgs/${orgId}/services/${serviceId}/revisions/${revisionId}/upstream-bindings`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    environmentUpstreams: [{ environmentId: envId, upstreamId }]
                })
            });

            if (!bindingsResponse.ok) {
                const errorText = await bindingsResponse.text();
                showNotification('error', 'Upstream Binding Failed', errorText);
                return;
            }
        }

        const response = await fetch(`${API_BASE}/orgs/${orgId}/services/${serviceId}/revisions/${revisionId}/deploy`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                environmentId: envId,
                force: forceValue
            })
        });

        if (response.ok) {
            showNotification('success', 'Deployment Successful', `Deployed to ${envName}`);
            await loadDeploymentData(orgId, revisionId);
        } else {
            const errorText = await response.text();
            showNotification('error', 'Deployment Failed', errorText);
        }
    } catch (error) {
        showNotification('error', 'Error', error.message);
    }
}

async function undeploySingleEnv(orgId, serviceId, revisionId, envId, envName) {
    if (!confirm(`Undeploy from ${envName}?`)) return;

    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/services/${serviceId}/revisions/${revisionId}/undeploy`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ environmentId: envId })
        });

        if (response.ok) {
            showNotification('success', 'Undeployment Successful', `Undeployed from ${envName}`);
            await loadDeploymentData(orgId, revisionId);
        } else {
            const errorText = await response.text();
            showNotification('error', 'Undeployment Failed', errorText);
        }
    } catch (error) {
        showNotification('error', 'Error', error.message);
    }
}

// ===== NOTIFICATION =====
function showNotification(type, title, message) {
    const notificationArea = document.getElementById('notificationArea');
    const notification = document.createElement('div');
    notification.className = `notification ${type}`;

    const icon = type === 'success' ? 'OK' : type === 'error' ? 'ERR' : 'WARN';

    notification.innerHTML = `
        <div class="notification-icon">${icon}</div>
        <div class="notification-content">
            <div class="notification-title">${title}</div>
            <div class="notification-message">${message}</div>
        </div>
        <button class="notification-close" onclick="this.parentElement.remove()">x</button>
    `;

    notificationArea.appendChild(notification);

    setTimeout(() => {
        if (notification.parentElement) {
            notification.style.animation = 'slideOut 0.3s ease-out';
            setTimeout(() => notification.remove(), 300);
        }
    }, 5000);
}

// ===== DEVELOPERS =====
document.getElementById('createDeveloperForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const orgId = getCurrentOrgId();
    if (!orgId) return;

    const email = document.getElementById('developerEmail').value;
    const name = document.getElementById('developerName').value;

    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/developers`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, name })
        });

        if (response.ok) {
            showNotification('success', 'Developer Added', `Developer "${name}" added`);
            e.target.reset();
            loadDevelopers();
        } else {
            const error = await response.text();
            showNotification('error', 'Failed', error);
        }
    } catch (error) {
        showNotification('error', 'Error', error.message);
    }
});

async function loadDevelopers() {
    const orgId = getCurrentOrgId();
    if (!orgId) {
        document.getElementById('developersList').innerHTML =
            '<p class="info-text">Please select an organization</p>';
        return;
    }

    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/developers`);
        const developers = await response.json();
        displayDevelopers(developers, orgId);
    } catch (error) {
        console.error('Error loading developers:', error);
    }
}

function displayDevelopers(developers, orgId) {
    const list = document.getElementById('developersList');
    if (developers.length === 0) {
        list.innerHTML = '<p class="info-text">No developers found.</p>';
        return;
    }

    list.innerHTML = developers.map(dev => `
        <div class="card">
            <h3>${dev.name}</h3>
            <p><strong>Email:</strong> ${dev.email}</p>
            <small>ID: ${dev.id}</small>
            <div class="card-actions">
                <button onclick="deleteDeveloper('${orgId}', '${dev.id}', '${dev.name}')" class="btn-danger">Remove</button>
            </div>
        </div>
    `).join('');
}

async function deleteDeveloper(orgId, developerId, name) {
    if (!confirm(`Remove developer "${name}"?`)) return;

    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/developers/${developerId}`, { method: 'DELETE' });
        if (response.ok) {
            showNotification('success', 'Removed', `Developer "${name}" removed`);
            loadDevelopers();
        } else {
            showNotification('error', 'Failed', await response.text());
        }
    } catch (error) {
        showNotification('error', 'Error', error.message);
    }
}

// ===== API SUBSCRIPTIONS =====
document.getElementById('createSubscriptionForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const orgId = getCurrentOrgId();
    if (!orgId) return;

    const developerId = document.getElementById('subDeveloperId').value;
    const envId = document.getElementById('subEnvId').value;
    const serviceId = document.getElementById('subServiceId').value;

    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/subscriptions`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ developerId, envId, serviceId })
        });

        if (response.ok) {
            const subscription = await response.json();
            showNotification('success', 'Subscription Created',
                `API key: ${subscription.apiKey}<br><small>Save this key!</small>`);
            e.target.reset();
            loadSubscriptions();
        } else {
            showNotification('error', 'Failed', await response.text());
        }
    } catch (error) {
        showNotification('error', 'Error', error.message);
    }
});

async function loadDevelopersForSubscription() {
    const orgId = getCurrentOrgId();
    const select = document.getElementById('subDeveloperId');
    if (!select) return;
    if (!orgId) { select.innerHTML = '<option value="">Select org first</option>'; return; }

    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/developers`);
        const developers = await response.json();
        select.innerHTML = '<option value="">Select Developer</option>' +
            developers.map(dev => `<option value="${dev.id}">${dev.name} (${dev.email})</option>`).join('');
    } catch (error) {
        console.error('Error:', error);
    }
}

async function loadEnvironmentsForSubscription() {
    const orgId = getCurrentOrgId();
    const select = document.getElementById('subEnvId');
    if (!select) return;
    if (!orgId) { select.innerHTML = '<option value="">Select org first</option>'; return; }

    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/envs`);
        const envs = await response.json();
        select.innerHTML = '<option value="">Select Environment</option>' +
            envs.map(env => `<option value="${env.id}">${env.name}</option>`).join('');
    } catch (error) {
        console.error('Error:', error);
    }
}

async function loadServicesForSubscription() {
    const orgId = getCurrentOrgId();
    const envId = document.getElementById('subEnvId').value;
    const select = document.getElementById('subServiceId');
    if (!select) return;
    if (!orgId || !envId) { select.innerHTML = '<option value="">Select env first</option>'; return; }

    try {
        // Get all services and check which have deployed revisions in this env
        const svcResponse = await fetch(`${API_BASE}/orgs/${orgId}/services?size=1000`);
        const svcData = await svcResponse.json();
        const services = svcData.content || [];

        const deployedServices = [];
        for (const svc of services) {
            const revResponse = await fetch(`${API_BASE}/orgs/${orgId}/services/${svc.id}/revisions?size=1000`);
            const revData = await revResponse.json();
            const revisions = revData.content || [];
            const hasDeployed = revisions.some(rev =>
                (rev.deployments || []).some(dep => dep.environmentId === envId)
            );
            if (hasDeployed) {
                deployedServices.push(svc);
            }
        }

        select.innerHTML = '<option value="">Select Service</option>' +
            deployedServices.map(svc => `<option value="${svc.id}">${svc.name}</option>`).join('');
    } catch (error) {
        console.error('Error:', error);
    }
}

async function loadDevelopersForFilter() {
    const orgId = getCurrentOrgId();
    const select = document.getElementById('subFilterDeveloperId');
    if (!select) return;
    if (!orgId) { select.innerHTML = '<option value="">All Developers</option>'; return; }

    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/developers`);
        const developers = await response.json();
        select.innerHTML = '<option value="">All Developers</option>' +
            developers.map(dev => `<option value="${dev.id}">${dev.name}</option>`).join('');
    } catch (error) {
        console.error('Error:', error);
    }
}

async function loadEnvironmentsForFilter() {
    const orgId = getCurrentOrgId();
    const select = document.getElementById('subFilterEnvId');
    if (!select) return;
    if (!orgId) { select.innerHTML = '<option value="">All Environments</option>'; return; }

    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/envs`);
        const envs = await response.json();
        select.innerHTML = '<option value="">All Environments</option>' +
            envs.map(env => `<option value="${env.id}">${env.name}</option>`).join('');
    } catch (error) {
        console.error('Error:', error);
    }
}

async function loadSubscriptions() {
    const orgId = getCurrentOrgId();
    if (!orgId) {
        document.getElementById('subscriptionsList').innerHTML =
            '<p class="info-text">Please select an organization</p>';
        return;
    }

    const developerId = document.getElementById('subFilterDeveloperId')?.value || '';
    const envId = document.getElementById('subFilterEnvId')?.value || '';

    try {
        let url = `${API_BASE}/orgs/${orgId}/subscriptions`;
        const params = new URLSearchParams();
        if (developerId) params.append('developerId', developerId);
        if (envId) params.append('envId', envId);
        if (params.toString()) url += `?${params.toString()}`;

        const response = await fetch(url);
        const subscriptions = await response.json();
        displaySubscriptions(subscriptions, orgId);
    } catch (error) {
        console.error('Error:', error);
    }
}

function getServiceName(serviceId) {
    const svc = servicesCache.find(s => s.id === serviceId);
    return svc ? svc.name : serviceId;
}

function displaySubscriptions(subscriptions, orgId) {
    const list = document.getElementById('subscriptionsList');
    if (subscriptions.length === 0) {
        list.innerHTML = '<p class="info-text">No subscriptions found.</p>';
        return;
    }

    list.innerHTML = subscriptions.map(sub => {
        const statusClass = sub.status === 'ACTIVE' ? 'success' :
            sub.status === 'REVOKED' ? 'danger' : 'warning';

        return `
            <div class="card">
                <h3>${getServiceName(sub.serviceId)}</h3>
                <p><strong>Service ID:</strong> ${sub.serviceId}</p>
                <p><strong>Developer ID:</strong> ${sub.developerId}</p>
                <p><strong>Environment ID:</strong> ${sub.envId}</p>
                <p><strong>Consumer ID:</strong> ${sub.apisixConsumerId}</p>
                <p><strong>Status:</strong> <span class="badge ${statusClass}">${sub.status}</span></p>
                <p><strong>Created:</strong> ${new Date(sub.createdAt).toLocaleString()}</p>
                <div class="card-actions">
                    ${sub.status === 'ACTIVE' ? `
                        <button onclick="revokeSubscription('${orgId}', '${sub.id}')" class="btn-danger">Revoke Access</button>
                    ` : ''}
                    ${sub.status === 'REVOKED' ? `
                        <button onclick="grantSubscription('${orgId}', '${sub.id}')" class="btn-primary">Grant Access</button>
                    ` : ''}
                </div>
            </div>
        `;
    }).join('');
}

async function revokeSubscription(orgId, subscriptionId) {
    if (!confirm('Revoke this subscription?')) return;

    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/subscriptions/${subscriptionId}`, { method: 'DELETE' });
        if (response.ok) {
            showNotification('success', 'Revoked', 'Subscription revoked');
            loadSubscriptions();
        } else {
            showNotification('error', 'Failed', await response.text());
        }
    } catch (error) {
        showNotification('error', 'Error', error.message);
    }
}

async function grantSubscription(orgId, subscriptionId) {
    if (!confirm('Grant access again?')) return;

    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/subscriptions/${subscriptionId}/grant`, { method: 'PUT' });
        if (response.ok) {
            showNotification('success', 'Granted', 'Access granted');
            loadSubscriptions();
        } else {
            showNotification('error', 'Failed', await response.text());
        }
    } catch (error) {
        showNotification('error', 'Error', error.message);
    }
}

// ===== API PRODUCTS =====
async function loadProducts() {
    const orgId = getCurrentOrgId();
    if (!orgId) {
        document.getElementById('productsList').innerHTML =
            '<p class="info-text">Please select an organization</p>';
        return;
    }

    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/products`);
        const products = await response.json();
        displayProducts(products, orgId);
    } catch (error) {
        console.error('Error:', error);
    }
}

function displayProducts(products, orgId) {
    const list = document.getElementById('productsList');
    if (products.length === 0) {
        list.innerHTML = '<p class="info-text">No products found.</p>';
        return;
    }

    list.innerHTML = products.map(product => {
        const serviceNames = (product.serviceIds || []).map(id => getServiceName(id)).join(', ');
        return `
            <div class="card">
                <h3>${product.displayName || product.name}</h3>
                <p><strong>Name:</strong> ${product.name}</p>
                <p><strong>Services:</strong> ${serviceNames || 'None'}</p>
                ${product.description ? `<p><strong>Description:</strong> ${product.description}</p>` : ''}
                <p><strong>Created:</strong> ${new Date(product.createdAt).toLocaleString()}</p>
                <div class="card-actions">
                    <button onclick="deleteProduct('${orgId}', '${product.id}', '${product.name}', false)" class="btn-danger">Delete</button>
                    <button onclick="deleteProduct('${orgId}', '${product.id}', '${product.name}', true)" class="btn-danger" style="opacity: 0.8;">Force Delete</button>
                </div>
            </div>
        `;
    }).join('');
}

async function loadServicesForProductSelection() {
    const orgId = getCurrentOrgId();
    if (!orgId) return;

    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/services?size=1000`);
        const data = await response.json();
        const services = data.content || [];
        servicesCache = services;

        const container = document.getElementById('productServicesSelection');
        if (services.length === 0) {
            container.innerHTML = '<p style="color: var(--text-secondary);">No services available</p>';
            return;
        }

        container.innerHTML = services.map(svc => `
            <label style="display: block; padding: 5px 0;">
                <input type="checkbox" class="product-service-checkbox" value="${svc.id}">
                ${svc.name}
            </label>
        `).join('');
    } catch (error) {
        console.error('Error:', error);
        document.getElementById('productServicesSelection').innerHTML =
            '<p style="color: var(--danger-color);">Failed to load services</p>';
    }
}

document.getElementById('createProductForm').addEventListener('submit', async (e) => {
    e.preventDefault();

    const orgId = getCurrentOrgId();
    if (!orgId) return;

    const selectedServiceIds = Array.from(document.querySelectorAll('.product-service-checkbox:checked'))
        .map(cb => cb.value);

    if (selectedServiceIds.length === 0) {
        showNotification('error', 'Validation Error', 'Please select at least one service');
        return;
    }

    const pluginConfig = document.getElementById('productPluginConfig').value.trim();
    if (pluginConfig) {
        try {
            JSON.parse(pluginConfig);
        } catch (error) {
            showNotification('error', 'Invalid JSON', 'Plugin configuration must be valid JSON');
            return;
        }
    }

    const productData = {
        name: document.getElementById('productName').value,
        displayName: document.getElementById('productDisplayName').value,
        description: document.getElementById('productDescription').value,
        serviceIds: selectedServiceIds,
        pluginConfig: pluginConfig || null
    };

    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/products`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(productData)
        });

        if (response.ok) {
            showNotification('success', 'Product Created', 'Product created!');
            document.getElementById('createProductForm').reset();
            loadServicesForProductSelection();
            loadProducts();
        } else {
            showNotification('error', 'Failed', await response.text());
        }
    } catch (error) {
        showNotification('error', 'Error', error.message);
    }
});

async function deleteProduct(orgId, productId, productName, force) {
    const message = force
        ? `FORCE DELETE product "${productName}"? This deletes ALL subscriptions and consumer groups.`
        : `Delete product "${productName}"?`;
    if (!confirm(message)) return;

    try {
        const url = `${API_BASE}/orgs/${orgId}/products/${productId}${force ? '?force=true' : ''}`;
        const response = await fetch(url, { method: 'DELETE' });

        if (response.ok) {
            showNotification('success', 'Deleted', `Product deleted${force ? ' (with all subscriptions)' : ''}!`);
            loadProducts();
        } else {
            const errorData = await response.json();
            showNotification('error', 'Failed', errorData.message || 'Failed to delete product');
        }
    } catch (error) {
        showNotification('error', 'Error', error.message);
    }
}

// ===== PRODUCT SUBSCRIPTIONS =====
async function loadProductSubscriptions() {
    const orgId = getCurrentOrgId();
    if (!orgId) {
        document.getElementById('productSubscriptionsList').innerHTML =
            '<p class="info-text">Please select an organization</p>';
        return;
    }

    const developerId = document.getElementById('prodSubFilterDeveloperId')?.value || '';
    const envId = document.getElementById('prodSubFilterEnvId')?.value || '';

    try {
        let url = `${API_BASE}/orgs/${orgId}/product-subscriptions`;
        const params = new URLSearchParams();
        if (developerId) params.append('developerId', developerId);
        if (envId) params.append('envId', envId);
        if (params.toString()) url += `?${params.toString()}`;

        const response = await fetch(url);
        const subscriptions = await response.json();
        displayProductSubscriptions(subscriptions, orgId);
    } catch (error) {
        console.error('Error:', error);
    }
}

function displayProductSubscriptions(subscriptions, orgId) {
    const list = document.getElementById('productSubscriptionsList');
    if (subscriptions.length === 0) {
        list.innerHTML = '<p class="info-text">No product subscriptions found.</p>';
        return;
    }

    list.innerHTML = subscriptions.map(sub => {
        const statusClass = sub.status === 'ACTIVE' ? 'success' :
            sub.status === 'REVOKED' ? 'danger' : 'warning';
        return `
            <div class="card">
                <h3>Product Subscription</h3>
                <p><strong>Product ID:</strong> ${sub.productId}</p>
                <p><strong>Developer ID:</strong> ${sub.developerId}</p>
                <p><strong>Environment ID:</strong> ${sub.envId}</p>
                <p><strong>Consumer ID:</strong> ${sub.consumerId}</p>
                <p><strong>Consumer Group:</strong> ${sub.consumerGroupId}</p>
                <p><strong>API Key:</strong> <code>${sub.apiKey}</code></p>
                <p><strong>Status:</strong> <span class="badge badge-${statusClass}">${sub.status}</span></p>
                <p><strong>Created:</strong> ${new Date(sub.createdAt).toLocaleString()}</p>
                <div class="card-actions">
                    ${sub.status === 'ACTIVE' ? `
                        <button onclick="revokeProductSubscription('${orgId}', '${sub.id}')" class="btn-danger">Revoke</button>
                    ` : ''}
                    ${sub.status === 'REVOKED' ? `
                        <button onclick="grantProductSubscription('${orgId}', '${sub.id}')" class="btn-primary">Grant</button>
                    ` : ''}
                </div>
            </div>
        `;
    }).join('');
}

async function loadDevelopersForProductSubscription() {
    const orgId = getCurrentOrgId();
    if (!orgId) return;

    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/developers`);
        const developers = await response.json();
        ['prodSubDeveloperId', 'prodSubFilterDeveloperId'].forEach(id => {
            const select = document.getElementById(id);
            if (select) {
                const currentValue = select.value;
                select.innerHTML = '<option value="">Select Developer</option>' +
                    developers.map(dev => `<option value="${dev.id}">${dev.name} (${dev.email})</option>`).join('');
                if (currentValue) select.value = currentValue;
            }
        });
    } catch (error) {
        console.error('Error:', error);
    }
}

async function loadEnvironmentsForProductSubscription() {
    const orgId = getCurrentOrgId();
    if (!orgId) return;

    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/envs`);
        const environments = await response.json();
        ['prodSubEnvId', 'prodSubFilterEnvId'].forEach(id => {
            const select = document.getElementById(id);
            if (select) {
                const currentValue = select.value;
                select.innerHTML = '<option value="">Select Environment</option>' +
                    environments.map(env => `<option value="${env.id}">${env.name}</option>`).join('');
                if (currentValue) select.value = currentValue;
            }
        });
    } catch (error) {
        console.error('Error:', error);
    }
}

async function loadProductsForSubscription() {
    const orgId = getCurrentOrgId();
    if (!orgId) return;

    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/products`);
        const products = await response.json();
        const select = document.getElementById('prodSubProductId');
        if (select) {
            select.innerHTML = '<option value="">Select Product</option>' +
                products.map(prod => `<option value="${prod.id}">${prod.displayName || prod.name}</option>`).join('');
        }
    } catch (error) {
        console.error('Error:', error);
    }
}

document.getElementById('createProductSubscriptionForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const orgId = getCurrentOrgId();
    if (!orgId) return;

    const subscriptionData = {
        developerId: document.getElementById('prodSubDeveloperId').value,
        envId: document.getElementById('prodSubEnvId').value,
        productId: document.getElementById('prodSubProductId').value
    };

    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/product-subscriptions`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(subscriptionData)
        });

        if (response.ok) {
            showNotification('success', 'Subscribed', 'Product subscription created!');
            document.getElementById('createProductSubscriptionForm').reset();
            loadProductSubscriptions();
        } else {
            showNotification('error', 'Failed', await response.text());
        }
    } catch (error) {
        showNotification('error', 'Error', error.message);
    }
});

async function revokeProductSubscription(orgId, subscriptionId) {
    if (!confirm('Revoke this product subscription?')) return;
    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/product-subscriptions/${subscriptionId}`, { method: 'DELETE' });
        if (response.ok) {
            showNotification('success', 'Revoked', 'Product subscription revoked');
            loadProductSubscriptions();
        } else {
            showNotification('error', 'Failed', await response.text());
        }
    } catch (error) {
        showNotification('error', 'Error', error.message);
    }
}

async function grantProductSubscription(orgId, subscriptionId) {
    if (!confirm('Grant access?')) return;
    try {
        const response = await fetch(`${API_BASE}/orgs/${orgId}/product-subscriptions/${subscriptionId}/grant`, { method: 'PUT' });
        if (response.ok) {
            showNotification('success', 'Granted', 'Product subscription granted');
            loadProductSubscriptions();
        } else {
            showNotification('error', 'Failed', await response.text());
        }
    } catch (error) {
        showNotification('error', 'Error', error.message);
    }
}

// Initialize
window.addEventListener('load', () => {
    loadOrganizations();
});
