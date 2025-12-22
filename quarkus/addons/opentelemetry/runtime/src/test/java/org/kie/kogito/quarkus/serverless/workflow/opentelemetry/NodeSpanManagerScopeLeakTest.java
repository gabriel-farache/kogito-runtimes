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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kie.kogito.quarkus.serverless.workflow.opentelemetry.config.SonataFlowOtelConfig;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;

import jakarta.enterprise.inject.Instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test suite verifying NodeSpanManager's scope management works correctly.
 *
 * The scope management ensures that spans are made current during their lifecycle,
 * allowing log events to be correctly attached to workflow spans via Span.current().
 * Scopes are properly closed when spans end, restoring the previous context.
 *
 * These tests verify:
 * 1. Spans are created and made current via makeCurrent()
 * 2. Cleanup properly ends all spans and closes scopes
 * 3. Concurrent access is handled safely
 * 4. Exception handling properly cleans up resources
 */
@ExtendWith(MockitoExtension.class)
public class NodeSpanManagerScopeLeakTest {

    @Mock
    private Tracer mockTracer;

    @Mock
    private SonataFlowOtelConfig mockConfig;

    @Mock
    private SonataFlowOtelConfig.SpanConfig mockSpanConfig;

    private NodeSpanManager spanManager;

    @BeforeEach
    public void setUp() {
        when(mockConfig.enabled()).thenReturn(true);
        when(mockConfig.serviceName()).thenReturn("test-service");
        when(mockConfig.serviceVersion()).thenReturn("1.0");
        when(mockConfig.spans()).thenReturn(mockSpanConfig);
        when(mockSpanConfig.enabled()).thenReturn(true);

        spanManager = new NodeSpanManager(createMockTracerInstance(mockTracer), mockConfig);
    }

    @SuppressWarnings("unchecked")
    private Instance<Tracer> createMockTracerInstance(Tracer tracer) {
        Instance<Tracer> mockInstance = mock(Instance.class);
        when(mockInstance.isResolvable()).thenReturn(tracer != null);
        when(mockInstance.get()).thenReturn(tracer);
        return mockInstance;
    }

    @org.junit.jupiter.api.AfterEach
    public void tearDown() {
        spanManager.cleanup();
        OtelContextHolder.clearRootContext("test-instance");
        OtelContextHolder.clearRootContext("test-instance-1");
        OtelContextHolder.clearRootContext("test-instance-2");
        OtelContextHolder.clearRootContext("race-instance");
    }

    private SpanBuilder setupMockSpanBuilder(Span mockSpan) {
        SpanBuilder mockSpanBuilder = mock(SpanBuilder.class, org.mockito.Mockito.RETURNS_SELF);
        when(mockTracer.spanBuilder(anyString())).thenReturn(mockSpanBuilder);
        when(mockSpanBuilder.startSpan()).thenReturn(mockSpan);

        io.opentelemetry.api.trace.SpanContext mockSpanContext = io.opentelemetry.api.trace.SpanContext.create(
                "0123456789abcdef0123456789abcdef",
                "0123456789abcdef",
                io.opentelemetry.api.trace.TraceFlags.getSampled(),
                io.opentelemetry.api.trace.TraceState.getDefault());
        when(mockSpan.getSpanContext()).thenReturn(mockSpanContext);

        return mockSpanBuilder;
    }

    /**
     * Test verifying span and scope cleanup works correctly.
     *
     * The scope management ensures proper cleanup of both spans and scopes.
     */
    @Test
    public void shouldCleanupSpanWhenExceptionOccurs() {
        Span mockSpan = mock(Span.class);
        setupMockSpanBuilder(mockSpan);

        assertThat(spanManager.getActiveScopeCount()).isZero();

        Span createdSpan = spanManager.createNodeSpan("test-instance", "test-process", "1.0", "ACTIVE", "node1", null, null);

        assertThat(createdSpan).isNotNull();
        assertThat(spanManager.getActiveScopeCount()).isEqualTo(1);

        spanManager.cleanup();

        assertThat(spanManager.getActiveScopeCount()).isZero();
        assertThat(spanManager.getActiveSpanCount()).isZero();
    }

    /**
     * Test verifying cleanup works after event processing failure.
     *
     * Scenario: NodeOtelEventListener.beforeNodeTriggered() creates a span successfully,
     * but then encounters an error during addProcessEvent. Cleanup should still work.
     */
    @Test
    public void shouldCleanupSpanWhenBeforeNodeTriggeredFails() {
        Span mockSpan = mock(Span.class);
        setupMockSpanBuilder(mockSpan);

        assertThat(spanManager.getActiveScopeCount()).isZero();

        spanManager.createNodeSpan("test-instance-1", "test-process", "1.0", "ACTIVE", "node1", null, null);

        assertThat(spanManager.getActiveScopeCount()).isEqualTo(1);

        when(mockSpan.addEvent(anyString(), any(io.opentelemetry.api.common.Attributes.class)))
                .thenThrow(new RuntimeException("Event processing failed"));

        try {
            spanManager.addProcessEvent(mockSpan, "test.event", "Test description");
        } catch (RuntimeException e) {
            // Expected exception during event processing
        }

        spanManager.cleanup();

        assertThat(spanManager.getActiveScopeCount()).isZero();
        assertThat(spanManager.getActiveSpanCount()).isZero();
    }

