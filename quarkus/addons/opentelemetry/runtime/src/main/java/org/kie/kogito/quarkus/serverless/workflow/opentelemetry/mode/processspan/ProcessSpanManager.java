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
package org.kie.kogito.quarkus.serverless.workflow.opentelemetry.mode.processspan;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.kie.kogito.quarkus.serverless.workflow.opentelemetry.AbstractSpanManager;
import org.kie.kogito.quarkus.serverless.workflow.opentelemetry.common.OtelContextHolder;
import org.kie.kogito.quarkus.serverless.workflow.opentelemetry.config.SonataFlowOtelConfig;
import org.kie.kogito.quarkus.serverless.workflow.opentelemetry.util.SpanAttributeApplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.SpanNames;

/**
 * Manages process-level spans for short transition mode.
 * Creates a single span per process instance that covers the entire workflow execution.
 * This class is instantiated at runtime based on the transition mode configuration.
 */
public class ProcessSpanManager extends AbstractSpanManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessSpanManager.class);

    private final Map<String, Span> activeProcessSpans = new ConcurrentHashMap<>();

    public ProcessSpanManager(Tracer tracer, SonataFlowOtelConfig config) {
        super(tracer, config);
    }

    /**
     * Creates a process-level span for short transition mode with an optional start time.
     *
     * @param processInstanceId the process instance ID
     * @param processId the process ID
     * @param processVersion the process version
     * @param processState the current process state
     * @param parentProcessInstanceId the parent process instance ID (may be null)
     * @param headerContext the HTTP header context for extracting transaction ID and tracker attributes
     * @param startTime the start time for the span (may be null to use current time)
     * @return the created span, or null if span creation is disabled or failed
     */
    public Span createProcessSpan(String processInstanceId, String processId, String processVersion,
            String processState, String parentProcessInstanceId, Map<String, String> headerContext,
            Instant startTime) {
        if (!isSpanCreationEnabled()) {
            LOGGER.debug("Span creation disabled for process span");
            return null;
        }

        try {
            Context parentContext = OtelContextHolder.getHttpRequestContext();
            if (parentContext == null) {
                parentContext = Context.current();
            }

            String spanName = SpanNames.createProcessSpanName(processId);

            var spanBuilder = tracer.spanBuilder(spanName)
                    .setParent(parentContext)
                    .setSpanKind(SpanKind.INTERNAL);

            SpanAttributeApplier.applyCommonAttributes(spanBuilder, processInstanceId, processId,
                    processVersion, processState, config);

            SpanAttributeApplier.applyOptionalParentProcessId(spanBuilder, parentProcessInstanceId);

            if (startTime != null) {
                spanBuilder.setStartTimestamp(startTime);
            }

            Span span = spanBuilder.startSpan();

            SpanAttributeApplier.applyHeaderContext(span, headerContext, processInstanceId);

            activeProcessSpans.put(processInstanceId, span);
            LOGGER.debug("Created process span for {}", processInstanceId);
            return span;
        } catch (Exception e) {
            LOGGER.error("Failed to create process span for {}", processInstanceId, e);
            return null;
        }
    }

    /**
     * Creates a process-level span for short transition mode.
     *
     * @param processInstanceId the process instance ID
     * @param processId the process ID
     * @param processVersion the process version
     * @param processState the current process state
     * @param parentProcessInstanceId the parent process instance ID (may be null)
     * @param headerContext the HTTP header context for extracting transaction ID and tracker attributes
     * @return the created span, or null if span creation is disabled or failed
     */
    public Span createProcessSpan(String processInstanceId, String processId, String processVersion,
            String processState, String parentProcessInstanceId, Map<String, String> headerContext) {
        return createProcessSpan(processInstanceId, processId, processVersion, processState,
                parentProcessInstanceId, headerContext, null);
    }

    /**
     * Ends the process span with the given status.
     *
     * @param processInstanceId the process instance ID
     * @param statusCode the status code to set
     * @param description the status description (may be null)
     */
    public void endProcessSpan(String processInstanceId, StatusCode statusCode, String description) {
        Span span = activeProcessSpans.remove(processInstanceId);
        if (span != null) {
            if (description != null) {
                span.setStatus(statusCode, description);
            } else {
                span.setStatus(statusCode);
            }
            span.end();
            LOGGER.debug("Ended process span for {} with status {}", processInstanceId, statusCode);
        }
    }

    @Override
    public void cleanup() {
        int spanCount = activeProcessSpans.size();
        if (spanCount > 0) {
            LOGGER.debug("Cleaning up {} active process spans during shutdown", spanCount);
            activeProcessSpans.values().forEach(span -> {
                try {
                    span.end();
                } catch (Exception e) {
                    LOGGER.warn("Error ending process span during cleanup", e);
                }
            });
        }
        activeProcessSpans.clear();
    }
}
