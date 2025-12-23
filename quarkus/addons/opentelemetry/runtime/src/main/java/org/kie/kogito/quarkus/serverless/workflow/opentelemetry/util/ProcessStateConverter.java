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

import org.kie.kogito.process.ProcessInstance;

import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.ProcessStates;

public final class ProcessStateConverter {

    private ProcessStateConverter() {
    }

    public static String fromState(int state) {
        return switch (state) {
            case ProcessInstance.STATE_PENDING -> ProcessStates.PENDING;
            case ProcessInstance.STATE_ACTIVE -> ProcessStates.ACTIVE;
            case ProcessInstance.STATE_COMPLETED -> ProcessStates.COMPLETED;
            case ProcessInstance.STATE_ABORTED -> ProcessStates.ABORTED;
            case ProcessInstance.STATE_SUSPENDED -> ProcessStates.SUSPENDED;
            case ProcessInstance.STATE_ERROR -> ProcessStates.ERROR;
            default -> ProcessStates.UNKNOWN;
        };
    }
}
