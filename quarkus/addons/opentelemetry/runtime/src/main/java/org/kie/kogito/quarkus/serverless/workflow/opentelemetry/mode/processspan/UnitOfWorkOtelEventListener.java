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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;
import org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes;
import org.kie.kogito.quarkus.serverless.workflow.opentelemetry.common.HeaderContextExtractor;
import org.kie.kogito.quarkus.serverless.workflow.opentelemetry.config.SonataFlowOtelConfig;
import org.kie.kogito.quarkus.serverless.workflow.opentelemetry.logging.OtelLogCollector;
import org.kie.kogito.quarkus.serverless.workflow.opentelemetry.util.ProcessStateConverter;
import org.kie.kogito.uow.UnitOfWork;
import org.kie.kogito.uow.events.UnitOfWorkAbortEvent;
import org.kie.kogito.uow.events.UnitOfWorkEndEvent;
import org.kie.kogito.uow.events.UnitOfWorkEventListener;
import org.kie.kogito.uow.events.UnitOfWorkStartEvent;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;

import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.PROCESS_INSTANCE_ID;

public class UnitOfWorkOtelEventListener implements UnitOfWorkEventListener {

    private final ProcessSpanManager spanManager;
    private final SonataFlowOtelConfig config;
    private final HeaderContextExtractor headerExtractor;
    private final Map<Integer, Instant> unitOfWorkStartTimes = new ConcurrentHashMap<>();

    public UnitOfWorkOtelEventListener(
            ProcessSpanManager spanManager,
            SonataFlowOtelConfig config,
            HeaderContextExtractor headerExtractor) {
        this.spanManager = spanManager;
        this.config = config;
        this.headerExtractor = headerExtractor;
    }

    @Override
    public void onBeforeStartEvent(UnitOfWorkStartEvent event) {
        if (!isSpanCreationEnabled()) {
            return;
        }
        unitOfWorkStartTimes.put(System.identityHashCode(event.getUnitOfWork()), Instant.now());
    }

    @Override
    public void onAfterEndEvent(UnitOfWorkEndEvent event) {
        if (!isSpanCreationEnabled()) {
            return;
        }

        UnitOfWork unitOfWork = event.getUnitOfWork();
        if (!(unitOfWork instanceof OtelEventDataProvider provider)) {
            unitOfWorkStartTimes.remove(System.identityHashCode(unitOfWork));
            return;
        }

        KogitoProcessInstance rootProcess = provider.getProcessInstanceForSpan().orElse(null);
        if (rootProcess == null) {
            unitOfWorkStartTimes.remove(System.identityHashCode(unitOfWork));
            return;
        }

        String processInstanceId = rootProcess.getId();
        String processId = rootProcess.getProcessId();
        String processVersion = rootProcess.getProcessVersion();
        String processState = ProcessStateConverter.fromState(rootProcess.getState());
        String parentProcessInstanceId = rootProcess.getParentProcessInstanceId();

        Map<String, String> headerContext = headerExtractor.extractFromProcessHeaders(rootProcess.getHeaders());

        Instant startTime = unitOfWorkStartTimes.remove(System.identityHashCode(unitOfWork));

        Span span = spanManager.createProcessSpan(
                processInstanceId,
                processId,
                processVersion,
                processState,
                parentProcessInstanceId,
                headerContext,
                startTime);

        if (span != null) {
            finalizeSpan(span, provider, processInstanceId, StatusCode.OK, null, true);
        }
    }

    @Override
    public void onAfterAbortEvent(UnitOfWorkAbortEvent event) {
        if (!isSpanCreationEnabled()) {
            return;
        }

        UnitOfWork unitOfWork = event.getUnitOfWork();
        Instant startTime = unitOfWorkStartTimes.remove(System.identityHashCode(unitOfWork));

        if (!(unitOfWork instanceof OtelEventDataProvider provider)) {
            return;
        }

        KogitoProcessInstance rootProcess = provider.getProcessInstanceForSpan().orElse(null);
        if (rootProcess == null) {
            return;
        }

        String processInstanceId = rootProcess.getId();
        String processId = rootProcess.getProcessId();
        String processVersion = rootProcess.getProcessVersion();
        String processState = ProcessStateConverter.fromState(rootProcess.getState());
        String parentProcessInstanceId = rootProcess.getParentProcessInstanceId();

        Map<String, String> headerContext = headerExtractor.extractFromProcessHeaders(rootProcess.getHeaders());

        Span span = spanManager.createProcessSpan(
                processInstanceId,
                processId,
                processVersion,
                processState,
                parentProcessInstanceId,
                headerContext,
                startTime);

        if (span != null) {
            finalizeSpan(span, provider, processInstanceId, StatusCode.ERROR, "Process failed with error", false);
        }
    }

    private void finalizeSpan(
            Span span,
            OtelEventDataProvider provider,
            String processInstanceId,
            StatusCode statusCode,
            String statusDescription,
            boolean includeCompletionEvent) {
        if (provider.isResumedExecution(processInstanceId)) {
            Attributes resumeAttrs = Attributes.of(PROCESS_INSTANCE_ID, processInstanceId);
            spanManager.addProcessEvent(span, SonataFlowOtelAttributes.Events.PROCESS_INSTANCE_RESUME, resumeAttrs);
        } else {
            Attributes startAttrs = Attributes.of(PROCESS_INSTANCE_ID, processInstanceId);
            spanManager.addProcessEvent(span, SonataFlowOtelAttributes.Events.PROCESS_INSTANCE_START, startAttrs);
        }

        for (OtelEventDataProvider.NodeEventData nodeEvent : provider.getCollectedNodeEvents(processInstanceId)) {
            Attributes nodeAttrs = Attributes.of(AttributeKey.stringKey("node.name"), nodeEvent.nodeName());
            spanManager.addProcessEvent(span, nodeEvent.eventName(), nodeAttrs);
        }

        addCollectedLogEvents(span);

        if (includeCompletionEvent && provider.getProcessCompletedEvent(processInstanceId).isPresent()) {
            Attributes completeAttrs = Attributes.of(PROCESS_INSTANCE_ID, processInstanceId);
            spanManager.addProcessEvent(span, SonataFlowOtelAttributes.Events.PROCESS_INSTANCE_COMPLETE, completeAttrs);
        }

        spanManager.endProcessSpan(processInstanceId, statusCode, statusDescription);
    }

    private void addCollectedLogEvents(Span span) {
        for (OtelLogCollector.LogEventData logEvent : OtelLogCollector.getAndClear()) {
            Attributes logAttrs = Attributes.of(
                    SonataFlowOtelAttributes.LOG_LEVEL, logEvent.level(),
                    SonataFlowOtelAttributes.LOG_LOGGER, logEvent.logger(),
                    SonataFlowOtelAttributes.LOG_MESSAGE, logEvent.message(),
                    SonataFlowOtelAttributes.LOG_THREAD_NAME, logEvent.threadName(),
                    SonataFlowOtelAttributes.LOG_THREAD_ID, logEvent.threadId());
            spanManager.addProcessEvent(span, SonataFlowOtelAttributes.Events.LOG_MESSAGE, logAttrs);
        }
    }

    private boolean isSpanCreationEnabled() {
        return config.spans().enabled();
    }
}
