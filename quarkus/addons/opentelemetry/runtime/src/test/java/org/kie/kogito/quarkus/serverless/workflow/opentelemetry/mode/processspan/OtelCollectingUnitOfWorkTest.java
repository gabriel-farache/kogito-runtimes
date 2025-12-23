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

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jbpm.process.instance.event.ProcessStartedEventImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kie.api.event.process.ProcessNodeLeftEvent;
import org.kie.api.event.process.ProcessNodeTriggeredEvent;
import org.kie.api.event.process.ProcessStartedEvent;
import org.kie.api.runtime.process.NodeInstance;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.kogito.event.EventManager;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;
import org.kie.kogito.services.uow.CollectingUnitOfWork;
import org.kie.kogito.uow.WorkUnit;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OtelCollectingUnitOfWorkTest {

    @Mock
    private EventManager eventManager;

    @Mock
    private ProcessInstance processInstance;

    private OtelCollectingUnitOfWork unitOfWork;

    @BeforeEach
    void setUp() {
        when(eventManager.newBatch()).thenReturn(mock(org.kie.kogito.event.EventBatch.class));
        unitOfWork = new OtelCollectingUnitOfWork(eventManager);
    }

    @Test
    void shouldExtendCollectingUnitOfWork() {
        assertTrue(unitOfWork instanceof CollectingUnitOfWork,
                "OtelCollectingUnitOfWork should extend CollectingUnitOfWork");
    }

    @Test
    void shouldCallParentEndMethod() {
        when(processInstance.getId()).thenReturn("process-1");

        AtomicBoolean workPerformed = new AtomicBoolean(false);
        ProcessStartedEvent event = new ProcessStartedEventImpl(processInstance, null, null);
        WorkUnit<ProcessStartedEvent> workUnit = WorkUnit.create(event, e -> workPerformed.set(true));

        unitOfWork.start();
        unitOfWork.intercept(workUnit);
        unitOfWork.end();

        assertTrue(workPerformed.get(), "Parent end() method should perform work units");
        verify(eventManager).publish(any());
    }

    @Test
    void shouldCaptureProcessNodeTriggeredEvent() {
        ProcessNodeTriggeredEvent nodeEvent = mock(ProcessNodeTriggeredEvent.class);
        ProcessInstance mockProcess = mock(ProcessInstance.class);
        NodeInstance mockNode = mock(NodeInstance.class);

        when(mockProcess.getId()).thenReturn("process-1");
        when(nodeEvent.getProcessInstance()).thenReturn(mockProcess);
        when(nodeEvent.getNodeInstance()).thenReturn(mockNode);
        when(mockNode.getNodeName()).thenReturn("TestNode");

        WorkUnit<ProcessNodeTriggeredEvent> workUnit = WorkUnit.create(nodeEvent, e -> {
        });

        unitOfWork.start();
        unitOfWork.intercept(workUnit);
        unitOfWork.end();

        List<OtelEventDataProvider.NodeEventData> collectedEvents = unitOfWork.getCollectedNodeEvents("process-1");
        assertEquals(1, collectedEvents.size(), "Should have captured one node triggered event");
        assertEquals("node.triggered", collectedEvents.get(0).eventName());
        assertEquals("TestNode", collectedEvents.get(0).nodeName());
    }

    @Test
    void shouldCaptureProcessNodeLeftEvent() {
        ProcessNodeLeftEvent nodeEvent = mock(ProcessNodeLeftEvent.class);
        ProcessInstance mockProcess = mock(ProcessInstance.class);
        NodeInstance mockNode = mock(NodeInstance.class);

        when(mockProcess.getId()).thenReturn("process-1");
        when(nodeEvent.getProcessInstance()).thenReturn(mockProcess);
        when(nodeEvent.getNodeInstance()).thenReturn(mockNode);
        when(mockNode.getNodeName()).thenReturn("CompletedNode");

        WorkUnit<ProcessNodeLeftEvent> workUnit = WorkUnit.create(nodeEvent, e -> {
        });

        unitOfWork.start();
        unitOfWork.intercept(workUnit);
        unitOfWork.end();

        List<OtelEventDataProvider.NodeEventData> collectedEvents = unitOfWork.getCollectedNodeEvents("process-1");
        assertEquals(1, collectedEvents.size(), "Should have captured one node left event");
        assertEquals("node.left", collectedEvents.get(0).eventName());
        assertEquals("CompletedNode", collectedEvents.get(0).nodeName());
    }

    @Test
    void shouldReturnRootProcessInstanceFromNodeEventsWhenNoStartEvent() {
        ProcessNodeTriggeredEvent nodeEvent = mock(ProcessNodeTriggeredEvent.class);
        KogitoProcessInstance mockProcess = mock(KogitoProcessInstance.class);
        NodeInstance mockNode = mock(NodeInstance.class);

        when(mockProcess.getId()).thenReturn("process-1");
        when(mockProcess.getParentProcessInstanceId()).thenReturn(null);
        when(nodeEvent.getProcessInstance()).thenReturn(mockProcess);
        when(nodeEvent.getNodeInstance()).thenReturn(mockNode);
        when(mockNode.getNodeName()).thenReturn("ResumeNode");

        WorkUnit<ProcessNodeTriggeredEvent> workUnit = WorkUnit.create(nodeEvent, e -> {
        });

        unitOfWork.start();
        unitOfWork.intercept(workUnit);
        unitOfWork.end();

        assertTrue(unitOfWork.getProcessInstanceForSpan().isPresent());
        assertEquals("process-1", unitOfWork.getProcessInstanceForSpan().get().getId());
    }

    @Test
    void shouldPreferStartEventOverNodeEventForRootProcessInstance() {
        KogitoProcessInstance mockProcess = mock(KogitoProcessInstance.class);
        when(mockProcess.getId()).thenReturn("process-1");
        when(mockProcess.getParentProcessInstanceId()).thenReturn(null);

        ProcessStartedEvent startEvent = new ProcessStartedEventImpl(mockProcess, null, null);
        WorkUnit<ProcessStartedEvent> startWork = WorkUnit.create(startEvent, e -> {
        });

        ProcessNodeTriggeredEvent nodeEvent = mock(ProcessNodeTriggeredEvent.class);
        NodeInstance mockNode = mock(NodeInstance.class);
        when(nodeEvent.getProcessInstance()).thenReturn(mockProcess);
        when(nodeEvent.getNodeInstance()).thenReturn(mockNode);
        when(mockNode.getNodeName()).thenReturn("TestNode");

        WorkUnit<ProcessNodeTriggeredEvent> nodeWork = WorkUnit.create(nodeEvent, e -> {
        });

        unitOfWork.start();
        unitOfWork.intercept(startWork);
        unitOfWork.intercept(nodeWork);
        unitOfWork.end();

        assertTrue(unitOfWork.getProcessInstanceForSpan().isPresent());
        assertFalse(unitOfWork.isResumedExecution("process-1"));
    }

    @Test
    void shouldDetectResumedExecution() {
        ProcessNodeTriggeredEvent nodeEvent = mock(ProcessNodeTriggeredEvent.class);
        KogitoProcessInstance mockProcess = mock(KogitoProcessInstance.class);
        NodeInstance mockNode = mock(NodeInstance.class);

        when(mockProcess.getId()).thenReturn("process-1");
        when(mockProcess.getParentProcessInstanceId()).thenReturn(null);
        when(nodeEvent.getProcessInstance()).thenReturn(mockProcess);
        when(nodeEvent.getNodeInstance()).thenReturn(mockNode);
        when(mockNode.getNodeName()).thenReturn("ResumeNode");

        WorkUnit<ProcessNodeTriggeredEvent> workUnit = WorkUnit.create(nodeEvent, e -> {
        });

        unitOfWork.start();
        unitOfWork.intercept(workUnit);
        unitOfWork.end();

        assertTrue(unitOfWork.isResumedExecution("process-1"));
    }

    @Test
    void shouldNotDetectResumedExecutionWhenStartEventPresent() {
        KogitoProcessInstance mockProcess = mock(KogitoProcessInstance.class);
        when(mockProcess.getId()).thenReturn("process-1");
        when(mockProcess.getParentProcessInstanceId()).thenReturn(null);

        ProcessStartedEvent startEvent = new ProcessStartedEventImpl(mockProcess, null, null);
        WorkUnit<ProcessStartedEvent> workUnit = WorkUnit.create(startEvent, e -> {
        });

        unitOfWork.start();
        unitOfWork.intercept(workUnit);
        unitOfWork.end();

        assertFalse(unitOfWork.isResumedExecution("process-1"));
    }

    @Test
    void shouldReturnSubflowProcessInstanceWhenOnlySubflowEventsPresent() {
        ProcessNodeTriggeredEvent nodeEvent = mock(ProcessNodeTriggeredEvent.class);
        KogitoProcessInstance subflowProcess = mock(KogitoProcessInstance.class);
        NodeInstance mockNode = mock(NodeInstance.class);

        when(subflowProcess.getId()).thenReturn("subflow-123");
        when(subflowProcess.getParentProcessInstanceId()).thenReturn("parent-456");
        when(nodeEvent.getProcessInstance()).thenReturn(subflowProcess);
        when(nodeEvent.getNodeInstance()).thenReturn(mockNode);
        when(mockNode.getNodeName()).thenReturn("WaitForEventNode");

        WorkUnit<ProcessNodeTriggeredEvent> workUnit = WorkUnit.create(nodeEvent, e -> {
        });

        unitOfWork.start();
        unitOfWork.intercept(workUnit);
        unitOfWork.end();

        assertTrue(unitOfWork.getProcessInstanceForSpan().isPresent(),
                "Should return subflow when no root process is present");
        assertEquals("subflow-123", unitOfWork.getProcessInstanceForSpan().get().getId());
    }
}
