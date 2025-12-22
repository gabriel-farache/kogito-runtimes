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

import java.util.HashMap;

import org.jbpm.workflow.instance.NodeInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kie.kogito.internal.process.event.KogitoProcessEventListener;
import org.kie.kogito.internal.process.runtime.KogitoNodeInstance;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.quarkus.serverless.workflow.opentelemetry.config.SonataFlowOtelConfig;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class NodeOtelEventListenerTest {

    @Mock
    private NodeSpanManager spanManager;

    @Mock
    private SonataFlowOtelConfig config;

    @Mock
    private SonataFlowOtelConfig.EventConfig eventConfig;

    @Mock
    private Span mockSpan;

    @Mock
    private KogitoProcessInstance processInstance;

    private NodeOtelEventListener eventListener;

    @Mock
    private HeaderContextExtractor headerExtractor;

    private NodeInstance jbpmNodeInstance;

    @BeforeEach
    public void setUp() {
        eventListener = new NodeOtelEventListener(spanManager, config, headerExtractor);
        jbpmNodeInstance = org.mockito.Mockito.mock(NodeInstance.class,
                org.mockito.Mockito.withSettings().extraInterfaces(KogitoNodeInstance.class));
        org.mockito.Mockito.lenient().when(((KogitoNodeInstance) jbpmNodeInstance).getMetaData()).thenReturn(new HashMap<>());
    }

    @Test
    public void shouldImplementKogitoProcessEventListener() {
        assertTrue(eventListener instanceof KogitoProcessEventListener);
    }

    @Test
    public void shouldCreateNodeSpanOnBeforeNodeTriggered() {
        when(((KogitoNodeInstance) jbpmNodeInstance).getNodeName()).thenReturn("TestNode");
        when(processInstance.getId()).thenReturn("process-instance-1");
        when(processInstance.getProcessId()).thenReturn("test-process");
        when(processInstance.getProcessVersion()).thenReturn("1.0.0");
        when(processInstance.getState()).thenReturn(ProcessInstance.STATE_ACTIVE);

        when(spanManager.createNodeSpanWithContext(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(mockSpan);

        org.kie.api.event.process.ProcessNodeTriggeredEvent event =
                org.mockito.Mockito.mock(org.kie.api.event.process.ProcessNodeTriggeredEvent.class);
        when(event.getNodeInstance()).thenReturn((KogitoNodeInstance) jbpmNodeInstance);
        when(event.getProcessInstance()).thenReturn(processInstance);

        eventListener.beforeNodeTriggered(event);

        verify(spanManager).createNodeSpanWithContext(eq("process-instance-1"), eq("test-process"), eq("1.0.0"), eq("ACTIVE"), eq("TestNode"), isNull(), isNull(), any());
        verify(spanManager).addProcessEvent(mockSpan, "node.started", "Node execution started: TestNode");
    }

    @Test
    public void shouldCreateSpanForEachNodeInSequence() {
        when(processInstance.getId()).thenReturn("process-instance-1");
        when(processInstance.getProcessId()).thenReturn("test-process");
        when(processInstance.getProcessVersion()).thenReturn("1.0.0");
        when(processInstance.getState()).thenReturn(ProcessInstance.STATE_ACTIVE);

        Span nodeASpan = org.mockito.Mockito.mock(Span.class);
        Span nodeBSpan = org.mockito.Mockito.mock(Span.class);

        when(spanManager.createNodeSpanWithContext(anyString(), anyString(), anyString(), anyString(), eq("NodeA"), any(), any(), any()))
                .thenReturn(nodeASpan);
        when(spanManager.createNodeSpanWithContext(anyString(), anyString(), anyString(), anyString(), eq("NodeB"), any(), any(), any()))
                .thenReturn(nodeBSpan);

        org.kie.api.event.process.ProcessNodeTriggeredEvent event =
                org.mockito.Mockito.mock(org.kie.api.event.process.ProcessNodeTriggeredEvent.class);
        when(event.getNodeInstance()).thenReturn((KogitoNodeInstance) jbpmNodeInstance);
        when(event.getProcessInstance()).thenReturn(processInstance);

        when(((KogitoNodeInstance) jbpmNodeInstance).getNodeName()).thenReturn("NodeA");
        eventListener.beforeNodeTriggered(event);

        when(((KogitoNodeInstance) jbpmNodeInstance).getNodeName()).thenReturn("NodeB");
        eventListener.beforeNodeTriggered(event);

        verify(spanManager).createNodeSpanWithContext(eq("process-instance-1"), eq("test-process"), eq("1.0.0"), eq("ACTIVE"), eq("NodeA"), isNull(), isNull(), any());
        verify(spanManager).addProcessEvent(nodeASpan, "node.started", "Node execution started: NodeA");

        verify(spanManager).createNodeSpanWithContext(eq("process-instance-1"), eq("test-process"), eq("1.0.0"), eq("ACTIVE"), eq("NodeB"), isNull(), isNull(), any());
        verify(spanManager).addProcessEvent(nodeBSpan, "node.started", "Node execution started: NodeB");
    }

    @Test
    public void shouldAddProcessStartEventForStartNode() {
        when(config.events()).thenReturn(eventConfig);
        when(eventConfig.enabled()).thenReturn(true);

        when(((KogitoNodeInstance) jbpmNodeInstance).getNodeName()).thenReturn("Start");
        when(processInstance.getId()).thenReturn("process-instance-1");
        when(processInstance.getProcessId()).thenReturn("test-process");
        when(processInstance.getProcessVersion()).thenReturn("1.0.0");
        when(processInstance.getState()).thenReturn(ProcessInstance.STATE_ACTIVE);

        when(spanManager.createNodeSpanWithContext(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(mockSpan);

        org.kie.api.event.process.ProcessNodeTriggeredEvent event =
                org.mockito.Mockito.mock(org.kie.api.event.process.ProcessNodeTriggeredEvent.class);
        when(event.getNodeInstance()).thenReturn((KogitoNodeInstance) jbpmNodeInstance);
        when(event.getProcessInstance()).thenReturn(processInstance);

        eventListener.beforeNodeTriggered(event);

        verify(spanManager).createNodeSpanWithContext(eq("process-instance-1"), eq("test-process"), eq("1.0.0"), eq("ACTIVE"), eq("Start"), isNull(), isNull(), any());
        verify(spanManager).addProcessEvent(mockSpan, "node.started", "Node execution started: Start");
        verify(spanManager).addProcessEvent(eq(mockSpan), eq("process.instance.start"), any(Attributes.class));
    }

    @Test
    public void shouldNotAddProcessStartEventForNonStartNodes() {
        when(((KogitoNodeInstance) jbpmNodeInstance).getNodeName()).thenReturn("ChooseOnLanguage");
        when(processInstance.getId()).thenReturn("process-instance-1");
        when(processInstance.getProcessId()).thenReturn("test-process");
        when(processInstance.getProcessVersion()).thenReturn("1.0.0");
        when(processInstance.getState()).thenReturn(ProcessInstance.STATE_ACTIVE);

        when(spanManager.createNodeSpanWithContext(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(mockSpan);

        org.kie.api.event.process.ProcessNodeTriggeredEvent event =
                org.mockito.Mockito.mock(org.kie.api.event.process.ProcessNodeTriggeredEvent.class);
        when(event.getNodeInstance()).thenReturn((KogitoNodeInstance) jbpmNodeInstance);
        when(event.getProcessInstance()).thenReturn(processInstance);

        eventListener.beforeNodeTriggered(event);

        verify(spanManager).createNodeSpanWithContext(eq("process-instance-1"), eq("test-process"), eq("1.0.0"), eq("ACTIVE"), eq("ChooseOnLanguage"), isNull(), isNull(), any());
        verify(spanManager).addProcessEvent(mockSpan, "node.started", "Node execution started: ChooseOnLanguage");
        verify(spanManager, never()).addProcessEvent(eq(mockSpan), eq("process.instance.start"), any(Attributes.class));
    }

    @Test
    public void shouldCompleteSpanOnAfterNodeLeft() {
        when(((KogitoNodeInstance) jbpmNodeInstance).getNodeName()).thenReturn("TestNode");
        when(processInstance.getId()).thenReturn("process-instance-1");
        when(processInstance.getState()).thenReturn(ProcessInstance.STATE_ACTIVE);

        when(spanManager.getActiveNodeSpan("process-instance-1", "TestNode")).thenReturn(mockSpan);

        org.kie.api.event.process.ProcessNodeLeftEvent event =
                org.mockito.Mockito.mock(org.kie.api.event.process.ProcessNodeLeftEvent.class);
        when(event.getNodeInstance()).thenReturn((KogitoNodeInstance) jbpmNodeInstance);
        when(event.getProcessInstance()).thenReturn(processInstance);

        eventListener.afterNodeLeft(event);

        verify(spanManager).addProcessEvent(mockSpan, "node.completed", "Node execution completed: TestNode");
        verify(spanManager).completeNodeSpan("process-instance-1", "TestNode");
    }

    @Test
    public void shouldNotCompleteSpanWhenNoActiveSpanExists() {
        when(((KogitoNodeInstance) jbpmNodeInstance).getNodeName()).thenReturn("TestNode");
        when(processInstance.getId()).thenReturn("process-instance-1");
        when(processInstance.getState()).thenReturn(ProcessInstance.STATE_ACTIVE);

        when(spanManager.getActiveNodeSpan("process-instance-1", "TestNode")).thenReturn(null);

        org.kie.api.event.process.ProcessNodeLeftEvent event =
                org.mockito.Mockito.mock(org.kie.api.event.process.ProcessNodeLeftEvent.class);
        when(event.getNodeInstance()).thenReturn((KogitoNodeInstance) jbpmNodeInstance);
        when(event.getProcessInstance()).thenReturn(processInstance);

        eventListener.afterNodeLeft(event);

        verify(spanManager, never()).addProcessEvent(any(), anyString(), anyString());
        verify(spanManager, never()).completeNodeSpan(anyString(), anyString());
    }

    @Test
    public void shouldExtractStateMetadataAndParentIdFromEvent() {
        java.util.Map<String, Object> nodeDefinitionMetadata = new java.util.HashMap<>();
        nodeDefinitionMetadata.put("state", "TestState");

        org.jbpm.workflow.instance.NodeInstance jbpmNodeInst = org.mockito.Mockito.mock(
                org.jbpm.workflow.instance.NodeInstance.class,
                org.mockito.Mockito.withSettings().extraInterfaces(KogitoNodeInstance.class));
        org.kie.api.definition.process.Node nodeDef = org.mockito.Mockito.mock(org.kie.api.definition.process.Node.class);

        when(((KogitoNodeInstance) jbpmNodeInst).getNodeName()).thenReturn("TestNode");
        when(jbpmNodeInst.getNode()).thenReturn(nodeDef);
        when(nodeDef.getMetaData()).thenReturn(nodeDefinitionMetadata);
        when(processInstance.getId()).thenReturn("process-instance-1");
        when(processInstance.getProcessId()).thenReturn("test-process");
        when(processInstance.getProcessVersion()).thenReturn("1.0.0");
        when(processInstance.getState()).thenReturn(ProcessInstance.STATE_ACTIVE);
        when(processInstance.getParentProcessInstanceId()).thenReturn("parent-123");

        when(spanManager.createNodeSpanWithContext(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(mockSpan);

        org.kie.api.event.process.ProcessNodeTriggeredEvent event =
                org.mockito.Mockito.mock(org.kie.api.event.process.ProcessNodeTriggeredEvent.class);
        when(event.getNodeInstance()).thenReturn((KogitoNodeInstance) jbpmNodeInst);
        when(event.getProcessInstance()).thenReturn(processInstance);

        eventListener.beforeNodeTriggered(event);

        verify(spanManager).createNodeSpanWithContext("process-instance-1", "test-process", "1.0.0", "ACTIVE", "TestNode", "TestState", "parent-123", new HashMap<>());
    }

}
