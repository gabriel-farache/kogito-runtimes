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
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kie.api.event.process.ProcessCompletedEvent;
import org.kie.api.event.process.ProcessStartedEvent;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.quarkus.serverless.workflow.opentelemetry.common.HeaderContextExtractor;
import org.kie.kogito.quarkus.serverless.workflow.opentelemetry.config.SonataFlowOtelConfig;
import org.kie.kogito.uow.UnitOfWork;
import org.kie.kogito.uow.events.UnitOfWorkAbortEvent;
import org.kie.kogito.uow.events.UnitOfWorkEndEvent;
import org.kie.kogito.uow.events.UnitOfWorkEventListener;
import org.kie.kogito.uow.events.UnitOfWorkStartEvent;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class UnitOfWorkOtelEventListenerTest {

    @Mock
    private ProcessSpanManager spanManager;

    @Mock
    private SonataFlowOtelConfig config;

    @Mock
    private SonataFlowOtelConfig.SpanConfig spanConfig;

    @Mock
    private HeaderContextExtractor headerExtractor;

    @Mock
    private Span mockSpan;

    @Mock
    private UnitOfWork unitOfWork;

    @Mock
    private OtelCollectingUnitOfWork otelUnitOfWork;

    @Mock
    private KogitoProcessInstance processInstance;

    @Mock
    private ProcessStartedEvent processStartedEvent;

    @Mock
    private ProcessCompletedEvent processCompletedEvent;

    private UnitOfWorkOtelEventListener eventListener;

    @BeforeEach
    public void setUp() {
        eventListener = new UnitOfWorkOtelEventListener(spanManager, config, headerExtractor);
        when(config.spans()).thenReturn(spanConfig);
        when(config.serviceName()).thenReturn("test-service");
        when(config.serviceVersion()).thenReturn("1.0.0");
    }

    @Test
    public void shouldImplementUnitOfWorkEventListener() {
        assertTrue(eventListener instanceof UnitOfWorkEventListener);
    }

    @Test
    public void shouldCreateSpanOnAfterEndEvent() {
        when(spanConfig.enabled()).thenReturn(true);
        when(config.transition()).thenReturn("short");
        when(otelUnitOfWork.getProcessInstanceForSpan()).thenReturn(java.util.Optional.of(processInstance));
        when(otelUnitOfWork.getProcessCompletedEvent("process-1")).thenReturn(java.util.Optional.of(processCompletedEvent));
        when(processInstance.getId()).thenReturn("process-1");
        when(processInstance.getProcessId()).thenReturn("test-process");
        when(processInstance.getProcessVersion()).thenReturn("1.0.0");
        when(processInstance.getState()).thenReturn(ProcessInstance.STATE_ACTIVE);
        when(processInstance.getHeaders()).thenReturn(Map.of());
        when(headerExtractor.extractFromProcessHeaders(any())).thenReturn(Map.of());
        when(spanManager.createProcessSpan(anyString(), anyString(), anyString(), anyString(), any(), anyMap(), any()))
                .thenReturn(mockSpan);

        UnitOfWorkStartEvent startEvent = new UnitOfWorkStartEvent(otelUnitOfWork);
        eventListener.onBeforeStartEvent(startEvent);

        UnitOfWorkEndEvent endEvent = new UnitOfWorkEndEvent(otelUnitOfWork);
        eventListener.onAfterEndEvent(endEvent);

        verify(spanManager).createProcessSpan(
                eq("process-1"),
                eq("test-process"),
                eq("1.0.0"),
                eq("ACTIVE"),
                isNull(),
                eq(Map.of()),
                any(Instant.class));
    }

    @Test
    public void shouldNotCreateSpanWhenSpansDisabled() {
        when(spanConfig.enabled()).thenReturn(false);
        when(config.transition()).thenReturn("short");

        UnitOfWorkStartEvent startEvent = new UnitOfWorkStartEvent(unitOfWork);
        eventListener.onBeforeStartEvent(startEvent);

        UnitOfWorkEndEvent endEvent = new UnitOfWorkEndEvent(unitOfWork);
        eventListener.onAfterEndEvent(endEvent);

        verify(spanManager, never()).createProcessSpan(anyString(), anyString(), anyString(), anyString(), anyString(), anyMap(), any());
    }

    @Test
    public void shouldEndSpanOnAfterEndEvent() {
        when(spanConfig.enabled()).thenReturn(true);
        when(config.transition()).thenReturn("short");
        when(otelUnitOfWork.getProcessInstanceForSpan()).thenReturn(java.util.Optional.of(processInstance));
        when(otelUnitOfWork.getProcessCompletedEvent("process-1")).thenReturn(java.util.Optional.of(processCompletedEvent));
        when(processInstance.getId()).thenReturn("process-1");
        when(processInstance.getProcessId()).thenReturn("test-process");
        when(processInstance.getProcessVersion()).thenReturn("1.0.0");
        when(processInstance.getState()).thenReturn(ProcessInstance.STATE_ACTIVE);
        when(processInstance.getHeaders()).thenReturn(Map.of());
        when(headerExtractor.extractFromProcessHeaders(any())).thenReturn(Map.of());
        when(spanManager.createProcessSpan(anyString(), anyString(), anyString(), anyString(), any(), anyMap(), any()))
                .thenReturn(mockSpan);

        UnitOfWorkStartEvent startEvent = new UnitOfWorkStartEvent(otelUnitOfWork);
        eventListener.onBeforeStartEvent(startEvent);

        UnitOfWorkEndEvent endEvent = new UnitOfWorkEndEvent(otelUnitOfWork);
        eventListener.onAfterEndEvent(endEvent);

        verify(spanManager).endProcessSpan(eq("process-1"), eq(StatusCode.OK), eq(null));
    }

    @Test
    public void shouldEnrichSpanWithProcessDataFromOtelCollectingUnitOfWork() {
        when(spanConfig.enabled()).thenReturn(true);
        when(config.transition()).thenReturn("short");
        when(otelUnitOfWork.getProcessInstanceForSpan()).thenReturn(java.util.Optional.of(processInstance));
        when(otelUnitOfWork.getProcessCompletedEvent("process-1")).thenReturn(java.util.Optional.of(processCompletedEvent));
        when(processInstance.getId()).thenReturn("process-1");
        when(processInstance.getProcessId()).thenReturn("test-process");
        when(processInstance.getProcessVersion()).thenReturn("1.0.0");
        when(processInstance.getState()).thenReturn(ProcessInstance.STATE_ACTIVE);
        when(processInstance.getParentProcessInstanceId()).thenReturn(null);
        when(processInstance.getHeaders()).thenReturn(Map.of());
        when(headerExtractor.extractFromProcessHeaders(any())).thenReturn(Map.of());
        when(spanManager.createProcessSpan(anyString(), anyString(), anyString(), anyString(), any(), anyMap(), any()))
                .thenReturn(mockSpan);

        UnitOfWorkStartEvent startEvent = new UnitOfWorkStartEvent(otelUnitOfWork);
        eventListener.onBeforeStartEvent(startEvent);

        UnitOfWorkEndEvent endEvent = new UnitOfWorkEndEvent(otelUnitOfWork);
        eventListener.onAfterEndEvent(endEvent);

        verify(spanManager).createProcessSpan(
                eq("process-1"),
                eq("test-process"),
                eq("1.0.0"),
                eq("ACTIVE"),
                isNull(),
                eq(Map.of()),
                any(Instant.class));
    }

    @Test
    public void shouldHandleNonOtelCollectingUnitOfWork() {
        when(spanConfig.enabled()).thenReturn(true);
        when(config.transition()).thenReturn("short");

        UnitOfWorkStartEvent startEvent = new UnitOfWorkStartEvent(unitOfWork);
        eventListener.onBeforeStartEvent(startEvent);

        UnitOfWorkEndEvent endEvent = new UnitOfWorkEndEvent(unitOfWork);
        eventListener.onAfterEndEvent(endEvent);

        verify(spanManager, never()).createProcessSpan(anyString(), anyString(), anyString(), anyString(), anyString(), anyMap(), any());
        verify(spanManager, never()).endProcessSpan(anyString(), any(), any());
    }

    @Test
    public void shouldSetTransactionIdFromHeader() {
        when(spanConfig.enabled()).thenReturn(true);
        when(config.transition()).thenReturn("short");
        when(otelUnitOfWork.getProcessInstanceForSpan()).thenReturn(java.util.Optional.of(processInstance));
        when(otelUnitOfWork.getProcessCompletedEvent("process-1")).thenReturn(java.util.Optional.of(processCompletedEvent));
        when(processInstance.getId()).thenReturn("process-1");
        when(processInstance.getProcessId()).thenReturn("test-process");
        when(processInstance.getProcessVersion()).thenReturn("1.0.0");
        when(processInstance.getState()).thenReturn(ProcessInstance.STATE_ACTIVE);
        Map<String, List<String>> headers = Map.of("X-TRANSACTION-ID", List.of("txn-123"));
        when(processInstance.getHeaders()).thenReturn(headers);
        when(headerExtractor.extractFromProcessHeaders(eq(headers)))
                .thenReturn(Map.of("transaction.id", "txn-123"));
        when(spanManager.createProcessSpan(anyString(), anyString(), anyString(), anyString(), any(), anyMap(), any()))
                .thenReturn(mockSpan);

        UnitOfWorkStartEvent startEvent = new UnitOfWorkStartEvent(otelUnitOfWork);
        eventListener.onBeforeStartEvent(startEvent);

        UnitOfWorkEndEvent endEvent = new UnitOfWorkEndEvent(otelUnitOfWork);
        eventListener.onAfterEndEvent(endEvent);

        ArgumentCaptor<Map<String, String>> contextCaptor = ArgumentCaptor.forClass(Map.class);
        verify(spanManager).createProcessSpan(
                eq("process-1"),
                eq("test-process"),
                eq("1.0.0"),
                eq("ACTIVE"),
                isNull(),
                contextCaptor.capture(),
                any(Instant.class));

        Map<String, String> capturedContext = contextCaptor.getValue();
        assertEquals("txn-123", capturedContext.get("transaction.id"));
    }

    @Test
    public void shouldFallbackToProcessInstanceIdWhenNoTransactionId() {
        when(spanConfig.enabled()).thenReturn(true);
        when(config.transition()).thenReturn("short");
        when(otelUnitOfWork.getProcessInstanceForSpan()).thenReturn(java.util.Optional.of(processInstance));
        when(otelUnitOfWork.getProcessCompletedEvent("process-1")).thenReturn(java.util.Optional.of(processCompletedEvent));
        when(processInstance.getId()).thenReturn("process-1");
        when(processInstance.getProcessId()).thenReturn("test-process");
        when(processInstance.getProcessVersion()).thenReturn("1.0.0");
        when(processInstance.getState()).thenReturn(ProcessInstance.STATE_ACTIVE);
        when(processInstance.getHeaders()).thenReturn(Map.of());
        when(headerExtractor.extractFromProcessHeaders(any())).thenReturn(Map.of());
        when(spanManager.createProcessSpan(anyString(), anyString(), anyString(), anyString(), any(), anyMap(), any()))
                .thenReturn(mockSpan);

        UnitOfWorkStartEvent startEvent = new UnitOfWorkStartEvent(otelUnitOfWork);
        eventListener.onBeforeStartEvent(startEvent);

        UnitOfWorkEndEvent endEvent = new UnitOfWorkEndEvent(otelUnitOfWork);
        eventListener.onAfterEndEvent(endEvent);

        verify(spanManager).createProcessSpan(
                eq("process-1"),
                eq("test-process"),
                eq("1.0.0"),
                eq("ACTIVE"),
                isNull(),
                eq(Map.of()),
                any(Instant.class));
    }

    @Test
    public void shouldSetTrackerAttributesFromHeaders() {
        when(spanConfig.enabled()).thenReturn(true);
        when(config.transition()).thenReturn("short");
        when(otelUnitOfWork.getProcessInstanceForSpan()).thenReturn(java.util.Optional.of(processInstance));
        when(otelUnitOfWork.getProcessCompletedEvent("process-1")).thenReturn(java.util.Optional.of(processCompletedEvent));
        when(processInstance.getId()).thenReturn("process-1");
        when(processInstance.getProcessId()).thenReturn("test-process");
        when(processInstance.getProcessVersion()).thenReturn("1.0.0");
        when(processInstance.getState()).thenReturn(ProcessInstance.STATE_ACTIVE);
        Map<String, List<String>> headers = Map.of(
                "X-TRACKER-USER", List.of("john.doe"),
                "X-TRACKER-TEAM", List.of("team-alpha"));
        when(processInstance.getHeaders()).thenReturn(headers);
        Map<String, String> extractedContext = new HashMap<>();
        extractedContext.put("tracker.user", "john.doe");
        extractedContext.put("tracker.team", "team-alpha");
        when(headerExtractor.extractFromProcessHeaders(eq(headers))).thenReturn(extractedContext);
        when(spanManager.createProcessSpan(anyString(), anyString(), anyString(), anyString(), any(), anyMap(), any()))
                .thenReturn(mockSpan);

        UnitOfWorkStartEvent startEvent = new UnitOfWorkStartEvent(otelUnitOfWork);
        eventListener.onBeforeStartEvent(startEvent);

        UnitOfWorkEndEvent endEvent = new UnitOfWorkEndEvent(otelUnitOfWork);
        eventListener.onAfterEndEvent(endEvent);

        ArgumentCaptor<Map<String, String>> contextCaptor = ArgumentCaptor.forClass(Map.class);
        verify(spanManager).createProcessSpan(
                eq("process-1"),
                eq("test-process"),
                eq("1.0.0"),
                eq("ACTIVE"),
                isNull(),
                contextCaptor.capture(),
                any(Instant.class));

        Map<String, String> capturedContext = contextCaptor.getValue();
        assertEquals("john.doe", capturedContext.get("tracker.user"));
        assertEquals("team-alpha", capturedContext.get("tracker.team"));
    }

    @Test
    public void shouldSetErrorStatusOnAbort() {
        when(spanConfig.enabled()).thenReturn(true);
        when(config.transition()).thenReturn("short");
        when(otelUnitOfWork.getProcessInstanceForSpan()).thenReturn(java.util.Optional.of(processInstance));
        when(processInstance.getId()).thenReturn("process-1");
        when(processInstance.getProcessId()).thenReturn("test-process");
        when(processInstance.getProcessVersion()).thenReturn("1.0.0");
        when(processInstance.getState()).thenReturn(ProcessInstance.STATE_ACTIVE);
        when(processInstance.getHeaders()).thenReturn(Map.of());
        when(headerExtractor.extractFromProcessHeaders(any())).thenReturn(Map.of());
        when(spanManager.createProcessSpan(anyString(), anyString(), anyString(), anyString(), any(), anyMap(), any()))
                .thenReturn(mockSpan);

        UnitOfWorkStartEvent startEvent = new UnitOfWorkStartEvent(otelUnitOfWork);
        eventListener.onBeforeStartEvent(startEvent);

        UnitOfWorkAbortEvent abortEvent = new UnitOfWorkAbortEvent(otelUnitOfWork);
        eventListener.onAfterAbortEvent(abortEvent);

        verify(spanManager).endProcessSpan(
                eq("process-1"),
                eq(StatusCode.ERROR),
                eq("Process failed with error"));
    }

    @Test
    public void shouldAddProcessStartEvent() {
        when(spanConfig.enabled()).thenReturn(true);
        when(config.transition()).thenReturn("short");
        when(otelUnitOfWork.getProcessInstanceForSpan()).thenReturn(java.util.Optional.of(processInstance));
        when(otelUnitOfWork.getProcessCompletedEvent("process-1")).thenReturn(java.util.Optional.of(processCompletedEvent));
        when(processInstance.getId()).thenReturn("process-1");
        when(processInstance.getProcessId()).thenReturn("test-process");
        when(processInstance.getProcessVersion()).thenReturn("1.0.0");
        when(processInstance.getState()).thenReturn(ProcessInstance.STATE_ACTIVE);
        when(processInstance.getParentProcessInstanceId()).thenReturn(null);
        when(processInstance.getHeaders()).thenReturn(Map.of());
        when(headerExtractor.extractFromProcessHeaders(any())).thenReturn(Map.of());
        when(spanManager.createProcessSpan(anyString(), anyString(), anyString(), anyString(), any(), anyMap(), any()))
                .thenReturn(mockSpan);

        UnitOfWorkStartEvent startEvent = new UnitOfWorkStartEvent(otelUnitOfWork);
        eventListener.onBeforeStartEvent(startEvent);

        UnitOfWorkEndEvent endEvent = new UnitOfWorkEndEvent(otelUnitOfWork);
        eventListener.onAfterEndEvent(endEvent);

        verify(spanManager).addProcessEvent(eq(mockSpan), eq("process.instance.start"), any(Attributes.class));
    }

    @Test
    public void shouldAddProcessCompleteEvent() {
        when(spanConfig.enabled()).thenReturn(true);
        when(config.transition()).thenReturn("short");
        when(otelUnitOfWork.getProcessInstanceForSpan()).thenReturn(java.util.Optional.of(processInstance));
        when(otelUnitOfWork.getProcessCompletedEvent("process-1")).thenReturn(java.util.Optional.of(processCompletedEvent));
        when(processInstance.getId()).thenReturn("process-1");
        when(processInstance.getProcessId()).thenReturn("test-process");
        when(processInstance.getProcessVersion()).thenReturn("1.0.0");
        when(processInstance.getState()).thenReturn(ProcessInstance.STATE_ACTIVE);
        when(processInstance.getHeaders()).thenReturn(Map.of());
        when(headerExtractor.extractFromProcessHeaders(any())).thenReturn(Map.of());
        when(spanManager.createProcessSpan(anyString(), anyString(), anyString(), anyString(), any(), anyMap(), any()))
                .thenReturn(mockSpan);

        UnitOfWorkStartEvent startEvent = new UnitOfWorkStartEvent(otelUnitOfWork);
        eventListener.onBeforeStartEvent(startEvent);

        UnitOfWorkEndEvent endEvent = new UnitOfWorkEndEvent(otelUnitOfWork);
        eventListener.onAfterEndEvent(endEvent);

        verify(spanManager).addProcessEvent(eq(mockSpan), eq("process.instance.complete"), any(Attributes.class));
    }

    @Test
    public void shouldFindRootProcessWhenMultipleProcesses() {
        when(spanConfig.enabled()).thenReturn(true);
        when(config.transition()).thenReturn("short");

        when(otelUnitOfWork.getProcessInstanceForSpan()).thenReturn(java.util.Optional.of(processInstance));
        when(otelUnitOfWork.getProcessCompletedEvent("process-1")).thenReturn(java.util.Optional.of(processCompletedEvent));

        when(processInstance.getId()).thenReturn("process-1");
        when(processInstance.getProcessId()).thenReturn("test-process");
        when(processInstance.getProcessVersion()).thenReturn("1.0.0");
        when(processInstance.getState()).thenReturn(ProcessInstance.STATE_ACTIVE);
        when(processInstance.getParentProcessInstanceId()).thenReturn(null);
        when(processInstance.getHeaders()).thenReturn(Map.of());

        when(headerExtractor.extractFromProcessHeaders(any())).thenReturn(Map.of());
        when(spanManager.createProcessSpan(anyString(), anyString(), anyString(), anyString(), any(), anyMap(), any()))
                .thenReturn(mockSpan);

        UnitOfWorkStartEvent startEvent = new UnitOfWorkStartEvent(otelUnitOfWork);
        eventListener.onBeforeStartEvent(startEvent);

        UnitOfWorkEndEvent endEvent = new UnitOfWorkEndEvent(otelUnitOfWork);
        eventListener.onAfterEndEvent(endEvent);

        verify(spanManager, times(1)).createProcessSpan(
                eq("process-1"),
                eq("test-process"),
                eq("1.0.0"),
                eq("ACTIVE"),
                isNull(),
                eq(Map.of()),
                any(Instant.class));

        verify(spanManager, never()).createProcessSpan(
                eq("child-process"),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyMap(),
                any());
    }

    @Test
    public void shouldCaptureStartTimeInOnBeforeStartEvent() {
        when(spanConfig.enabled()).thenReturn(true);
        when(config.transition()).thenReturn("short");
        when(otelUnitOfWork.getProcessInstanceForSpan()).thenReturn(java.util.Optional.of(processInstance));
        when(otelUnitOfWork.getProcessCompletedEvent("process-1")).thenReturn(java.util.Optional.of(processCompletedEvent));
        when(processInstance.getId()).thenReturn("process-1");
        when(processInstance.getProcessId()).thenReturn("test-process");
        when(processInstance.getProcessVersion()).thenReturn("1.0.0");
        when(processInstance.getState()).thenReturn(ProcessInstance.STATE_ACTIVE);
        when(processInstance.getHeaders()).thenReturn(Map.of());
        when(headerExtractor.extractFromProcessHeaders(any())).thenReturn(Map.of());
        when(spanManager.createProcessSpan(anyString(), anyString(), anyString(), anyString(), any(), anyMap(), any()))
                .thenReturn(mockSpan);

        Instant beforeStart = Instant.now();

        UnitOfWorkStartEvent startEvent = new UnitOfWorkStartEvent(otelUnitOfWork);
        eventListener.onBeforeStartEvent(startEvent);

        UnitOfWorkEndEvent endEvent = new UnitOfWorkEndEvent(otelUnitOfWork);
        eventListener.onAfterEndEvent(endEvent);

        Instant afterEnd = Instant.now();

        ArgumentCaptor<Instant> startTimeCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(spanManager).createProcessSpan(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(),
                anyMap(),
                startTimeCaptor.capture());

        Instant capturedStartTime = startTimeCaptor.getValue();
        assertTrue(capturedStartTime.isAfter(beforeStart) || capturedStartTime.equals(beforeStart));
        assertTrue(capturedStartTime.isBefore(afterEnd) || capturedStartTime.equals(afterEnd));
    }
}
