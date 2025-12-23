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

import org.junit.jupiter.api.Test;
import org.kie.kogito.process.ProcessInstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.ProcessStates;

class ProcessStateConverterTest {

    @Test
    void shouldConvertStatePending() {
        String result = ProcessStateConverter.fromState(ProcessInstance.STATE_PENDING);

        assertThat(result).isEqualTo(ProcessStates.PENDING);
    }

    @Test
    void shouldConvertStateActive() {
        String result = ProcessStateConverter.fromState(ProcessInstance.STATE_ACTIVE);

        assertThat(result).isEqualTo(ProcessStates.ACTIVE);
    }

    @Test
    void shouldConvertStateCompleted() {
        String result = ProcessStateConverter.fromState(ProcessInstance.STATE_COMPLETED);

        assertThat(result).isEqualTo(ProcessStates.COMPLETED);
    }

    @Test
    void shouldConvertStateAborted() {
        String result = ProcessStateConverter.fromState(ProcessInstance.STATE_ABORTED);

        assertThat(result).isEqualTo(ProcessStates.ABORTED);
    }

    @Test
    void shouldConvertStateSuspended() {
        String result = ProcessStateConverter.fromState(ProcessInstance.STATE_SUSPENDED);

        assertThat(result).isEqualTo(ProcessStates.SUSPENDED);
    }

    @Test
    void shouldConvertStateError() {
        String result = ProcessStateConverter.fromState(ProcessInstance.STATE_ERROR);

        assertThat(result).isEqualTo(ProcessStates.ERROR);
    }

    @Test
    void shouldReturnUnknownForInvalidState() {
        String result = ProcessStateConverter.fromState(999);

        assertThat(result).isEqualTo(ProcessStates.UNKNOWN);
    }

    @Test
    void shouldReturnUnknownForNegativeState() {
        String result = ProcessStateConverter.fromState(-1);

        assertThat(result).isEqualTo(ProcessStates.UNKNOWN);
    }
}
