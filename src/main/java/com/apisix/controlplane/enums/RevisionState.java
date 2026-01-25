package com.apisix.controlplane.enums;

public enum RevisionState {
    DRAFT,      // Not yet deployed to any environment
    DEPLOYED,   // Deployed to at least one environment
    UNDEPLOYED  // Was deployed but has been undeployed from all environments
}

