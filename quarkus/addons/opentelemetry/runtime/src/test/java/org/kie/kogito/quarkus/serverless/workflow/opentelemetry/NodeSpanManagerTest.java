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
package org.kie.kogito.quarkus.serverless.workflow.opentelemetry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.kie.kogito.quarkus.serverless.workflow.opentelemetry.config.SonataFlowOtelConfig;

import jakarta.enterprise.inject.Instance;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.*;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.SONATAFLOW_PARENT_PROCESS_INSTANCE_ID;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.SONATAFLOW_TRANSACTION_ID;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.SONATAFLOW_WORKFLOW_STATE;
import static org.mockito.Mockito.*;

public class NodeSpanManagerTest {

    @AfterEach
    public void cleanup() {
        OtelContextHolder.clearRootContext("test-instance");
    }

    private SonataFlowOtelConfig createMockConfig(boolean enabled, boolean spanEnabled) {
        SonataFlowOtelConfig mockConfig = mock(SonataFlowOtelConfig.class);
        SonataFlowOtelConfig.SpanConfig mockSpanConfig = mock(SonataFlowOtelConfig.SpanConfig.class);

        when(mockConfig.enabled()).thenReturn(enabled);
        when(mockConfig.serviceName()).thenReturn("kogito-workflow-service");
        when(mockConfig.serviceVersion()).thenReturn("unknown");
        when(mockConfig.spans()).thenReturn(mockSpanConfig);
        when(mockSpanConfig.enabled()).thenReturn(spanEnabled);

        return mockConfig;
    }

    @SuppressWarnings("unchecked")
    private Instance<io.opentelemetry.api.trace.Tracer> createMockTracerInstance(io.opentelemetry.api.trace.Tracer tracer) {
        Instance<io.opentelemetry.api.trace.Tracer> mockInstance = mock(Instance.class);
        when(mockInstance.isResolvable()).thenReturn(tracer != null);
        when(mockInstance.get()).thenReturn(tracer);
        return mockInstance;
    }

    private io.opentelemetry.api.trace.SpanBuilder setupMockSpanBuilder(
            io.opentelemetry.api.trace.Tracer mockTracer,
            io.opentelemetry.api.trace.Span mockSpan) {

        io.opentelemetry.api.trace.SpanBuilder mockSpanBuilder = org.mockito.Mockito.mock(io.opentelemetry.api.trace.SpanBuilder.class, org.mockito.Mockito.RETURNS_SELF);

        org.mockito.Mockito.when(mockTracer.spanBuilder(org.mockito.ArgumentMatchers.anyString())).thenReturn(mockSpanBuilder);
        org.mockito.Mockito.when(mockSpanBuilder.startSpan()).thenReturn(mockSpan);

        io.opentelemetry.api.trace.SpanContext mockSpanContext = io.opentelemetry.api.trace.SpanContext.create(
                "0123456789abcdef0123456789abcdef",
                "0123456789abcdef",
                io.opentelemetry.api.trace.TraceFlags.getSampled(),
                io.opentelemetry.api.trace.TraceState.getDefault());
        org.mockito.Mockito.when(mockSpan.getSpanContext()).thenReturn(mockSpanContext);

        return mockSpanBuilder;
    }

    @Test
    public void shouldReturnNullWhenSpansDisabled() throws Exception {
        io.opentelemetry.api.trace.Tracer mockTracer = mock(io.opentelemetry.api.trace.Tracer.class);
        SonataFlowOtelConfig mockConfig = createMockConfig(true, false);
        NodeSpanManager spanManager = new NodeSpanManager(createMockTracerInstance(mockTracer), mockConfig);

        io.opentelemetry.api.trace.Span result = spanManager.createNodeSpan("test", "test", "1.0", "ACTIVE", "TestNode", null, null);

        org.junit.jupiter.api.Assertions.assertNull(result);
    }

    @Test
    public void shouldAddProcessEventToSpan() throws Exception {
        io.opentelemetry.api.trace.Tracer mockTracer = mock(io.opentelemetry.api.trace.Tracer.class);
        io.opentelemetry.api.trace.Span mockSpan = mock(io.opentelemetry.api.trace.Span.class);
        SonataFlowOtelConfig mockConfig = createMockConfig(true, true);

        setupMockSpanBuilder(mockTracer, mockSpan);

        NodeSpanManager spanManager = new NodeSpanManager(createMockTracerInstance(mockTracer), mockConfig);

        io.opentelemetry.api.trace.Span span = spanManager.createNodeSpan("test-instance", "test-process", "1.0", "ACTIVE", "TestNode", null, null);
        spanManager.addProcessEvent(span, "process.started", "Process execution started");

        verify(mockSpan).addEvent("process.started", io.opentelemetry.api.common.Attributes.of(
                EVENT_DESCRIPTION, "Process execution started"));
    }

