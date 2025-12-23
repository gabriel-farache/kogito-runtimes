/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.kie.kogito.quarkus.serverless.workflow.opentelemetry.util;

import java.util.Map;

import org.kie.kogito.quarkus.serverless.workflow.opentelemetry.config.SonataFlowOtelConfig;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;

import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.RequestProperties;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.SERVICE_NAME;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.SERVICE_VERSION;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.SONATAFLOW_PARENT_PROCESS_INSTANCE_ID;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.SONATAFLOW_PROCESS_ID;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.SONATAFLOW_PROCESS_INSTANCE_ID;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.SONATAFLOW_PROCESS_INSTANCE_NODE;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.SONATAFLOW_PROCESS_INSTANCE_STATE;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.SONATAFLOW_PROCESS_VERSION;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.SONATAFLOW_TRANSACTION_ID;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.SONATAFLOW_WORKFLOW_STATE;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.TrackerAttributes;

public final class SpanAttributeApplier {

    private SpanAttributeApplier() {
    }

    public static void applyCommonAttributes(
            SpanBuilder spanBuilder,
            String processInstanceId,
            String processId,
            String processVersion,
            String processState,
            SonataFlowOtelConfig config) {
        spanBuilder
                .setAttribute(SONATAFLOW_PROCESS_INSTANCE_ID, processInstanceId)
                .setAttribute(SONATAFLOW_PROCESS_ID, processId)
                .setAttribute(SONATAFLOW_PROCESS_VERSION, processVersion)
                .setAttribute(SONATAFLOW_PROCESS_INSTANCE_STATE, processState)
                .setAttribute(SERVICE_NAME, config.serviceName())
                .setAttribute(SERVICE_VERSION, config.serviceVersion());
    }

    public static void applyHeaderContext(
            Span span,
            Map<String, String> headerContext,
            String processInstanceId) {
        if (span == null) {
            return;
        }

        String transactionId = null;

        if (headerContext != null && !headerContext.isEmpty()) {
            transactionId = headerContext.get(RequestProperties.TRANSACTION_ID);

            for (Map.Entry<String, String> entry : headerContext.entrySet()) {
                if (entry.getKey().startsWith(RequestProperties.TRACKER_PREFIX)) {
                    String attributeKey = TrackerAttributes.createTrackerAttributeKey(entry.getKey());
                    span.setAttribute(attributeKey, entry.getValue());
                }
            }
        }

        if (transactionId == null) {
            transactionId = processInstanceId;
        }

        span.setAttribute(SONATAFLOW_TRANSACTION_ID, transactionId);
    }

    public static void applyOptionalParentProcessId(
            SpanBuilder spanBuilder,
            String parentProcessInstanceId) {
        if (parentProcessInstanceId != null && !parentProcessInstanceId.isEmpty()) {
            spanBuilder.setAttribute(SONATAFLOW_PARENT_PROCESS_INSTANCE_ID, parentProcessInstanceId);
        }
    }

    /**
     * Applies the workflow state attribute to the span builder if the state name is present.
     *
     * @param spanBuilder the span builder to configure
     * @param stateName the workflow state name (can be null or empty)
     */
    public static void applyOptionalWorkflowState(
            SpanBuilder spanBuilder,
            String stateName) {
        if (stateName != null && !stateName.isEmpty()) {
            spanBuilder.setAttribute(SONATAFLOW_WORKFLOW_STATE, stateName);
        }
    }

    /**
     * Applies the node attribute to the span builder.
     *
     * @param spanBuilder the span builder to configure
     * @param nodeId the node identifier
     */
    public static void applyNodeAttribute(
            SpanBuilder spanBuilder,
            String nodeId) {
        if (nodeId != null) {
            spanBuilder.setAttribute(SONATAFLOW_PROCESS_INSTANCE_NODE, nodeId);
        }
    }
}
