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

import org.kie.api.event.process.ProcessCompletedEvent;
import org.kie.api.event.process.ProcessNodeLeftEvent;
import org.kie.api.event.process.ProcessNodeTriggeredEvent;
import org.kie.api.event.process.ProcessStartedEvent;
import org.kie.api.event.process.ProcessVariableChangedEvent;
import org.kie.kogito.internal.process.event.KogitoProcessEventListener;

/**
 * A no-operation implementation of KogitoProcessEventListener.
 * Used when the node-level event listener should not be active (e.g., in short transition mode).
 */
public class NoOpKogitoProcessEventListener implements KogitoProcessEventListener {

    @Override
    public void beforeProcessStarted(ProcessStartedEvent event) {
        // No-op
    }

    @Override
    public void afterProcessStarted(ProcessStartedEvent event) {
        // No-op
    }

    @Override
    public void beforeProcessCompleted(ProcessCompletedEvent event) {
        // No-op
    }

    @Override
    public void afterProcessCompleted(ProcessCompletedEvent event) {
        // No-op
    }

    @Override
    public void beforeNodeTriggered(ProcessNodeTriggeredEvent event) {
        // No-op
    }

    @Override
    public void afterNodeTriggered(ProcessNodeTriggeredEvent event) {
        // No-op
    }

    @Override
    public void beforeNodeLeft(ProcessNodeLeftEvent event) {
        // No-op
    }

    @Override
    public void afterNodeLeft(ProcessNodeLeftEvent event) {
        // No-op
    }

    @Override
    public void beforeVariableChanged(ProcessVariableChangedEvent event) {
        // No-op
    }

    @Override
    public void afterVariableChanged(ProcessVariableChangedEvent event) {
        // No-op
    }
}
