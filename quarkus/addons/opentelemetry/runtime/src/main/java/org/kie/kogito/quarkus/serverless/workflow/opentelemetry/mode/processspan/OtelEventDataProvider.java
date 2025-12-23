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
import java.util.List;
import java.util.Optional;

import org.kie.api.event.process.ProcessCompletedEvent;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;

public interface OtelEventDataProvider {
    /**
     * Returns the most appropriate process instance for creating a span.
     *
     * <p>
     * Selection priority:
     * <ol>
     * <li>Root process instance from start events (parentProcessInstanceId == null)</li>
     * <li>Root process instance from node triggered events</li>
     * <li>Any subflow process instance (for subflow-only execution scenarios)</li>
     * </ol>
     *
     * <p>
     * This method handles scenarios where only a subflow executes (e.g., CloudEvent
     * resuming a waiting subflow after application restart).
     *
     * @return the process instance for span creation, or empty if no process executed
     */
    Optional<KogitoProcessInstance> getProcessInstanceForSpan();

    Optional<ProcessCompletedEvent> getProcessCompletedEvent(String processInstanceId);

    boolean isResumedExecution(String processInstanceId);

    List<NodeEventData> getCollectedNodeEvents(String processInstanceId);

    record NodeEventData(String processInstanceId, String eventName, String nodeName, Instant timestamp) {
    }
}
