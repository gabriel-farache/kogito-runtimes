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
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kie.kogito.quarkus.serverless.workflow.opentelemetry.common.OtelContextHolder;
import org.kie.kogito.quarkus.serverless.workflow.opentelemetry.config.SonataFlowOtelConfig;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.EVENT_DESCRIPTION;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.SERVICE_NAME;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.SERVICE_VERSION;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.SONATAFLOW_PARENT_PROCESS_INSTANCE_ID;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.SONATAFLOW_PROCESS_ID;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.SONATAFLOW_PROCESS_INSTANCE_ID;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.SONATAFLOW_PROCESS_INSTANCE_STATE;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.SONATAFLOW_PROCESS_VERSION;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.SONATAFLOW_TRANSACTION_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ProcessSpanManagerTest {

    @Mock
    private Tracer tracer;

    @Mock
    private SonataFlowOtelConfig config;

    @Mock
    private SonataFlowOtelConfig.SpanConfig spanConfig;

    @Mock
    private Span mockSpan;

    @Mock
    private SpanBuilder mockSpanBuilder;

    private ProcessSpanManager spanManager;

    @BeforeEach
    public void setUp() {
        when(config.spans()).thenReturn(spanConfig);
        when(config.serviceName()).thenReturn("test-service");
        when(config.serviceVersion()).thenReturn("1.0.0");
        when(spanConfig.enabled()).thenReturn(true);

        when(mockSpanBuilder.setParent(any(Context.class))).thenReturn(mockSpanBuilder);
        when(mockSpanBuilder.setSpanKind(any(SpanKind.class))).thenReturn(mockSpanBuilder);
        when(mockSpanBuilder.setAttribute(any(io.opentelemetry.api.common.AttributeKey.class), anyString()))
                .thenReturn(mockSpanBuilder);
        when(mockSpanBuilder.setStartTimestamp(any(Instant.class))).thenReturn(mockSpanBuilder);
        when(mockSpanBuilder.startSpan()).thenReturn(mockSpan);
        when(tracer.spanBuilder(anyString())).thenReturn(mockSpanBuilder);

        spanManager = new ProcessSpanManager(tracer, config);
    }

    @AfterEach
    public void tearDown() {
        spanManager.cleanup();
        OtelContextHolder.clearHttpRequestContext();
    }

    @Test
    public void shouldCreateProcessSpanWithBasicAttributes() {
        Span span = spanManager.createProcessSpan(
                "instance-1", "my-process", "1.0", "ACTIVE", null, null);

        assertNotNull(span);
        verify(mockSpanBuilder).setAttribute(SONATAFLOW_PROCESS_INSTANCE_ID, "instance-1");
        verify(mockSpanBuilder).setAttribute(SONATAFLOW_PROCESS_ID, "my-process");
        verify(mockSpanBuilder).setAttribute(SONATAFLOW_PROCESS_VERSION, "1.0");
        verify(mockSpanBuilder).setAttribute(SONATAFLOW_PROCESS_INSTANCE_STATE, "ACTIVE");
        verify(mockSpanBuilder).setAttribute(SERVICE_NAME, "test-service");
        verify(mockSpanBuilder).setAttribute(SERVICE_VERSION, "1.0.0");
        verify(mockSpan).setAttribute(SONATAFLOW_TRANSACTION_ID, "instance-1");
    }

    @Test
    public void shouldReturnNullWhenSpansDisabled() {
        when(spanConfig.enabled()).thenReturn(false);
        spanManager = new ProcessSpanManager(tracer, config);

        Span span = spanManager.createProcessSpan(
                "instance-1", "my-process", "1.0", "ACTIVE", null, null);

        assertNull(span);
        verify(tracer, never()).spanBuilder(anyString());
    }

    @Test
    public void shouldSetParentProcessInstanceIdWhenProvided() {
        Span span = spanManager.createProcessSpan(
                "instance-1", "my-process", "1.0", "ACTIVE", "parent-1", null);

        assertNotNull(span);
        verify(mockSpanBuilder).setAttribute(SONATAFLOW_PARENT_PROCESS_INSTANCE_ID, "parent-1");
    }

    @Test
    public void shouldNotSetParentProcessInstanceIdWhenNull() {
        spanManager.createProcessSpan(
                "instance-1", "my-process", "1.0", "ACTIVE", null, null);

        verify(mockSpanBuilder, never()).setAttribute(
                eq(SONATAFLOW_PARENT_PROCESS_INSTANCE_ID), anyString());
    }

    @Test
    public void shouldNotSetParentProcessInstanceIdWhenEmpty() {
        spanManager.createProcessSpan(
                "instance-1", "my-process", "1.0", "ACTIVE", "", null);

        verify(mockSpanBuilder, never()).setAttribute(
                eq(SONATAFLOW_PARENT_PROCESS_INSTANCE_ID), anyString());
    }

    @Test
    public void shouldUseTransactionIdFromHeaderContext() {
        Map<String, String> headerContext = new HashMap<>();
        headerContext.put("transaction.id", "txn-123");

        spanManager.createProcessSpan(
                "instance-1", "my-process", "1.0", "ACTIVE", null, headerContext);

        verify(mockSpan).setAttribute(SONATAFLOW_TRANSACTION_ID, "txn-123");
    }

    @Test
    public void shouldUseProcessInstanceIdAsTransactionIdWhenNotInHeaders() {
        spanManager.createProcessSpan(
                "instance-1", "my-process", "1.0", "ACTIVE", null, null);

        verify(mockSpan).setAttribute(SONATAFLOW_TRANSACTION_ID, "instance-1");
    }

    @Test
    public void shouldApplyTrackerAttributesFromHeaderContext() {
        Map<String, String> headerContext = new HashMap<>();
        headerContext.put("tracker.user", "john.doe");
        headerContext.put("tracker.request.id", "req-456");

        spanManager.createProcessSpan(
                "instance-1", "my-process", "1.0", "ACTIVE", null, headerContext);

        verify(mockSpan).setAttribute("sonataflow.tracker.user", "john.doe");
        verify(mockSpan).setAttribute("sonataflow.tracker.request.id", "req-456");
    }

    @Test
    public void shouldEndProcessSpanWithOkStatus() {
        spanManager.createProcessSpan(
                "instance-1", "my-process", "1.0", "ACTIVE", null, null);

        spanManager.endProcessSpan("instance-1", StatusCode.OK, null);

        verify(mockSpan).setStatus(StatusCode.OK);
        verify(mockSpan).end();
    }

    @Test
    public void shouldEndProcessSpanWithErrorStatusAndDescription() {
        spanManager.createProcessSpan(
                "instance-1", "my-process", "1.0", "ACTIVE", null, null);

        spanManager.endProcessSpan("instance-1", StatusCode.ERROR, "Process failed");

        verify(mockSpan).setStatus(StatusCode.ERROR, "Process failed");
        verify(mockSpan).end();
    }

    @Test
    public void shouldHandleEndOfNonExistentSpan() {
        spanManager.endProcessSpan("non-existent", StatusCode.OK, null);

        verify(mockSpan, never()).end();
    }

    @Test
    public void shouldAddProcessEventWithDescription() {
        spanManager.addProcessEvent(mockSpan, "test.event", "Test description");

        verify(mockSpan).addEvent("test.event", Attributes.of(EVENT_DESCRIPTION, "Test description"));
    }

    @Test
    public void shouldAddProcessEventWithoutDescription() {
        spanManager.addProcessEvent(mockSpan, "test.event", (String) null);

        verify(mockSpan).addEvent("test.event");
    }

    @Test
    public void shouldHandleAddProcessEventWithNullSpan() {
        spanManager.addProcessEvent(null, "test.event", "Test description");

        verify(mockSpan, never()).addEvent(anyString(), any(Attributes.class));
    }

    @Test
    public void shouldAddProcessEventWithAttributes() {
        Attributes attrs = Attributes.of(EVENT_DESCRIPTION, "custom");

        spanManager.addProcessEvent(mockSpan, "test.event", attrs);

        verify(mockSpan).addEvent("test.event", attrs);
    }

    @Test
    public void shouldSetSpanError() {
        RuntimeException exception = new RuntimeException("Test error");

        spanManager.setSpanError(mockSpan, exception, "Error occurred");

        verify(mockSpan).setStatus(StatusCode.ERROR, "Error occurred");
        verify(mockSpan).recordException(exception);
    }

    @Test
    public void shouldSetSpanErrorWithoutException() {
        spanManager.setSpanError(mockSpan, null, "Error occurred");

        verify(mockSpan).setStatus(StatusCode.ERROR, "Error occurred");
        verify(mockSpan, never()).recordException(any());
    }

    @Test
    public void shouldHandleSetSpanErrorWithNullSpan() {
        spanManager.setSpanError(null, new RuntimeException(), "Error");

        verify(mockSpan, never()).setStatus(any(), anyString());
    }

    @Test
    public void shouldCleanupAllActiveSpans() {
        Span span1 = mock(Span.class);
        Span span2 = mock(Span.class);

        when(mockSpanBuilder.startSpan()).thenReturn(span1, span2);

        spanManager.createProcessSpan("instance-1", "p1", "1.0", "ACTIVE", null, null);
        spanManager.createProcessSpan("instance-2", "p2", "1.0", "ACTIVE", null, null);

        spanManager.cleanup();

        verify(span1).end();
        verify(span2).end();
    }

    @Test
    public void shouldUseHttpRequestContextAsParent() {
        Context httpContext = Context.current();
        OtelContextHolder.setHttpRequestContext(httpContext);

        spanManager.createProcessSpan(
                "instance-1", "my-process", "1.0", "ACTIVE", null, null);

        verify(mockSpanBuilder).setParent(httpContext);
    }

    @Test
    public void shouldUseCurrentContextWhenHttpContextNotSet() {
        spanManager.createProcessSpan(
                "instance-1", "my-process", "1.0", "ACTIVE", null, null);

        verify(mockSpanBuilder).setParent(any(Context.class));
    }

    @Test
    public void shouldSetStartTimestampWhenProvided() {
        Instant startTime = Instant.now().minusSeconds(10);

        Span span = spanManager.createProcessSpan(
                "instance-1", "my-process", "1.0", "ACTIVE", null, null, startTime);

        assertNotNull(span);
        verify(mockSpanBuilder).setStartTimestamp(startTime);
    }

    @Test
    public void shouldNotSetStartTimestampWhenNull() {
        Span span = spanManager.createProcessSpan(
                "instance-1", "my-process", "1.0", "ACTIVE", null, null, null);

        assertNotNull(span);
        verify(mockSpanBuilder, never()).setStartTimestamp(any(Instant.class));
    }
}