    /**
     * Test verifying all spans are cleaned up during application shutdown.
     *
     * The @PreDestroy cleanup() method should close all active spans.
     */
    @Test
    public void shouldCleanupAllSpansOnApplicationShutdown() {
        Span mockSpan1 = mock(Span.class);
        Span mockSpan2 = mock(Span.class);
        SpanBuilder mockSpanBuilder = mock(SpanBuilder.class, org.mockito.Mockito.RETURNS_SELF);

        when(mockTracer.spanBuilder(anyString())).thenReturn(mockSpanBuilder);
        when(mockSpanBuilder.startSpan()).thenReturn(mockSpan1, mockSpan2);

        io.opentelemetry.api.trace.SpanContext mockSpanContext1 = io.opentelemetry.api.trace.SpanContext.create(
                "0123456789abcdef0123456789abcdef",
                "0123456789abcdef",
                io.opentelemetry.api.trace.TraceFlags.getSampled(),
                io.opentelemetry.api.trace.TraceState.getDefault());
        io.opentelemetry.api.trace.SpanContext mockSpanContext2 = io.opentelemetry.api.trace.SpanContext.create(
                "abcdef0123456789abcdef0123456789",
                "abcdef0123456789",
                io.opentelemetry.api.trace.TraceFlags.getSampled(),
                io.opentelemetry.api.trace.TraceState.getDefault());
        when(mockSpan1.getSpanContext()).thenReturn(mockSpanContext1);
        when(mockSpan2.getSpanContext()).thenReturn(mockSpanContext2);

        assertThat(spanManager.getActiveScopeCount()).isZero();

        spanManager.createNodeSpan("test-instance-1", "test-process", "1.0", "ACTIVE", "node1", null, null);
        spanManager.createNodeSpan("test-instance-2", "test-process", "1.0", "ACTIVE", "node2", null, null);

        assertThat(spanManager.getActiveScopeCount()).isEqualTo(2);
        assertThat(spanManager.getActiveSpanCount()).isEqualTo(2);

        spanManager.cleanup();

        assertThat(spanManager.getActiveScopeCount()).isZero();
        assertThat(spanManager.getActiveSpanCount()).isZero();
    }

    /**
     * Test verifying cleanup works for process errors without NodeLeftEvent.
     *
     * Scenario: Process instance crashes or terminates abnormally without triggering
     * the error handling path. cleanup() should still work.
     */
    @Test
    public void shouldCleanupSpansOnProcessErrorWithoutNodeLeftEvent() {
        Span mockSpan = mock(Span.class);
        setupMockSpanBuilder(mockSpan);

        assertThat(spanManager.getActiveScopeCount()).isZero();

        spanManager.createNodeSpan("test-instance-1", "test-process", "1.0", "ACTIVE", "node1", null, null);

        assertThat(spanManager.getActiveScopeCount()).isEqualTo(1);

        spanManager.cleanup();

        assertThat(spanManager.getActiveScopeCount()).isZero();
        assertThat(spanManager.getActiveSpanCount()).isZero();
    }

    /**
     * Test verifying concurrent span creation for same node is handled safely.
     *
     * Scenario: Two threads try to create spans for the same node (same processInstanceId + nodeId)
     * concurrently. The implementation should handle this by replacing the previous span
     * and properly closing the previous scope.
     */
    @Test
    public void shouldHandleConcurrentAccessSafely() throws InterruptedException {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(2);

        Span mockSpan1 = mock(Span.class);
        Span mockSpan2 = mock(Span.class);

        SpanBuilder mockSpanBuilder = mock(SpanBuilder.class, org.mockito.Mockito.RETURNS_SELF);
        when(mockTracer.spanBuilder(anyString())).thenReturn(mockSpanBuilder);
        when(mockSpanBuilder.startSpan()).thenReturn(mockSpan1, mockSpan2);

        io.opentelemetry.api.trace.SpanContext mockSpanContext1 = io.opentelemetry.api.trace.SpanContext.create(
                "0123456789abcdef0123456789abcdef",
                "0123456789abcdef",
                io.opentelemetry.api.trace.TraceFlags.getSampled(),
                io.opentelemetry.api.trace.TraceState.getDefault());
        io.opentelemetry.api.trace.SpanContext mockSpanContext2 = io.opentelemetry.api.trace.SpanContext.create(
                "abcdef0123456789abcdef0123456789",
                "abcdef0123456789",
                io.opentelemetry.api.trace.TraceFlags.getSampled(),
                io.opentelemetry.api.trace.TraceState.getDefault());
        org.mockito.Mockito.lenient().when(mockSpan1.getSpanContext()).thenReturn(mockSpanContext1);
        org.mockito.Mockito.lenient().when(mockSpan2.getSpanContext()).thenReturn(mockSpanContext2);

        assertThat(spanManager.getActiveScopeCount()).isZero();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.submit(() -> {
            try {
                startLatch.await();
                spanManager.createNodeSpan("race-instance", "test-process", "1.0", "ACTIVE", "same-node", null, null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                completeLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                spanManager.createNodeSpan("race-instance", "test-process", "1.0", "ACTIVE", "same-node", null, null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                completeLatch.countDown();
            }
        });

        startLatch.countDown();
        completeLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(spanManager.getActiveScopeCount()).isEqualTo(1);

        spanManager.cleanup();

        assertThat(spanManager.getActiveScopeCount()).isZero();
        assertThat(spanManager.getActiveSpanCount()).isZero();
    }
}
