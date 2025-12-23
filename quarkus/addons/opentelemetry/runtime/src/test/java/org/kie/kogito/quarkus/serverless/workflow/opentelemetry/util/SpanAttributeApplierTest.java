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

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.kogito.quarkus.serverless.workflow.opentelemetry.config.SonataFlowOtelConfig;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SpanAttributeApplierTest {

    private SpanBuilder mockSpanBuilder;
    private Span mockSpan;
    private SonataFlowOtelConfig mockConfig;

    @BeforeEach
    void setUp() {
        mockSpanBuilder = mock(SpanBuilder.class);
        mockSpan = mock(Span.class);
        mockConfig = mock(SonataFlowOtelConfig.class);

        when(mockSpanBuilder.setAttribute(eq(SONATAFLOW_PROCESS_INSTANCE_ID), eq("test-instance-123")))
                .thenReturn(mockSpanBuilder);
        when(mockSpanBuilder.setAttribute(eq(SONATAFLOW_PROCESS_ID), eq("test-process")))
                .thenReturn(mockSpanBuilder);
        when(mockSpanBuilder.setAttribute(eq(SONATAFLOW_PROCESS_VERSION), eq("1.0.0")))
                .thenReturn(mockSpanBuilder);
        when(mockSpanBuilder.setAttribute(eq(SONATAFLOW_PROCESS_INSTANCE_STATE), eq("ACTIVE")))
                .thenReturn(mockSpanBuilder);
        when(mockSpanBuilder.setAttribute(eq(SERVICE_NAME), eq("my-service")))
                .thenReturn(mockSpanBuilder);
        when(mockSpanBuilder.setAttribute(eq(SERVICE_VERSION), eq("2.0.0")))
                .thenReturn(mockSpanBuilder);
        when(mockSpanBuilder.setAttribute(eq(SONATAFLOW_PARENT_PROCESS_INSTANCE_ID), eq("parent-123")))
                .thenReturn(mockSpanBuilder);
        when(mockSpanBuilder.setAttribute(eq(SONATAFLOW_WORKFLOW_STATE), eq("GreetState")))
                .thenReturn(mockSpanBuilder);
        when(mockSpanBuilder.setAttribute(eq(SONATAFLOW_PROCESS_INSTANCE_NODE), eq("node-456")))
                .thenReturn(mockSpanBuilder);

        when(mockConfig.serviceName()).thenReturn("my-service");
        when(mockConfig.serviceVersion()).thenReturn("2.0.0");
    }

    @Test
    void shouldApplyCommonAttributes() {
        SpanAttributeApplier.applyCommonAttributes(
                mockSpanBuilder,
                "test-instance-123",
                "test-process",
                "1.0.0",
                "ACTIVE",
                mockConfig);

        verify(mockSpanBuilder).setAttribute(SONATAFLOW_PROCESS_INSTANCE_ID, "test-instance-123");
        verify(mockSpanBuilder).setAttribute(SONATAFLOW_PROCESS_ID, "test-process");
        verify(mockSpanBuilder).setAttribute(SONATAFLOW_PROCESS_VERSION, "1.0.0");
        verify(mockSpanBuilder).setAttribute(SONATAFLOW_PROCESS_INSTANCE_STATE, "ACTIVE");
        verify(mockSpanBuilder).setAttribute(SERVICE_NAME, "my-service");
        verify(mockSpanBuilder).setAttribute(SERVICE_VERSION, "2.0.0");
    }

    @Test
    void shouldApplyHeaderContextWithTransactionId() {
        Map<String, String> headerContext = new HashMap<>();
        headerContext.put("transaction.id", "txn-456");

        SpanAttributeApplier.applyHeaderContext(mockSpan, headerContext, "test-instance-123");

        verify(mockSpan).setAttribute(SONATAFLOW_TRANSACTION_ID, "txn-456");
    }

    @Test
    void shouldApplyHeaderContextWithTrackerAttributes() {
        Map<String, String> headerContext = new HashMap<>();
        headerContext.put("tracker.user", "john.doe");
        headerContext.put("tracker.session", "sess-789");

        SpanAttributeApplier.applyHeaderContext(mockSpan, headerContext, "test-instance-123");

        verify(mockSpan).setAttribute(eq("sonataflow.tracker.user"), eq("john.doe"));
        verify(mockSpan).setAttribute(eq("sonataflow.tracker.session"), eq("sess-789"));
        verify(mockSpan).setAttribute(SONATAFLOW_TRANSACTION_ID, "test-instance-123");
    }

    @Test
    void shouldApplyHeaderContextWithTransactionIdAndTrackers() {
        Map<String, String> headerContext = new HashMap<>();
        headerContext.put("transaction.id", "txn-999");
        headerContext.put("tracker.request.id", "req-abc");

        SpanAttributeApplier.applyHeaderContext(mockSpan, headerContext, "test-instance-123");

        verify(mockSpan).setAttribute(SONATAFLOW_TRANSACTION_ID, "txn-999");
        verify(mockSpan).setAttribute(eq("sonataflow.tracker.request.id"), eq("req-abc"));
    }

    @Test
    void shouldUseProcessInstanceIdWhenNoTransactionId() {
        Map<String, String> headerContext = new HashMap<>();

        SpanAttributeApplier.applyHeaderContext(mockSpan, headerContext, "test-instance-123");

        verify(mockSpan).setAttribute(SONATAFLOW_TRANSACTION_ID, "test-instance-123");
    }

    @Test
    void shouldUseProcessInstanceIdWhenHeaderContextIsNull() {
        SpanAttributeApplier.applyHeaderContext(mockSpan, null, "test-instance-123");

        verify(mockSpan).setAttribute(SONATAFLOW_TRANSACTION_ID, "test-instance-123");
    }

    @Test
    void shouldHandleEmptyHeaderContext() {
        Map<String, String> headerContext = new HashMap<>();

        SpanAttributeApplier.applyHeaderContext(mockSpan, headerContext, "test-instance-123");

        verify(mockSpan).setAttribute(SONATAFLOW_TRANSACTION_ID, "test-instance-123");
    }

    @Test
    void shouldNotApplyHeaderContextWhenSpanIsNull() {
        Map<String, String> headerContext = new HashMap<>();
        headerContext.put("transaction.id", "txn-456");

        SpanAttributeApplier.applyHeaderContext(null, headerContext, "test-instance-123");

        verifyNoInteractions(mockSpan);
    }

    @Test
    void shouldApplyOptionalParentProcessId() {
        SpanAttributeApplier.applyOptionalParentProcessId(mockSpanBuilder, "parent-123");

        verify(mockSpanBuilder).setAttribute(SONATAFLOW_PARENT_PROCESS_INSTANCE_ID, "parent-123");
    }

    @Test
    void shouldNotApplyParentProcessIdWhenNull() {
        SpanAttributeApplier.applyOptionalParentProcessId(mockSpanBuilder, null);

        verify(mockSpanBuilder, never()).setAttribute(eq(SONATAFLOW_PARENT_PROCESS_INSTANCE_ID), eq(null));
    }

    @Test
    void shouldNotApplyParentProcessIdWhenEmpty() {
        SpanAttributeApplier.applyOptionalParentProcessId(mockSpanBuilder, "");

        verify(mockSpanBuilder, never()).setAttribute(eq(SONATAFLOW_PARENT_PROCESS_INSTANCE_ID), eq(""));
    }

    @Test
    void shouldApplyParentProcessIdWhenWhitespace() {
        SpanAttributeApplier.applyOptionalParentProcessId(mockSpanBuilder, "   ");

        verify(mockSpanBuilder).setAttribute(SONATAFLOW_PARENT_PROCESS_INSTANCE_ID, "   ");
    }

    @Test
    void shouldApplyOptionalWorkflowState() {
        SpanAttributeApplier.applyOptionalWorkflowState(mockSpanBuilder, "GreetState");

        verify(mockSpanBuilder).setAttribute(SONATAFLOW_WORKFLOW_STATE, "GreetState");
    }

    @Test
    void shouldNotApplyWorkflowStateWhenNull() {
        SpanAttributeApplier.applyOptionalWorkflowState(mockSpanBuilder, null);

        verify(mockSpanBuilder, never()).setAttribute(eq(SONATAFLOW_WORKFLOW_STATE), eq(null));
    }

    @Test
    void shouldNotApplyWorkflowStateWhenEmpty() {
        SpanAttributeApplier.applyOptionalWorkflowState(mockSpanBuilder, "");

        verify(mockSpanBuilder, never()).setAttribute(eq(SONATAFLOW_WORKFLOW_STATE), eq(""));
    }

    @Test
    void shouldApplyNodeAttribute() {
        SpanAttributeApplier.applyNodeAttribute(mockSpanBuilder, "node-456");

        verify(mockSpanBuilder).setAttribute(SONATAFLOW_PROCESS_INSTANCE_NODE, "node-456");
    }

    @Test
    void shouldNotApplyNodeAttributeWhenNull() {
        SpanAttributeApplier.applyNodeAttribute(mockSpanBuilder, null);

        verify(mockSpanBuilder, never()).setAttribute(eq(SONATAFLOW_PROCESS_INSTANCE_NODE), eq(null));
    }
}
