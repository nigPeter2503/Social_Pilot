package com.socialpilot.app.data.local.snapshot;

import com.socialpilot.app.data.remote.social.FollowerResult;
import com.socialpilot.app.data.remote.social.PostResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The versioned adapter ID is opaque. A registry entry with that ID is immutable. */
public record MetricDefinition(String definitionId, String metricKey, String unit, String entityType,
        String measurementKind, String aggregation, String providerMetricName, String providerFilter,
        String measurementWindow, boolean permitsNegative, List<String> componentDefinitionIds) {
    public MetricDefinition {
        componentDefinitionIds = Collections.unmodifiableList(new ArrayList<>(componentDefinitionIds));
    }

    public static MetricDefinition from(FollowerResult.MetricDefinition d) {
        return new MetricDefinition(d.definitionId, d.metricKey.name(), d.unit.name(), d.entityType.name(),
                d.measurementKind.name(), d.aggregation.name(), d.providerMetricName, null, null,
                false, d.componentDefinitionIds);
    }

    public static MetricDefinition from(PostResult.MetricDefinition d) {
        return new MetricDefinition(d.definitionId, d.metricKey.name(), d.unit.name(), d.entityType.name(),
                d.measurementKind.name(), d.aggregation.name(), d.providerMetricName, d.providerFilter,
                d.measurementWindow, d.permitsNegative, d.componentDefinitionIds);
    }
}