    @Test
    public void shouldSetSpanStatusOnError() throws Exception {
        io.opentelemetry.api.trace.Tracer mockTracer = mock(io.opentelemetry.api.trace.Tracer.class);
        io.opentelemetry.api.trace.Span mockSpan = mock(io.opentelemetry.api.trace.Span.class);
        SonataFlowOtelConfig mockConfig = createMockConfig(true, true);

        setupMockSpanBuilder(mockTracer, mockSpan);

        NodeSpanManager spanManager = new NodeSpanManager(createMockTracerInstance(mockTracer), mockConfig);

        io.opentelemetry.api.trace.Span span = spanManager.createNodeSpan("test-instance", "test-process", "1.0", "ERROR", "TestNode", null, null);
        spanManager.setSpanError(span, new RuntimeException("Test error"), "Node execution failed");

        verify(mockSpan).setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, "Node execution failed");
        verify(mockSpan).recordException(org.mockito.ArgumentMatchers.any(RuntimeException.class));
    }

    @Test
    public void shouldCorrelateSpanWithHeaderContext() throws Exception {
        io.opentelemetry.api.trace.Tracer mockTracer = mock(io.opentelemetry.api.trace.Tracer.class);
        io.opentelemetry.api.trace.Span mockSpan = mock(io.opentelemetry.api.trace.Span.class);
        SonataFlowOtelConfig mockConfig = createMockConfig(true, true);

        setupMockSpanBuilder(mockTracer, mockSpan);

        NodeSpanManager spanManager = new NodeSpanManager(createMockTracerInstance(mockTracer), mockConfig);

        java.util.Map<String, String> headerContext = java.util.Map.of(
                "transaction.id", "txn-123",
                "tracker.user", "john.doe");

        io.opentelemetry.api.trace.Span span = spanManager.createNodeSpanWithContext(
                "test-instance", "test-process", "1.0", "ACTIVE", "TestNode", null, null, headerContext);

        verify(mockSpan).setAttribute(SONATAFLOW_TRANSACTION_ID, "txn-123");
        verify(mockSpan).setAttribute(org.mockito.ArgumentMatchers.eq("sonataflow.tracker.user"), org.mockito.ArgumentMatchers.eq("john.doe"));
    }

    @Test
    public void shouldUseCurrentContextForAllNodes() throws Exception {
        io.opentelemetry.api.trace.Tracer mockTracer = mock(io.opentelemetry.api.trace.Tracer.class);
        io.opentelemetry.api.trace.Span mockSpan = mock(io.opentelemetry.api.trace.Span.class);
        io.opentelemetry.api.trace.SpanBuilder mockSpanBuilder = setupMockSpanBuilder(mockTracer, mockSpan);
        SonataFlowOtelConfig mockConfig = createMockConfig(true, true);

        NodeSpanManager spanManager = new NodeSpanManager(createMockTracerInstance(mockTracer), mockConfig);

        spanManager.createNodeSpan("test-instance", "test-process", "1.0", "ACTIVE", "Node1", null, null);

        spanManager.createNodeSpan("test-instance", "test-process", "1.0", "ACTIVE", "Node2", null, null);

        verify(mockSpanBuilder, atLeastOnce()).setParent(io.opentelemetry.context.Context.current());
    }

    @Test
    public void shouldClearSpansOnProcessCompletion() throws Exception {
        io.opentelemetry.api.trace.Tracer mockTracer = mock(io.opentelemetry.api.trace.Tracer.class);
        io.opentelemetry.api.trace.Span mockSpan = mock(io.opentelemetry.api.trace.Span.class);
        setupMockSpanBuilder(mockTracer, mockSpan);
        SonataFlowOtelConfig mockConfig = createMockConfig(true, true);

        NodeSpanManager spanManager = new NodeSpanManager(createMockTracerInstance(mockTracer), mockConfig);

        spanManager.createNodeSpan("test-instance", "test-process", "1.0", "ACTIVE", "Node1", null, null);

        int activeSpansBeforeEnd = spanManager.getActiveScopeCount();
        org.junit.jupiter.api.Assertions.assertTrue(activeSpansBeforeEnd > 0, "Should have active spans");

        spanManager.endRemainingSpans("test-instance");

        int activeSpansAfterEnd = spanManager.getActiveScopeCount();
        org.junit.jupiter.api.Assertions.assertEquals(0, activeSpansAfterEnd, "All spans should be cleared on process completion");
    }

    @Test
    public void shouldCreateNodeSpanWithCorrectAttributes() throws Exception {
        io.opentelemetry.api.trace.Tracer mockTracer = mock(io.opentelemetry.api.trace.Tracer.class);
        io.opentelemetry.api.trace.Span mockSpan = mock(io.opentelemetry.api.trace.Span.class);
        SonataFlowOtelConfig mockConfig = createMockConfig(true, true);

        io.opentelemetry.api.trace.SpanBuilder mockSpanBuilder = setupMockSpanBuilder(mockTracer, mockSpan);

        NodeSpanManager spanManager = new NodeSpanManager(createMockTracerInstance(mockTracer), mockConfig);

        spanManager.createNodeSpan("test-instance", "test-process", "1.0", "ACTIVE", "TestNode", null, null);

        verify(mockSpanBuilder).setAttribute(SONATAFLOW_PROCESS_INSTANCE_NODE, "TestNode");
    }

    @Test
    public void shouldGetActiveNodeSpan() throws Exception {
        io.opentelemetry.api.trace.Tracer mockTracer = mock(io.opentelemetry.api.trace.Tracer.class);
        io.opentelemetry.api.trace.Span mockSpan = mock(io.opentelemetry.api.trace.Span.class);
        SonataFlowOtelConfig mockConfig = createMockConfig(true, true);

        setupMockSpanBuilder(mockTracer, mockSpan);

        NodeSpanManager spanManager = new NodeSpanManager(createMockTracerInstance(mockTracer), mockConfig);

        spanManager.createNodeSpan("test-instance", "test-process", "1.0", "ACTIVE", "TestNode", null, null);

        io.opentelemetry.api.trace.Span retrievedSpan = spanManager.getActiveNodeSpan("test-instance", "TestNode");

        assertNotNull(retrievedSpan, "Should retrieve active node span");
        org.junit.jupiter.api.Assertions.assertEquals(mockSpan, retrievedSpan, "Retrieved span should match created span");
    }

    @Test
    public void shouldBuildCorrectSpanKey() throws Exception {
        io.opentelemetry.api.trace.Tracer mockTracer = mock(io.opentelemetry.api.trace.Tracer.class);
        io.opentelemetry.api.trace.Span mockSpan = mock(io.opentelemetry.api.trace.Span.class);
        SonataFlowOtelConfig mockConfig = createMockConfig(true, true);

        setupMockSpanBuilder(mockTracer, mockSpan);

        NodeSpanManager spanManager = new NodeSpanManager(createMockTracerInstance(mockTracer), mockConfig);

        spanManager.createNodeSpan("test-instance-123", "test-process", "1.0", "ACTIVE", "Node-ABC", null, null);

        boolean hasActiveScope = spanManager.hasActiveScope("test-instance-123", "Node-ABC");
        org.junit.jupiter.api.Assertions.assertTrue(hasActiveScope, "Span key should be in format processInstanceId:nodeId");
    }

    @Test
    public void shouldCompleteNodeSpanByProcessAndNodeId() throws Exception {
        io.opentelemetry.api.trace.Tracer mockTracer = mock(io.opentelemetry.api.trace.Tracer.class);
        io.opentelemetry.api.trace.Span mockSpan = mock(io.opentelemetry.api.trace.Span.class);
        SonataFlowOtelConfig mockConfig = createMockConfig(true, true);

        setupMockSpanBuilder(mockTracer, mockSpan);

        NodeSpanManager spanManager = new NodeSpanManager(createMockTracerInstance(mockTracer), mockConfig);

        spanManager.createNodeSpan("test-instance", "test-process", "1.0", "ACTIVE", "TestNode", null, null);
        org.junit.jupiter.api.Assertions.assertEquals(1, spanManager.getActiveScopeCount(),
                "Should have one active scope after creating span");

        spanManager.completeNodeSpan("test-instance", "TestNode");

        verify(mockSpan).end();
        org.junit.jupiter.api.Assertions.assertEquals(0, spanManager.getActiveScopeCount(),
                "Should have no active scopes after completing span");
    }

    @Test
    public void shouldHandleCompleteNodeSpanForNonExistentSpan() throws Exception {
        io.opentelemetry.api.trace.Tracer mockTracer = mock(io.opentelemetry.api.trace.Tracer.class);
        SonataFlowOtelConfig mockConfig = createMockConfig(true, true);

        NodeSpanManager spanManager = new NodeSpanManager(createMockTracerInstance(mockTracer), mockConfig);

        spanManager.completeNodeSpan("non-existent-instance", "NonExistentNode");

        org.junit.jupiter.api.Assertions.assertEquals(0, spanManager.getActiveScopeCount(),
                "Should handle non-existent span gracefully");
    }

    @Test
    public void shouldSetStateMetadataAttributeWhenPresent() throws Exception {
        io.opentelemetry.api.trace.Tracer mockTracer = mock(io.opentelemetry.api.trace.Tracer.class);
        io.opentelemetry.api.trace.Span mockSpan = mock(io.opentelemetry.api.trace.Span.class);
        io.opentelemetry.api.trace.SpanBuilder mockSpanBuilder = setupMockSpanBuilder(mockTracer, mockSpan);
        SonataFlowOtelConfig mockConfig = createMockConfig(true, true);

        NodeSpanManager spanManager = new NodeSpanManager(createMockTracerInstance(mockTracer), mockConfig);

        spanManager.createNodeSpan("test-instance", "test-process", "1.0", "ACTIVE", "TestNode", "MyState", null);

        verify(mockSpanBuilder).setAttribute(SONATAFLOW_WORKFLOW_STATE, "MyState");
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void shouldNotSetStateMetadataAttributeWhenNullOrEmpty(String stateMetadata) throws Exception {
        io.opentelemetry.api.trace.Tracer mockTracer = mock(io.opentelemetry.api.trace.Tracer.class);
        io.opentelemetry.api.trace.Span mockSpan = mock(io.opentelemetry.api.trace.Span.class);
        io.opentelemetry.api.trace.SpanBuilder mockSpanBuilder = setupMockSpanBuilder(mockTracer, mockSpan);
        SonataFlowOtelConfig mockConfig = createMockConfig(true, true);

        NodeSpanManager spanManager = new NodeSpanManager(createMockTracerInstance(mockTracer), mockConfig);

        spanManager.createNodeSpan("test-instance", "test-process", "1.0", "ACTIVE", "TestNode", stateMetadata, null);

        verify(mockSpanBuilder, never()).setAttribute(org.mockito.ArgumentMatchers.eq(SONATAFLOW_WORKFLOW_STATE), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    public void shouldSetParentProcessInstanceIdWhenPresent() throws Exception {
        io.opentelemetry.api.trace.Tracer mockTracer = mock(io.opentelemetry.api.trace.Tracer.class);
        io.opentelemetry.api.trace.Span mockSpan = mock(io.opentelemetry.api.trace.Span.class);
        io.opentelemetry.api.trace.SpanBuilder mockSpanBuilder = setupMockSpanBuilder(mockTracer, mockSpan);
        SonataFlowOtelConfig mockConfig = createMockConfig(true, true);

        NodeSpanManager spanManager = new NodeSpanManager(createMockTracerInstance(mockTracer), mockConfig);

        spanManager.createNodeSpan("test-instance", "test-process", "1.0", "ACTIVE", "TestNode", null, "parent-123");

        verify(mockSpanBuilder).setAttribute(SONATAFLOW_PARENT_PROCESS_INSTANCE_ID, "parent-123");
    }

    @Test
    public void shouldNotSetParentProcessInstanceIdWhenNull() throws Exception {
        io.opentelemetry.api.trace.Tracer mockTracer = mock(io.opentelemetry.api.trace.Tracer.class);
        io.opentelemetry.api.trace.Span mockSpan = mock(io.opentelemetry.api.trace.Span.class);
        io.opentelemetry.api.trace.SpanBuilder mockSpanBuilder = setupMockSpanBuilder(mockTracer, mockSpan);
        SonataFlowOtelConfig mockConfig = createMockConfig(true, true);

        NodeSpanManager spanManager = new NodeSpanManager(createMockTracerInstance(mockTracer), mockConfig);

        spanManager.createNodeSpan("test-instance", "test-process", "1.0", "ACTIVE", "TestNode", null, null);

        verify(mockSpanBuilder, never()).setAttribute(org.mockito.ArgumentMatchers.eq(SONATAFLOW_PARENT_PROCESS_INSTANCE_ID), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    public void shouldUseHttpRequestSpanContextWhenRootContextMissing() throws Exception {
        io.opentelemetry.api.trace.Tracer mockTracer = mock(io.opentelemetry.api.trace.Tracer.class);
        io.opentelemetry.api.trace.Span mockSpan = mock(io.opentelemetry.api.trace.Span.class);
        io.opentelemetry.api.trace.SpanBuilder mockSpanBuilder = setupMockSpanBuilder(mockTracer, mockSpan);
        SonataFlowOtelConfig mockConfig = createMockConfig(true, true);

        io.opentelemetry.api.trace.SpanContext mockHttpSpanContext = io.opentelemetry.api.trace.SpanContext.create(
                "0123456789abcdef0123456789abcdef",
                "0123456789abcdef",
                io.opentelemetry.api.trace.TraceFlags.getSampled(),
                io.opentelemetry.api.trace.TraceState.getDefault());

        io.opentelemetry.api.trace.Span httpSpan = io.opentelemetry.api.trace.Span.wrap(mockHttpSpanContext);
        io.opentelemetry.context.Context httpContext = io.opentelemetry.context.Context.current().with(httpSpan);
        OtelContextHolder.setHttpRequestContext(httpContext);

        try {
            NodeSpanManager spanManager = new NodeSpanManager(createMockTracerInstance(mockTracer), mockConfig);

            spanManager.createNodeSpan("new-instance-no-root", "test-process", "1.0", "ACTIVE", "TestNode", null, null);

            org.mockito.ArgumentCaptor<io.opentelemetry.context.Context> contextCaptor =
                    org.mockito.ArgumentCaptor.forClass(io.opentelemetry.context.Context.class);
            verify(mockSpanBuilder).setParent(contextCaptor.capture());

            io.opentelemetry.context.Context capturedContext = contextCaptor.getValue();
            io.opentelemetry.api.trace.Span parentSpan = io.opentelemetry.api.trace.Span.fromContext(capturedContext);
            org.junit.jupiter.api.Assertions.assertEquals(mockHttpSpanContext.getTraceId(), parentSpan.getSpanContext().getTraceId());
            org.junit.jupiter.api.Assertions.assertEquals(mockHttpSpanContext.getSpanId(), parentSpan.getSpanContext().getSpanId());
        } finally {
            OtelContextHolder.clearHttpRequestContext();
            OtelContextHolder.clearRootContext("new-instance-no-root");
        }
    }

    @Test
    public void shouldPreferStoredRootSpanContextOverHttpContext() throws Exception {
        io.opentelemetry.api.trace.Tracer mockTracer = mock(io.opentelemetry.api.trace.Tracer.class);
        io.opentelemetry.api.trace.Span mockSpan = mock(io.opentelemetry.api.trace.Span.class);
        io.opentelemetry.api.trace.SpanBuilder mockSpanBuilder = setupMockSpanBuilder(mockTracer, mockSpan);
        SonataFlowOtelConfig mockConfig = createMockConfig(true, true);

        io.opentelemetry.api.trace.SpanContext storedSpanContext = io.opentelemetry.api.trace.SpanContext.create(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "bbbbbbbbbbbbbbbb",
                io.opentelemetry.api.trace.TraceFlags.getSampled(),
                io.opentelemetry.api.trace.TraceState.getDefault());
        OtelContextHolder.setRootSpanContext("test-instance", storedSpanContext);

        io.opentelemetry.api.trace.SpanContext httpSpanContext = io.opentelemetry.api.trace.SpanContext.create(
                "cccccccccccccccccccccccccccccccc",
                "dddddddddddddddd",
                io.opentelemetry.api.trace.TraceFlags.getSampled(),
                io.opentelemetry.api.trace.TraceState.getDefault());
        io.opentelemetry.api.trace.Span httpSpan = io.opentelemetry.api.trace.Span.wrap(httpSpanContext);
        io.opentelemetry.context.Context httpContext = io.opentelemetry.context.Context.current().with(httpSpan);
        OtelContextHolder.setHttpRequestContext(httpContext);

        try {
            NodeSpanManager spanManager = new NodeSpanManager(createMockTracerInstance(mockTracer), mockConfig);

            spanManager.createNodeSpan("test-instance", "test-process", "1.0", "ACTIVE", "TestNode", null, null);

            org.mockito.ArgumentCaptor<io.opentelemetry.context.Context> contextCaptor =
                    org.mockito.ArgumentCaptor.forClass(io.opentelemetry.context.Context.class);
            verify(mockSpanBuilder).setParent(contextCaptor.capture());

            io.opentelemetry.context.Context capturedContext = contextCaptor.getValue();
            io.opentelemetry.api.trace.Span parentSpan = io.opentelemetry.api.trace.Span.fromContext(capturedContext);
            org.junit.jupiter.api.Assertions.assertEquals(storedSpanContext.getTraceId(), parentSpan.getSpanContext().getTraceId());
            org.junit.jupiter.api.Assertions.assertEquals(storedSpanContext.getSpanId(), parentSpan.getSpanContext().getSpanId());
        } finally {
            OtelContextHolder.clearHttpRequestContext();
            OtelContextHolder.clearRootContext("test-instance");
        }
    }

    @Test
    public void shouldReturnNullWhenGetLastActiveSpanCalledWithNoSpans() {
        io.opentelemetry.api.trace.Tracer mockTracer = mock(io.opentelemetry.api.trace.Tracer.class);
        SonataFlowOtelConfig mockConfig = createMockConfig(true, true);
        NodeSpanManager spanManager = new NodeSpanManager(createMockTracerInstance(mockTracer), mockConfig);

        org.junit.jupiter.api.Assertions.assertNull(spanManager.getLastActiveSpan("non-existent-instance"),
                "Should return null when no spans exist for the process instance");
    }

    @Test
    public void shouldReturnNullWhenGetAnyActiveSpanCalledWithNoSpans() {
        io.opentelemetry.api.trace.Tracer mockTracer = mock(io.opentelemetry.api.trace.Tracer.class);
        SonataFlowOtelConfig mockConfig = createMockConfig(true, true);
        NodeSpanManager spanManager = new NodeSpanManager(createMockTracerInstance(mockTracer), mockConfig);

        org.junit.jupiter.api.Assertions.assertNull(spanManager.getAnyActiveSpan("non-existent-instance"),
                "Should return null when no spans exist for the process instance");
    }

    @Test
    public void shouldEndRemainingSpansWithErrorStatus() throws Exception {
        io.opentelemetry.api.trace.Tracer mockTracer = mock(io.opentelemetry.api.trace.Tracer.class);
        io.opentelemetry.api.trace.Span mockSpan = mock(io.opentelemetry.api.trace.Span.class);
        setupMockSpanBuilder(mockTracer, mockSpan);
        SonataFlowOtelConfig mockConfig = createMockConfig(true, true);

        NodeSpanManager spanManager = new NodeSpanManager(createMockTracerInstance(mockTracer), mockConfig);
        spanManager.createNodeSpan("test-instance", "test-process", "1.0", "ACTIVE", "Node1", null, null);

        spanManager.endRemainingSpansWithError("test-instance");

        verify(mockSpan).setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, "Process failed with error");
        verify(mockSpan).end();
    }

    @Test
    public void shouldReturnNullWhenTracerNotAvailable() {
        SonataFlowOtelConfig mockConfig = createMockConfig(true, true);
        NodeSpanManager spanManager = new NodeSpanManager(createMockTracerInstance(null), mockConfig);

        io.opentelemetry.api.trace.Span result = spanManager.createNodeSpan(
                "test-instance", "test-process", "1.0", "ACTIVE", "TestNode", null, null);

        org.junit.jupiter.api.Assertions.assertNull(result,
                "Should return null when Tracer is not available");
    }

    @Test
    public void shouldHandleGracefullyWhenTracerNotAvailable() {
        SonataFlowOtelConfig mockConfig = createMockConfig(true, true);
        NodeSpanManager spanManager = new NodeSpanManager(createMockTracerInstance(null), mockConfig);

        spanManager.endRemainingSpans("test-instance");
        spanManager.endRemainingSpansWithError("test-instance");
        spanManager.completeNodeSpan("test-instance", "TestNode");

        org.junit.jupiter.api.Assertions.assertEquals(0, spanManager.getActiveScopeCount(),
                "Should handle all operations gracefully when Tracer is not available");
    }

}
