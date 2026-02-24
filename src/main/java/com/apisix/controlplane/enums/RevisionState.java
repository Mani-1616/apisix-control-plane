package com.apisix.controlplane.enums;

public enum RevisionState {
    INACTIVE,   // Not deployed to any environment
    ACTIVE      // Deployed to at least one environment
}
