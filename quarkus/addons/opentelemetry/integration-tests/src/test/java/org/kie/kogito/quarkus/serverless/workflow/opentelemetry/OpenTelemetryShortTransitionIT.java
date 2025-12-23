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

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.OpenTelemetryTestUtils.*;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.*;

/**
 * Integration tests for OpenTelemetry "short" transition mode.
 * In short mode, only ONE span per workflow execution should be created,
 * unlike "long" mode which creates spans for each node.
 */
@QuarkusIntegrationTest
@QuarkusTestResource(value = ShortTransitionModeTestResource.class, restrictToAnnotatedClass = true)
@QuarkusTestResource(OtlpMockTestResource.class)
@QuarkusTestResource(TokenPropagationExternalServicesMock.class)
@QuarkusTestResource(KeycloakServiceMock.class)
public class OpenTelemetryShortTransitionIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenTelemetryShortTransitionIT.class);

    @BeforeEach
    public void cleanup() throws InterruptedException {
        OtlpMockTestResource.clearRequests();
        TokenPropagationExternalServicesMock.getInstance().resetRequests();

        for (int i = 0; i < 5; i++) {
            Thread.sleep(200);
            OtlpMockTestResource.clearRequests();
            if (OtlpMockTestResource.getSpanCount() == 0) {
                break;
            }
        }
    }

    /**
     * Test that short mode creates only a single span per workflow execution.
     * In "short" transition mode, the entire workflow execution should be represented
     * by one span, regardless of how many nodes are executed.
     */
    @Test
    void shouldCreateSingleSpanPerWorkflowExecution() {
        executeWorkflowWithTxn("/greet", buildGreetBody("John", "English"),
                "workflow-short-single-span-txn", 201);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<SpanData> spans = OtlpMockTestResource.getSpans();
            List<SpanData> workflowSpans = filterWorkflowSpans(spans);

            LOGGER.info("Total workflow spans for short mode test: {}", workflowSpans.size());

            List<SpanData> testSpans = filterSpansByTransactionId(workflowSpans,
                    "workflow-short-single-span-txn");

            assertThat(testSpans)
                    .withFailMessage("Short transition mode should create exactly 1 span per workflow execution. Found: %d",
                            testSpans.size())
                    .hasSize(1);

            SpanData workflowSpan = testSpans.get(0);
            assertThat(workflowSpan.getName()).startsWith("sonataflow.process.greet.execute");
        });
    }

    /**
     * Test that the single span in short mode contains all mandatory process attributes.
     */
    @Test
    void shouldContainProcessAttributes() {
        executeWorkflowWithTxn("/greet", buildGreetBody("Alice", "Spanish"),
                "workflow-short-attributes-txn", 201);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<SpanData> spans = OtlpMockTestResource.getSpans();
            List<SpanData> workflowSpans = filterWorkflowSpans(spans);

            List<SpanData> testSpans = filterSpansByTransactionId(workflowSpans,
                    "workflow-short-attributes-txn");

            assertThat(testSpans).hasSize(1);

            SpanData workflowSpan = testSpans.get(0);

            assertThat(workflowSpan.getAttributes().get(SONATAFLOW_PROCESS_ID))
                    .isEqualTo("greet");
            assertThat(workflowSpan.getAttributes().get(SONATAFLOW_PROCESS_INSTANCE_ID))
                    .isNotNull();
            assertThat(workflowSpan.getAttributes().get(SONATAFLOW_PROCESS_VERSION))
                    .isEqualTo("1.0");
            assertThat(workflowSpan.getAttributes().get(SONATAFLOW_PROCESS_INSTANCE_STATE))
                    .isNotNull();
            assertThat(workflowSpan.getAttributes().get(SERVICE_NAME))
                    .isNotNull();
            assertThat(workflowSpan.getAttributes().get(SERVICE_VERSION))
                    .isNotNull();
        });
    }

    /**
     * Test that transaction ID from header is propagated to the workflow span.
     */
    @Test
    void shouldSetTransactionIdFromHeader() {
        executeWorkflowWithTxn("/greet", buildGreetBody("Bob", "English"),
                "workflow-short-txn-header-test", 201);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<SpanData> spans = OtlpMockTestResource.getSpans();
            List<SpanData> workflowSpans = filterWorkflowSpans(spans);

            List<SpanData> testSpans = filterSpansByTransactionId(workflowSpans,
                    "workflow-short-txn-header-test");

            assertThat(testSpans).hasSize(1);

            SpanData workflowSpan = testSpans.get(0);
            String transactionId = workflowSpan.getAttributes().get(SONATAFLOW_TRANSACTION_ID);

            assertThat(transactionId)
                    .isEqualTo("workflow-short-txn-header-test");
        });
    }

    /**
     * Test that concurrent workflow executions create independent spans.
     * Each workflow should have its own single span, with unique trace IDs.
     */
    @Test
    void shouldCreateIndependentSpansForConcurrentWorkflows() {
        for (int i = 0; i < 3; i++) {
            executeWorkflowWithTxn("/greet", buildGreetBody("User" + i, "English"),
                    "workflow-short-concurrent-txn-" + i, 201);
        }

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            List<SpanData> spans = OtlpMockTestResource.getSpans();
            List<SpanData> workflowSpans = filterWorkflowSpans(spans);

            List<SpanData> testSpans = workflowSpans.stream()
                    .filter(span -> {
                        String txnId = span.getAttributes().get(SONATAFLOW_TRANSACTION_ID);
                        return txnId != null && txnId.startsWith("workflow-short-concurrent-txn-");
                    })
                    .collect(Collectors.toList());

            LOGGER.info("Found {} spans for concurrent workflow test", testSpans.size());

            assertThat(testSpans)
                    .withFailMessage("Expected exactly 3 spans (one per workflow execution) in short mode")
                    .hasSize(3);

            Set<String> traceIds = testSpans.stream()
                    .map(SpanData::getTraceId)
                    .collect(Collectors.toSet());

            assertThat(traceIds)
                    .withFailMessage("Each workflow execution should have a unique trace ID")
                    .hasSize(3);

            Set<String> transactionIds = testSpans.stream()
                    .map(span -> span.getAttributes().get(SONATAFLOW_TRANSACTION_ID))
                    .collect(Collectors.toSet());

            assertThat(transactionIds).containsExactlyInAnyOrder(
                    "workflow-short-concurrent-txn-0",
                    "workflow-short-concurrent-txn-1",
                    "workflow-short-concurrent-txn-2");
        });
    }

    /**
     * Test that short mode span does not have node-level information.
     * Since short mode operates at workflow level, no node attribute should be present.
     */
    @Test
    void shouldNotContainNodeLevelAttributes() {
        executeWorkflowWithTxn("/greet", buildGreetBody("Test", "English"),
                "workflow-short-no-node-attrs-txn", 201);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<SpanData> spans = OtlpMockTestResource.getSpans();
            List<SpanData> workflowSpans = filterWorkflowSpans(spans);

            List<SpanData> testSpans = filterSpansByTransactionId(workflowSpans,
                    "workflow-short-no-node-attrs-txn");

            assertThat(testSpans).hasSize(1);

            SpanData workflowSpan = testSpans.get(0);
            String nodeName = workflowSpan.getAttributes().get(SONATAFLOW_PROCESS_INSTANCE_NODE);

            assertThat(nodeName)
                    .withFailMessage("Short mode spans should not have node-level attributes")
                    .isNull();
        });
    }

    /**
     * Test that tracker headers are propagated to the workflow span in short mode.
     */
    @Test
    void shouldPropagateTrackerAttributes() {
        executeWorkflowWithTrackers("/greet", buildGreetBody("TrackerTest", "Spanish"),
                "workflow-short-tracker-test-txn",
                "customer-999", "session-abc", 201);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<SpanData> spans = OtlpMockTestResource.getSpans();
            List<SpanData> workflowSpans = filterWorkflowSpans(spans);

            List<SpanData> testSpans = filterSpansByTransactionId(workflowSpans,
                    "workflow-short-tracker-test-txn");

            assertThat(testSpans).hasSize(1);

            SpanData workflowSpan = testSpans.get(0);
            validateTransactionAndTrackerAttributes(workflowSpan,
                    "workflow-short-tracker-test-txn",
                    "customer-999",
                    "session-abc");
        });
    }

    /**
     * Test that successful workflow execution has OK span status.
     */
    @Test
    void shouldHaveOkStatusOnSuccessfulWorkflow() {
        executeWorkflowWithTxn("/greet", buildGreetBody("StatusTest", "English"),
                "workflow-short-status-ok-txn", 201);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<SpanData> spans = OtlpMockTestResource.getSpans();
            List<SpanData> workflowSpans = filterWorkflowSpans(spans);

            List<SpanData> testSpans = filterSpansByTransactionId(workflowSpans,
                    "workflow-short-status-ok-txn");

            assertThat(testSpans).hasSize(1);

            SpanData workflowSpan = testSpans.get(0);
            assertThat(workflowSpan.getStatus().getStatusCode())
                    .withFailMessage("Successful workflow should have OK or UNSET status, not ERROR")
                    .isNotEqualTo(StatusCode.ERROR);
        });
    }

    /**
     * Test that process start and complete events are present on the span.
     */
    @Test
    void shouldContainProcessLifecycleEvents() {
        executeWorkflowWithTxn("/greet", buildGreetBody("EventTest", "Spanish"),
                "workflow-short-events-txn", 201);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<SpanData> spans = OtlpMockTestResource.getSpans();
            List<SpanData> workflowSpans = filterWorkflowSpans(spans);

            List<SpanData> testSpans = filterSpansByTransactionId(workflowSpans,
                    "workflow-short-events-txn");

            assertThat(testSpans).hasSize(1);

            SpanData workflowSpan = testSpans.get(0);
            List<EventData> events = workflowSpan.getEvents();

            LOGGER.info("Found {} events on workflow span", events.size());
            events.forEach(event -> LOGGER.info("Event: {}", event.getName()));

            List<String> eventNames = events.stream()
                    .map(EventData::getName)
                    .collect(Collectors.toList());

            assertThat(eventNames)
                    .withFailMessage("Span should contain process.instance.start event")
                    .contains("process.instance.start");

            assertThat(eventNames)
                    .withFailMessage("Span should contain process.instance.complete event")
                    .contains("process.instance.complete");
        });
    }

    /**
     * Test that process instance ID is consistent in span attributes.
     */
    @Test
    void shouldHaveConsistentProcessInstanceId() {
        executeWorkflowWithTxn("/greet", buildGreetBody("InstanceTest", "English"),
                "workflow-short-instance-id-txn", 201);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<SpanData> spans = OtlpMockTestResource.getSpans();
            List<SpanData> workflowSpans = filterWorkflowSpans(spans);

            List<SpanData> testSpans = filterSpansByTransactionId(workflowSpans,
                    "workflow-short-instance-id-txn");

            assertThat(testSpans).hasSize(1);

            SpanData workflowSpan = testSpans.get(0);
            String processInstanceId = workflowSpan.getAttributes().get(SONATAFLOW_PROCESS_INSTANCE_ID);

            assertThat(processInstanceId)
                    .withFailMessage("Process instance ID should not be null or empty")
                    .isNotNull()
                    .isNotEmpty();

            assertThat(processInstanceId)
                    .withFailMessage("Process instance ID should be a valid UUID format")
                    .matches("[a-f0-9\\-]+");
        });
    }

    /**
     * Test that node events are captured and added to the process span.
     * In short mode, node-level activity should be represented as span events.
     */
    @Test
    void shouldContainNodeEventsOnProcessSpan() {
        executeWorkflowWithTxn("/greet", buildGreetBody("NodeEventTest", "English"),
                "workflow-short-node-events-txn", 201);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<SpanData> spans = OtlpMockTestResource.getSpans();
            List<SpanData> workflowSpans = filterWorkflowSpans(spans);

            List<SpanData> testSpans = filterSpansByTransactionId(workflowSpans,
                    "workflow-short-node-events-txn");

            assertThat(testSpans).hasSize(1);

            SpanData workflowSpan = testSpans.get(0);
            List<EventData> events = workflowSpan.getEvents();

            LOGGER.info("Found {} events on workflow span", events.size());
            events.forEach(event -> LOGGER.info("Event: {} | Attributes: {}",
                    event.getName(), event.getAttributes()));

            List<String> eventNames = events.stream()
                    .map(EventData::getName)
                    .collect(Collectors.toList());

            assertThat(eventNames)
                    .withFailMessage("Span should contain node.triggered events")
                    .anyMatch(name -> name.equals("node.triggered"));

            assertThat(eventNames)
                    .withFailMessage("Span should contain node.left events")
                    .anyMatch(name -> name.equals("node.left"));
        });
    }
}
