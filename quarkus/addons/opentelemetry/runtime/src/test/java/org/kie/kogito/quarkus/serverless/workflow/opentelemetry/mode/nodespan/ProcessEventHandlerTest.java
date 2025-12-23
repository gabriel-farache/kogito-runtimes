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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;
import org.kie.kogito.process.ProcessError;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.quarkus.serverless.workflow.opentelemetry.common.OtelContextHolder;
import org.kie.kogito.quarkus.serverless.workflow.opentelemetry.config.SonataFlowOtelConfig;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.DURATION_MS;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.ERROR_MESSAGE;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.ERROR_TYPE;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.ErrorConstants;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.OUTCOME;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.PROCESS_INSTANCE_ID;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.ProcessContextStorage;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.ProcessStates;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.REFERENCE_ID;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.TRIGGER;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.TriggerTypes;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.VariableNames;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.Events.PROCESS_INSTANCE_COMPLETE;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.Events.PROCESS_INSTANCE_ERROR;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.Events.PROCESS_INSTANCE_START;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ProcessEventHandlerTest {

    @Mock
    private NodeSpanManager spanManager;

    @Mock
    private SonataFlowOtelConfig config;

    @Mock
    private SonataFlowOtelConfig.EventConfig eventConfig;

    @Mock
    private Span mockSpan;

    @Mock
    private KogitoProcessInstance kogitoProcessInstance;

    @Mock
    @SuppressWarnings("rawtypes")
    private ProcessInstance wrappedProcessInstance;

    @Mock
    private ProcessError processError;

    private ProcessEventHandler handler;

    @BeforeEach
    public void setUp() {
        handler = new ProcessEventHandler(spanManager, config);
        when(config.events()).thenReturn(eventConfig);
    }

    @AfterEach
    public void cleanup() {
        OtelContextHolder.clearProcessStartContext("test-instance");
        OtelContextHolder.clear();
    }

    @Test
    public void shouldAddProcessStartEventWhenEventsEnabled() {
        when(eventConfig.enabled()).thenReturn(true);

        handler.handleProcessStartEvent(mockSpan, "test-instance");

        ArgumentCaptor<Attributes> attributesCaptor = ArgumentCaptor.forClass(Attributes.class);
        verify(spanManager).addProcessEvent(eq(mockSpan), eq(PROCESS_INSTANCE_START), attributesCaptor.capture());

        Attributes capturedAttributes = attributesCaptor.getValue();
        assertEquals("test-instance", capturedAttributes.get(PROCESS_INSTANCE_ID));
        assertEquals(TriggerTypes.HTTP, capturedAttributes.get(TRIGGER));
        assertEquals("test-instance", capturedAttributes.get(REFERENCE_ID));
    }

    @Test
    public void shouldNotAddProcessStartEventWhenEventsDisabled() {
        when(eventConfig.enabled()).thenReturn(false);

        handler.handleProcessStartEvent(mockSpan, "test-instance");

        verify(spanManager, never()).addProcessEvent(any(Span.class), anyString(), any(Attributes.class));
    }

    @Test
    public void shouldNotAddDuplicateProcessStartEvent() {
        when(eventConfig.enabled()).thenReturn(true);

        handler.handleProcessStartEvent(mockSpan, "test-instance");
        handler.handleProcessStartEvent(mockSpan, "test-instance");

        verify(spanManager).addProcessEvent(any(Span.class), eq(PROCESS_INSTANCE_START), any(Attributes.class));
    }

    @Test
    public void shouldUseTransactionIdFromContextForProcessStart() {
        when(eventConfig.enabled()).thenReturn(true);
        OtelContextHolder.setTransactionId("txn-123");

        handler.handleProcessStartEvent(mockSpan, "test-instance");

        ArgumentCaptor<Attributes> attributesCaptor = ArgumentCaptor.forClass(Attributes.class);
        verify(spanManager).addProcessEvent(eq(mockSpan), eq(PROCESS_INSTANCE_START), attributesCaptor.capture());

        Attributes capturedAttributes = attributesCaptor.getValue();
        assertEquals("txn-123", capturedAttributes.get(REFERENCE_ID));
    }

    @Test
    public void shouldUseProcessInstanceIdWhenNoTransactionId() {
        when(eventConfig.enabled()).thenReturn(true);

        handler.handleProcessStartEvent(mockSpan, "test-instance");

        ArgumentCaptor<Attributes> attributesCaptor = ArgumentCaptor.forClass(Attributes.class);
        verify(spanManager).addProcessEvent(eq(mockSpan), eq(PROCESS_INSTANCE_START), attributesCaptor.capture());

        Attributes capturedAttributes = attributesCaptor.getValue();
        assertEquals("test-instance", capturedAttributes.get(REFERENCE_ID));
    }

    @Test
    public void shouldMarkProcessStartContextAsAdded() {
        when(eventConfig.enabled()).thenReturn(true);

        handler.handleProcessStartEvent(mockSpan, "test-instance");

        String startContext = OtelContextHolder.getProcessStartContext("test-instance");
        assertEquals(ProcessContextStorage.ADDED, startContext);
    }

    @Test
    public void shouldAddProcessCompleteEventWithCorrectAttributes() {
        handler.addProcessCompleteEvent(mockSpan, "test-instance", 1500L, ProcessStates.COMPLETED);

        ArgumentCaptor<Attributes> attributesCaptor = ArgumentCaptor.forClass(Attributes.class);
        verify(spanManager).addProcessEvent(eq(mockSpan), eq(PROCESS_INSTANCE_COMPLETE), attributesCaptor.capture());

        Attributes capturedAttributes = attributesCaptor.getValue();
        assertEquals("test-instance", capturedAttributes.get(PROCESS_INSTANCE_ID));
        assertEquals(ProcessStates.COMPLETED, capturedAttributes.get(OUTCOME));
        assertEquals(1500L, capturedAttributes.get(DURATION_MS));
    }

    @Test
    public void shouldHandleExceptionInProcessCompleteEvent() {
        org.mockito.Mockito.doThrow(new RuntimeException("Test exception"))
                .when(spanManager).addProcessEvent(any(Span.class), anyString(), any(Attributes.class));

        handler.addProcessCompleteEvent(mockSpan, "test-instance", 1000L, ProcessStates.COMPLETED);
    }

    @Test
    public void shouldAddProcessErrorEventWithErrorDetails() {
        when(kogitoProcessInstance.getId()).thenReturn("test-instance");
        when(kogitoProcessInstance.unwrap()).thenReturn(wrappedProcessInstance);
        when(wrappedProcessInstance.error()).thenReturn(Optional.of(processError));
        when(processError.errorMessage()).thenReturn("Test error message");
        when(processError.errorCause()).thenReturn(new IllegalStateException("Test cause"));

        handler.handleProcessErrorEvent(mockSpan, "test-instance", kogitoProcessInstance);

        ArgumentCaptor<Attributes> attributesCaptor = ArgumentCaptor.forClass(Attributes.class);
        verify(spanManager).addProcessEvent(eq(mockSpan), eq(PROCESS_INSTANCE_ERROR), attributesCaptor.capture());

        Attributes capturedAttributes = attributesCaptor.getValue();
        assertEquals("test-instance", capturedAttributes.get(PROCESS_INSTANCE_ID));
        assertEquals("Test error message", capturedAttributes.get(ERROR_MESSAGE));
        assertEquals("IllegalStateException", capturedAttributes.get(ERROR_TYPE));
    }

    @Test
    public void shouldUseDefaultErrorMessageWhenEmpty() {
        when(kogitoProcessInstance.getId()).thenReturn("test-instance");
        when(kogitoProcessInstance.unwrap()).thenReturn(wrappedProcessInstance);
        when(wrappedProcessInstance.error()).thenReturn(Optional.of(processError));
        when(processError.errorMessage()).thenReturn("");
        when(processError.errorCause()).thenReturn(null);

        Map<String, Object> variables = new HashMap<>();
        when(kogitoProcessInstance.getVariables()).thenReturn(variables);

        handler.handleProcessErrorEvent(mockSpan, "test-instance", kogitoProcessInstance);

        ArgumentCaptor<Attributes> attributesCaptor = ArgumentCaptor.forClass(Attributes.class);
        verify(spanManager).addProcessEvent(eq(mockSpan), eq(PROCESS_INSTANCE_ERROR), attributesCaptor.capture());

        Attributes capturedAttributes = attributesCaptor.getValue();
        assertEquals(ErrorConstants.PROCESS_EXECUTION_FAILED_UNCAUGHT, capturedAttributes.get(ERROR_MESSAGE));
    }

    @Test
    public void shouldExtractErrorFromVariables() {
        when(kogitoProcessInstance.getId()).thenReturn("test-instance");
        when(kogitoProcessInstance.unwrap()).thenReturn(wrappedProcessInstance);
        when(wrappedProcessInstance.error()).thenReturn(Optional.empty());

        Map<String, Object> variables = new HashMap<>();
        variables.put(VariableNames.ERROR, "Variable error message");
        when(kogitoProcessInstance.getVariables()).thenReturn(variables);

        handler.handleProcessErrorEvent(mockSpan, "test-instance", kogitoProcessInstance);

        ArgumentCaptor<Attributes> attributesCaptor = ArgumentCaptor.forClass(Attributes.class);
        verify(spanManager).addProcessEvent(eq(mockSpan), eq(PROCESS_INSTANCE_ERROR), attributesCaptor.capture());

        Attributes capturedAttributes = attributesCaptor.getValue();
        assertEquals("Variable error message", capturedAttributes.get(ERROR_MESSAGE));
        assertEquals("String", capturedAttributes.get(ERROR_TYPE));
    }

    @Test
    public void shouldHandleNullErrorMessage() {
        when(kogitoProcessInstance.getId()).thenReturn("test-instance");
        when(kogitoProcessInstance.unwrap()).thenReturn(wrappedProcessInstance);
        when(wrappedProcessInstance.error()).thenReturn(Optional.of(processError));
        when(processError.errorMessage()).thenReturn(null);
        when(processError.errorCause()).thenReturn(null);
        when(kogitoProcessInstance.getVariables()).thenReturn(null);

        handler.handleProcessErrorEvent(mockSpan, "test-instance", kogitoProcessInstance);

        ArgumentCaptor<Attributes> attributesCaptor = ArgumentCaptor.forClass(Attributes.class);
        verify(spanManager).addProcessEvent(eq(mockSpan), eq(PROCESS_INSTANCE_ERROR), attributesCaptor.capture());

        Attributes capturedAttributes = attributesCaptor.getValue();
        assertNotNull(capturedAttributes.get(ERROR_MESSAGE));
    }

    @Test
    public void shouldHandleExceptionInProcessErrorEvent() {
        when(kogitoProcessInstance.getId()).thenReturn("test-instance");
        when(kogitoProcessInstance.unwrap()).thenThrow(new RuntimeException("Test exception"));

        handler.handleProcessErrorEvent(mockSpan, "test-instance", kogitoProcessInstance);
    }

    @Test
    public void shouldNotCreateErrorSpanWhenEventsDisabled() {
        when(eventConfig.enabled()).thenReturn(false);
        when(kogitoProcessInstance.getId()).thenReturn("test-instance");
        when(spanManager.getAnyActiveSpan("test-instance")).thenReturn(null);

        handler.handleProcessErrorWithoutSpans(kogitoProcessInstance, 1000L);

        verify(spanManager, never()).createNodeSpanWithContext(
                anyString(), anyString(), anyString(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    public void shouldNotCreateErrorSpanWhenActiveSpanExists() {
        when(eventConfig.enabled()).thenReturn(true);
        when(kogitoProcessInstance.getId()).thenReturn("test-instance");
        when(spanManager.getAnyActiveSpan("test-instance")).thenReturn(mockSpan);

        handler.handleProcessErrorWithoutSpans(kogitoProcessInstance, 1000L);

        verify(spanManager, never()).createNodeSpanWithContext(
                anyString(), anyString(), anyString(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    public void shouldCreateErrorSpanWhenNoActiveSpans() {
        when(eventConfig.enabled()).thenReturn(true);
        when(kogitoProcessInstance.getId()).thenReturn("test-instance");
        when(kogitoProcessInstance.getProcessId()).thenReturn("test-process");
        when(kogitoProcessInstance.getProcessVersion()).thenReturn("1.0");
        when(kogitoProcessInstance.getParentProcessInstanceId()).thenReturn(null);
        when(kogitoProcessInstance.unwrap()).thenReturn(wrappedProcessInstance);
        when(wrappedProcessInstance.error()).thenReturn(Optional.of(processError));
        when(processError.errorMessage()).thenReturn("Test error");
        when(processError.errorCause()).thenReturn(new RuntimeException("Test cause"));

        when(spanManager.getAnyActiveSpan("test-instance")).thenReturn(null);
        when(spanManager.createNodeSpanWithContext(
                eq("test-instance"), eq("test-process"), eq("1.0"), eq(ProcessStates.ERROR),
                eq("ProcessError"), isNull(), isNull(), any())).thenReturn(mockSpan);

        handler.handleProcessErrorWithoutSpans(kogitoProcessInstance, 1500L);

        verify(spanManager).createNodeSpanWithContext(
                eq("test-instance"), eq("test-process"), eq("1.0"), eq(ProcessStates.ERROR),
                eq("ProcessError"), isNull(), isNull(), any());

        verify(spanManager).addProcessEvent(eq(mockSpan), eq(PROCESS_INSTANCE_ERROR), any(Attributes.class));
        verify(spanManager).addProcessEvent(eq(mockSpan), eq(PROCESS_INSTANCE_COMPLETE), any(Attributes.class));
        verify(spanManager).setSpanError(eq(mockSpan), isNull(), eq(ErrorConstants.SPAN_ERROR_DESCRIPTION));
        verify(mockSpan).end();
    }

    @Test
    public void shouldHandleExceptionWhenCreatingErrorSpan() {
        when(eventConfig.enabled()).thenReturn(true);
        when(kogitoProcessInstance.getId()).thenReturn("test-instance");
        when(spanManager.getAnyActiveSpan("test-instance")).thenReturn(null);
        when(kogitoProcessInstance.getProcessId()).thenThrow(new RuntimeException("Test exception"));

        handler.handleProcessErrorWithoutSpans(kogitoProcessInstance, 1000L);
    }

    @Test
    public void shouldIncludeErrorSpanDurationInCompleteEvent() {
        when(eventConfig.enabled()).thenReturn(true);
        when(kogitoProcessInstance.getId()).thenReturn("test-instance");
        when(kogitoProcessInstance.getProcessId()).thenReturn("test-process");
        when(kogitoProcessInstance.getProcessVersion()).thenReturn("1.0");
        when(kogitoProcessInstance.getParentProcessInstanceId()).thenReturn(null);
        when(kogitoProcessInstance.unwrap()).thenReturn(wrappedProcessInstance);
        when(wrappedProcessInstance.error()).thenReturn(Optional.of(processError));
        when(processError.errorMessage()).thenReturn("Error occurred");
        when(processError.errorCause()).thenReturn(new IllegalArgumentException());

        when(spanManager.getAnyActiveSpan("test-instance")).thenReturn(null);
        when(spanManager.createNodeSpanWithContext(
                anyString(), anyString(), anyString(), anyString(), anyString(), any(), any(), any()))
                        .thenReturn(mockSpan);

        handler.handleProcessErrorWithoutSpans(kogitoProcessInstance, 2500L);

        ArgumentCaptor<Attributes> attributesCaptor = ArgumentCaptor.forClass(Attributes.class);
        verify(spanManager).addProcessEvent(eq(mockSpan), eq(PROCESS_INSTANCE_COMPLETE), attributesCaptor.capture());

        Attributes capturedAttributes = attributesCaptor.getValue();
        assertEquals(2500L, capturedAttributes.get(DURATION_MS));
        assertEquals(ProcessStates.ERROR, capturedAttributes.get(OUTCOME));
    }

    @Test
    public void shouldSetErrorStatusOnErrorSpan() {
        when(eventConfig.enabled()).thenReturn(true);
        when(kogitoProcessInstance.getId()).thenReturn("test-instance");
        when(kogitoProcessInstance.getProcessId()).thenReturn("test-process");
        when(kogitoProcessInstance.getProcessVersion()).thenReturn("1.0");
        when(kogitoProcessInstance.getParentProcessInstanceId()).thenReturn(null);
        when(kogitoProcessInstance.unwrap()).thenReturn(wrappedProcessInstance);
        when(wrappedProcessInstance.error()).thenReturn(Optional.of(processError));
        when(processError.errorMessage()).thenReturn("Fatal error");
        when(processError.errorCause()).thenReturn(new NullPointerException());

        when(spanManager.getAnyActiveSpan("test-instance")).thenReturn(null);
        when(spanManager.createNodeSpanWithContext(
                anyString(), anyString(), anyString(), anyString(), anyString(), any(), any(), any()))
                        .thenReturn(mockSpan);

        handler.handleProcessErrorWithoutSpans(kogitoProcessInstance, 1000L);

        verify(spanManager).setSpanError(eq(mockSpan), isNull(), eq(ErrorConstants.SPAN_ERROR_DESCRIPTION));
    }

    @Test
    public void shouldHandleNullWrappedInstanceInErrorExtraction() {
        when(kogitoProcessInstance.getId()).thenReturn("test-instance");
        when(kogitoProcessInstance.unwrap()).thenReturn(null);
        when(kogitoProcessInstance.getVariables()).thenReturn(null);

        handler.handleProcessErrorEvent(mockSpan, "test-instance", kogitoProcessInstance);

        ArgumentCaptor<Attributes> attributesCaptor = ArgumentCaptor.forClass(Attributes.class);
        verify(spanManager).addProcessEvent(eq(mockSpan), eq(PROCESS_INSTANCE_ERROR), attributesCaptor.capture());

        Attributes capturedAttributes = attributesCaptor.getValue();
        assertEquals(ErrorConstants.PROCESS_EXECUTION_FAILED_UNCAUGHT, capturedAttributes.get(ERROR_MESSAGE));
        assertEquals(ErrorConstants.WORKFLOW_EXECUTION_EXCEPTION, capturedAttributes.get(ERROR_TYPE));
    }

    @Test
    public void shouldUseDefaultsWhenErrorExtractionFails() {
        when(kogitoProcessInstance.getId()).thenReturn("test-instance");
        when(kogitoProcessInstance.unwrap()).thenThrow(new RuntimeException("Extraction failed"));

        handler.handleProcessErrorEvent(mockSpan, "test-instance", kogitoProcessInstance);

        ArgumentCaptor<Attributes> attributesCaptor = ArgumentCaptor.forClass(Attributes.class);
        verify(spanManager).addProcessEvent(eq(mockSpan), eq(PROCESS_INSTANCE_ERROR), attributesCaptor.capture());

        Attributes capturedAttributes = attributesCaptor.getValue();
        assertEquals(ErrorConstants.PROCESS_EXECUTION_FAILED, capturedAttributes.get(ERROR_MESSAGE));
        assertEquals(ErrorConstants.PROCESS_EXECUTION_ERROR, capturedAttributes.get(ERROR_TYPE));
    }

    @Test
    public void shouldExtractErrorTypeFromErrorCause() {
        when(kogitoProcessInstance.getId()).thenReturn("test-instance");
        when(kogitoProcessInstance.unwrap()).thenReturn(wrappedProcessInstance);
        when(wrappedProcessInstance.error()).thenReturn(Optional.of(processError));
        when(processError.errorMessage()).thenReturn("Custom error");
        when(processError.errorCause()).thenReturn(new IllegalArgumentException("Bad argument"));

        handler.handleProcessErrorEvent(mockSpan, "test-instance", kogitoProcessInstance);

        ArgumentCaptor<Attributes> attributesCaptor = ArgumentCaptor.forClass(Attributes.class);
        verify(spanManager).addProcessEvent(eq(mockSpan), eq(PROCESS_INSTANCE_ERROR), attributesCaptor.capture());

        Attributes capturedAttributes = attributesCaptor.getValue();
        assertEquals("IllegalArgumentException", capturedAttributes.get(ERROR_TYPE));
    }
}
