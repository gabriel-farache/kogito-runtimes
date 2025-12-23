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
package org.kie.kogito.quarkus.serverless.workflow.opentelemetry.mode.nodespan;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.kie.kogito.quarkus.serverless.workflow.opentelemetry.AbstractSpanManager;
import org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes;
import org.kie.kogito.quarkus.serverless.workflow.opentelemetry.common.OtelContextHolder;
import org.kie.kogito.quarkus.serverless.workflow.opentelemetry.config.SonataFlowOtelConfig;
import org.kie.kogito.quarkus.serverless.workflow.opentelemetry.util.SpanAttributeApplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.SpanNames;

@ApplicationScoped
public class NodeSpanManager extends AbstractSpanManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(NodeSpanManager.class);
    private final Map<String, ScopeManager> activeScopeManagers = new ConcurrentHashMap<>();
    private final Map<String, Span> lastActiveNodeSpan = new ConcurrentHashMap<>();

    protected NodeSpanManager() {
        super();
    }

    @Inject
    public NodeSpanManager(Tracer tracer, SonataFlowOtelConfig config) {
        super(tracer, config);
    }

    record ScopeManager(Span span, Scope scope, String spanKey) implements AutoCloseable {

        void endWithStatus(StatusCode statusCode, String description) {
            try {
                if (description != null) {
                    span.setStatus(statusCode, description);
                } else {
                    span.setStatus(statusCode);
                }
                span.end();
                closeScope();
            } catch (Exception e) {
                LOGGER.error("Error ending span for {}", spanKey, e);
                close();
            }
        }

        private void closeScope() {
            if (scope != null) {
                try {
                    scope.close();
                } catch (Exception e) {
                    LOGGER.debug("Error closing scope for {}", spanKey, e);
                }
            }
        }

        @Override
        public void close() {
            if (span != null) {
                span.end();
            }
            closeScope();
        }
    }

    private void registerScopeManager(ScopeManager scopeManager, String spanKey) {
        ScopeManager previousManager = activeScopeManagers.put(spanKey, scopeManager);
        if (previousManager != null) {
            previousManager.close();
            LOGGER.debug("Replaced previous span for {}", spanKey);
        }
        LOGGER.debug("Registered span for {}", spanKey);
    }

    public Span getLastActiveSpan(String processInstanceId) {
        return lastActiveNodeSpan.get(processInstanceId);
    }

    public Span getAnyActiveSpan(String processInstanceId) {
        return activeScopeManagers.entrySet().stream()
                .filter(forProcessInstance(processInstanceId))
                .map(entry -> entry.getValue().span())
                .findFirst()
                .orElse(null);
    }

    public void endRemainingSpans(String processInstanceId) {
        endRemainingSpansWithStatus(processInstanceId, StatusCode.OK, null);
    }

    public void endRemainingSpansWithError(String processInstanceId) {
        endRemainingSpansWithStatus(processInstanceId, StatusCode.ERROR, "Process failed with error");
    }

    private void endRemainingSpansWithStatus(String processInstanceId, StatusCode statusCode, String description) {
        String nodePrefix = processInstanceId + ":";
        activeScopeManagers.entrySet().stream()
                .filter(forProcessInstance(processInstanceId))
                .forEach(entry -> {
                    ScopeManager manager = entry.getValue();
                    String spanKey = manager.spanKey();

                    String nodeId = spanKey.substring(nodePrefix.length());
                    String eventName = SonataFlowOtelAttributes.Events.NODE_COMPLETED;
                    String eventDescription = SonataFlowOtelAttributes.EventDescriptions.NODE_COMPLETED_PREFIX + nodeId;

                    addProcessEvent(manager.span, eventName, eventDescription);
                    manager.endWithStatus(statusCode, description);
                    LOGGER.debug("Ended span for {} with status {}", spanKey, statusCode);
                });

        activeScopeManagers.entrySet().removeIf(forProcessInstance(processInstanceId));
        lastActiveNodeSpan.remove(processInstanceId);
        OtelContextHolder.clearRootContext(processInstanceId);
    }

    private Predicate<Map.Entry<String, ScopeManager>> forProcessInstance(String processInstanceId) {
        String prefix = processInstanceId + ":";
        return entry -> entry.getKey().startsWith(prefix);
    }

    public Span createNodeSpan(String processInstanceId, String processId, String processVersion,
            String processState, String nodeId, String stateName, String parentProcessInstanceId) {
        if (!isSpanCreationEnabled()) {
            LOGGER.debug("Span creation disabled");
            return null;
        }

        ScopeManager scopeManager = null;
        String spanKey = null;
        try {
            Context parentContext = getOrCaptureRootContext(processInstanceId);

            spanKey = buildSpanKey(processInstanceId, nodeId);
            Span span = buildSpan(processInstanceId, processId, processVersion, processState, nodeId, stateName, parentProcessInstanceId, parentContext);

            Scope scope = span.makeCurrent();
            scopeManager = new ScopeManager(span, scope, spanKey);
            registerScopeManager(scopeManager, spanKey);

            lastActiveNodeSpan.put(processInstanceId, span);
            return span;
        } catch (Exception e) {
            if (scopeManager != null) {
                scopeManager.close();
                if (spanKey != null) {
                    activeScopeManagers.remove(spanKey);
                    lastActiveNodeSpan.remove(processInstanceId);
                }
            }
            LOGGER.error("Failed to create node span for {}:{}", processInstanceId, nodeId, e);
            return null;
        }
    }

    private Context getOrCaptureRootContext(String processInstanceId) {
        Context rootContext = OtelContextHolder.getRootContext(processInstanceId);
        if (rootContext != null) {
            LOGGER.debug("Using stored root context for process {}", processInstanceId);
            return rootContext;
        }

        Context httpContext = OtelContextHolder.getHttpRequestContext();
        if (httpContext != null) {
            OtelContextHolder.setRootContext(processInstanceId, httpContext);
            LOGGER.debug("Using HTTP request context for process {} (restart-safe)", processInstanceId);
            return httpContext;
        }

        Context currentContext = Context.current();
        OtelContextHolder.setRootContext(processInstanceId, currentContext);
        LOGGER.debug("Captured and stored root context for first node in process {}", processInstanceId);
        return currentContext;
    }

    private String buildSpanKey(String processInstanceId, String nodeId) {
        return processInstanceId + ":" + nodeId;
    }

    private Span buildSpan(String processInstanceId, String processId, String processVersion,
            String processState, String nodeId, String stateName, String parentProcessInstanceId, Context parentContext) {
        String spanName = SpanNames.createProcessSpanName(processId);

        var spanBuilder = tracer.spanBuilder(spanName)
                .setParent(parentContext)
                .setSpanKind(SpanKind.INTERNAL);

        SpanAttributeApplier.applyCommonAttributes(spanBuilder, processInstanceId, processId, processVersion, processState, config);
        SpanAttributeApplier.applyNodeAttribute(spanBuilder, nodeId);
        SpanAttributeApplier.applyOptionalWorkflowState(spanBuilder, stateName);
        SpanAttributeApplier.applyOptionalParentProcessId(spanBuilder, parentProcessInstanceId);

        return spanBuilder.startSpan();
    }

    public Span createNodeSpanWithContext(String processInstanceId, String processId, String processVersion,
            String processState, String nodeId, String stateName, String parentProcessInstanceId, Map<String, String> headerContext) {
        Span span = createNodeSpan(processInstanceId, processId, processVersion, processState, nodeId, stateName, parentProcessInstanceId);

        SpanAttributeApplier.applyHeaderContext(span, headerContext, processInstanceId);

        return span;
    }

    public Span getActiveNodeSpan(String processInstanceId, String nodeId) {
        String spanKey = buildSpanKey(processInstanceId, nodeId);
        ScopeManager manager = activeScopeManagers.get(spanKey);
        return manager != null ? manager.span() : null;
    }

    @VisibleForTesting
    boolean hasActiveScope(String processInstanceId, String nodeId) {
        String spanKey = buildSpanKey(processInstanceId, nodeId);
        return activeScopeManagers.containsKey(spanKey);
    }

    public void completeNodeSpan(String processInstanceId, String nodeId) {
        String spanKey = buildSpanKey(processInstanceId, nodeId);

        ScopeManager manager = activeScopeManagers.remove(spanKey);
        if (manager != null) {
            manager.endWithStatus(StatusCode.OK, null);
            LOGGER.debug("Completed span for {}", spanKey);
        }
    }

    @VisibleForTesting
    int getActiveScopeCount() {
        return activeScopeManagers.size();
    }

    @VisibleForTesting
    int getActiveSpanCount() {
        return lastActiveNodeSpan.size();
    }

    @PreDestroy
    @Override
    public void cleanup() {
        int spanCount = activeScopeManagers.size();
        if (spanCount > 0) {
            LOGGER.debug("Cleaning up {} active spans during shutdown", spanCount);
            activeScopeManagers.values().forEach(manager -> {
                try {
                    manager.close();
                } catch (Exception e) {
                    LOGGER.warn("Error ending span for {}", manager.spanKey(), e);
                }
            });
        }

        activeScopeManagers.clear();
        lastActiveNodeSpan.clear();
    }
}
