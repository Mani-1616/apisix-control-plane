package com.apisix.controlplane.dto;

import com.apisix.controlplane.entity.UpstreamBinding;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpstreamBindingResponse {

    private String environmentId;
    private String environmentName;
    private String upstreamId;
    private String upstreamName;

    public static UpstreamBindingResponse from(UpstreamBinding binding,
                                               String environmentName,
                                               String upstreamName) {
        return UpstreamBindingResponse.builder()
                .environmentId(binding.getEnvironmentId())
                .environmentName(environmentName)
                .upstreamId(binding.getUpstreamId())
                .upstreamName(upstreamName)
                .build();
    }
}
