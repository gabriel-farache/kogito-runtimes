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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.kie.api.event.process.ProcessCompletedEvent;
import org.kie.api.event.process.ProcessNodeLeftEvent;
import org.kie.api.event.process.ProcessNodeTriggeredEvent;
import org.kie.api.event.process.ProcessStartedEvent;
import org.kie.kogito.event.EventManager;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;
import org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes;
import org.kie.kogito.services.uow.CollectingUnitOfWork;
import org.kie.kogito.uow.WorkUnit;

public class OtelCollectingUnitOfWork extends CollectingUnitOfWork implements OtelEventDataProvider {

    private final Map<String, ProcessStartedEvent> savedStartEvents = new ConcurrentHashMap<>();
    private final Map<String, ProcessCompletedEvent> savedCompleteEvents = new ConcurrentHashMap<>();
    private final Map<String, ProcessNodeTriggeredEvent> savedNodeTriggeredEvents = new ConcurrentHashMap<>();
    private final List<NodeEventData> collectedNodeEvents = Collections.synchronizedList(new ArrayList<>());

    public OtelCollectingUnitOfWork(EventManager eventManager) {
        super(eventManager);
    }

    @Override
    public void end() {
        for (WorkUnit<?> work : sorted()) {
            Object data = work.data();
            if (data instanceof ProcessStartedEvent event) {
                String processInstanceId = event.getProcessInstance().getId();
                savedStartEvents.put(processInstanceId, event);
            }
            if (data instanceof ProcessCompletedEvent event) {
                String processInstanceId = event.getProcessInstance().getId();
                savedCompleteEvents.put(processInstanceId, event);
            }
            if (data instanceof ProcessNodeTriggeredEvent event) {
                String processInstanceId = event.getProcessInstance().getId();
                savedNodeTriggeredEvents.putIfAbsent(processInstanceId, event);
                collectedNodeEvents.add(new NodeEventData(
                        processInstanceId,
                        SonataFlowOtelAttributes.Events.NODE_TRIGGERED,
                        event.getNodeInstance().getNodeName(),
                        Instant.now()));
            }
            if (data instanceof ProcessNodeLeftEvent event) {
                collectedNodeEvents.add(new NodeEventData(
                        event.getProcessInstance().getId(),
                        SonataFlowOtelAttributes.Events.NODE_LEFT,
                        event.getNodeInstance().getNodeName(),
                        Instant.now()));
            }
        }
        super.end();
    }

    @Override
    public Optional<KogitoProcessInstance> getProcessInstanceForSpan() {
        Optional<KogitoProcessInstance> rootFromStart = savedStartEvents.values().stream()
                .filter(event -> event.getProcessInstance() instanceof KogitoProcessInstance)
                .map(event -> (KogitoProcessInstance) event.getProcessInstance())
                .filter(instance -> instance.getParentProcessInstanceId() == null)
                .findFirst();

        if (rootFromStart.isPresent()) {
            return rootFromStart;
        }

        Optional<KogitoProcessInstance> rootFromNode = savedNodeTriggeredEvents.values().stream()
                .filter(event -> event.getProcessInstance() instanceof KogitoProcessInstance)
                .map(event -> (KogitoProcessInstance) event.getProcessInstance())
                .filter(instance -> instance.getParentProcessInstanceId() == null)
                .findFirst();

        if (rootFromNode.isPresent()) {
            return rootFromNode;
        }

        Optional<KogitoProcessInstance> anyFromStart = savedStartEvents.values().stream()
                .filter(event -> event.getProcessInstance() instanceof KogitoProcessInstance)
                .map(event -> (KogitoProcessInstance) event.getProcessInstance())
                .findFirst();

        if (anyFromStart.isPresent()) {
            return anyFromStart;
        }

        return savedNodeTriggeredEvents.values().stream()
                .filter(event -> event.getProcessInstance() instanceof KogitoProcessInstance)
                .map(event -> (KogitoProcessInstance) event.getProcessInstance())
                .findFirst();
    }

    @Override
    public Optional<ProcessCompletedEvent> getProcessCompletedEvent(String processInstanceId) {
        return Optional.ofNullable(savedCompleteEvents.get(processInstanceId));
    }

    @Override
    public boolean isResumedExecution(String processInstanceId) {
        return !savedStartEvents.containsKey(processInstanceId)
                && savedNodeTriggeredEvents.containsKey(processInstanceId);
    }

    @Override
    public List<NodeEventData> getCollectedNodeEvents(String processInstanceId) {
        return collectedNodeEvents.stream()
                .filter(event -> event.processInstanceId().equals(processInstanceId))
                .toList();
    }
}
