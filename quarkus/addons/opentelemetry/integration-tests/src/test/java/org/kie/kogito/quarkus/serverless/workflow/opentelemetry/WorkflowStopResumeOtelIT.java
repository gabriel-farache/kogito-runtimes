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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kie.kogito.quarkus.serverless.workflow.opentelemetry.containers.PersistentPostgreSQLContainer;
import org.kie.kogito.quarkus.serverless.workflow.opentelemetry.containers.QuarkusAppContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;

import jakarta.ws.rs.core.MediaType;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.OpenTelemetryTestUtils.*;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.*;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.SONATAFLOW_PROCESS_ID;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.SONATAFLOW_PROCESS_INSTANCE_ID;
import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.SONATAFLOW_PROCESS_INSTANCE_NODE;

/**
 * Integration test for workflow stop/resume scenarios with OpenTelemetry span validation.
 * This test uses TestContainers to manage the application lifecycle, allowing true app restarts
 * while PostgreSQL persists workflow state.
 *
 * Test Sequence:
 * 1. Start app -> Start workflow -> After 10s send /loud -> Send /quiet -> workflow completes
 * 2. Start workflow -> After 10s restart app -> Send /loud (no error) -> Send /quiet
 * 3. Start new workflow -> After 10s send /loud (10s timeout) -> Send /quiet -> workflow completes
 * 4. Start workflow -> After 10s send /loud -> Restart app -> Send /quiet -> workflow completes
 *
 * All spans must be flat (same parent) - strict hierarchy validation.
 *
 * Note: Docker API version compatibility is configured via docker-java.properties
 * to support Docker 29+ (requires API version 1.44).
 */
@Testcontainers
public class WorkflowStopResumeOtelIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowStopResumeOtelIT.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Duration WAIT_BEFORE_EVENT = Duration.ofSeconds(10);
    private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(10);

    // Process IDs
    private static final String MAIN_WORKFLOW_ID = "yamlgreet";
    private static final String SUBFLOW_ID = "anothergreet";

    // Expected nodes for yamlgreet (English path)
    private static final Set<String> YAMLGREET_ENGLISH_NODES = Set.of(
            "ChooseOnLanguage", "GreetInEnglish", "SetMessage",
            "ExecuteSubflow", "waitForEvent", "GreetPerson");

    // Expected nodes for yamlgreet (Spanish path)
    private static final Set<String> YAMLGREET_SPANISH_NODES = Set.of(
            "ChooseOnLanguage", "GreetInSpanish", "SetMessage",
            "ExecuteSubflow", "waitForEvent", "GreetPerson");

    // Expected nodes for anothergreet subflow
    private static final Set<String> ANOTHERGREET_NODES = Set.of(
            "GreetPersonSubflow", "waitForEventSubflow", "GreetPersonAfterWaitSubflow");

    // Minimum span counts
    private static final int MIN_YAMLGREET_SPANS = 5;
    private static final int MIN_ANOTHERGREET_SPANS = 3;

    private static Network network;
    private static PersistentPostgreSQLContainer postgres;
    private static OtlpMockCollector otlpCollector;
    private static QuarkusAppContainer quarkusApp;

    @BeforeAll
    static void setupInfrastructure() {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

        network = Network.newNetwork();

        postgres = new PersistentPostgreSQLContainer(network);
        postgres.start();
        LOGGER.info("PostgreSQL started at: {}", postgres.getJdbcUrl());

        otlpCollector = new OtlpMockCollector();
        otlpCollector.start();
        LOGGER.info("OTLP Collector started at: {}", otlpCollector.getBaseUrl());

        startQuarkusApp();
    }

    @AfterAll
    static void tearDown() {
        stopQuarkusApp();
        if (postgres != null) {
            postgres.stop();
        }
        if (otlpCollector != null) {
            otlpCollector.stop();
        }
        if (network != null) {
            network.close();
        }
    }

    private static void startQuarkusApp() {
        // Expose the OTLP collector port to containers (required on Linux)
        org.testcontainers.Testcontainers.exposeHostPorts(otlpCollector.getPort());

        String otlpEndpointForContainer = "http://host.testcontainers.internal:" + otlpCollector.getPort();

        quarkusApp = new QuarkusAppContainer(
                network,
                postgres.getNetworkJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword(),
                otlpEndpointForContainer);
        quarkusApp.start();

        RestAssured.baseURI = quarkusApp.getAppUrl();
        LOGGER.info("Quarkus App started at: {}", quarkusApp.getAppUrl());
    }

    private static void stopQuarkusApp() {
        if (quarkusApp != null && quarkusApp.isRunning()) {
            LOGGER.info("Stopping Quarkus App...");
            quarkusApp.stop();
            quarkusApp = null;
        }
    }

    private static void restartQuarkusApp() throws InterruptedException {
        LOGGER.info("Restarting Quarkus App...");
        stopQuarkusApp();
        startQuarkusApp();
        // Give the workflow engine time to load persisted instances
        Thread.sleep(Duration.ofSeconds(3).toMillis());
        LOGGER.info("Quarkus App restarted successfully");
    }

    @Test
    void testWorkflowStopResumeWithSpanValidation() throws InterruptedException {
        otlpCollector.clearRequests();

        // === SEQUENCE 1: Complete workflow normally ===
        LOGGER.info("=== SEQUENCE 1: Complete workflow normally ===");
        String processInstanceId1 = startWorkflow("John", "English");
        LOGGER.info("Started workflow 1 with ID: {}", processInstanceId1);

        Thread.sleep(WAIT_BEFORE_EVENT.toMillis());
        String subflowId1 = getSubflowInstanceId();
        LOGGER.info("Found subflow 1 with ID: {}", subflowId1);
        sendLoudEvent(subflowId1);
        sendQuietEvent(processInstanceId1);

        awaitWorkflowCompletion(processInstanceId1);
        validateSpansComprehensively("SEQUENCE 1: Normal completion", "English", false);
        otlpCollector.clearRequests();

        // === SEQUENCE 2: Start workflow, restart app, resume ===
        LOGGER.info("=== SEQUENCE 2: Start workflow, restart app, resume ===");
        String processInstanceId2 = startWorkflow("Maria", "Spanish");
        LOGGER.info("Started workflow 2 with ID: {}", processInstanceId2);

        Thread.sleep(WAIT_BEFORE_EVENT.toMillis());
        // Capture subflow ID before restart (needed because persisted instances may not be immediately listed)
        String subflowId2 = getSubflowInstanceId();
        LOGGER.info("Found subflow 2 with ID: {} (before restart)", subflowId2);

        restartQuarkusApp();

        // Use the subflow ID we captured before restart
        LOGGER.info("Sending loud event to subflow 2 after restart");
        sendLoudEvent(subflowId2);
        sendQuietEvent(processInstanceId2);

        awaitWorkflowCompletion(processInstanceId2);
        validateSpansComprehensively("SEQUENCE 2: Restart before loud event", "Spanish", true);
        otlpCollector.clearRequests();

        // === SEQUENCE 3: Start new workflow, complete normally (fast timeout check) ===
        LOGGER.info("=== SEQUENCE 3: Start new workflow, complete normally ===");
        String processInstanceId3 = startWorkflow("Carlos", "English");
        LOGGER.info("Started workflow 3 with ID: {}", processInstanceId3);

        Thread.sleep(WAIT_BEFORE_EVENT.toMillis());
        String subflowId3 = getSubflowInstanceIdWithShortTimeout();
        LOGGER.info("Found subflow 3 with ID: {}", subflowId3);
        sendLoudEventWithShortTimeout(subflowId3);
        sendQuietEvent(processInstanceId3);

        awaitWorkflowCompletion(processInstanceId3);
        validateSpansComprehensively("SEQUENCE 3: Normal completion (short timeout)", "English", false);
        otlpCollector.clearRequests();

        // === SEQUENCE 4: Start workflow, send loud, restart app, send quiet ===
        LOGGER.info("=== SEQUENCE 4: Start workflow, send loud, restart, complete ===");
        String processInstanceId4 = startWorkflow("Elena", "Spanish");
        LOGGER.info("Started workflow 4 with ID: {}", processInstanceId4);

        Thread.sleep(WAIT_BEFORE_EVENT.toMillis());
        String subflowId4 = getSubflowInstanceId();
        LOGGER.info("Found subflow 4 with ID: {}", subflowId4);
        sendLoudEvent(subflowId4);

        restartQuarkusApp();

        sendQuietEvent(processInstanceId4);

        awaitWorkflowCompletion(processInstanceId4);
        validateSpansComprehensively("SEQUENCE 4: Restart after loud event", "Spanish", true);

        LOGGER.info("=== All test sequences completed successfully ===");
    }

    private String startWorkflow(String name, String language) {
        String body = """
                {
                    "name": "%s",
                    "language": "%s"
                }
                """.formatted(name, language);

        return given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/yamlgreet")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private String getSubflowInstanceId() {
        return await().atMost(TIMEOUT)
                .until(() -> {
                    try {
                        List<Map<String, Object>> instances = given()
                                .contentType(ContentType.JSON)
                                .get("/anothergreet")
                                .then()
                                .statusCode(200)
                                .extract()
                                .jsonPath()
                                .getList("$");
                        if (instances != null && !instances.isEmpty()) {
                            return (String) instances.get(0).get("id");
                        }
                        return null;
                    } catch (Exception e) {
                        LOGGER.debug("Waiting for subflow instance...", e);
                        return null;
                    }
                }, Objects::nonNull);
    }

    private String getSubflowInstanceIdWithShortTimeout() {
        return await().atMost(SHORT_TIMEOUT)
                .until(() -> {
                    try {
                        List<Map<String, Object>> instances = given()
                                .contentType(ContentType.JSON)
                                .get("/anothergreet")
                                .then()
                                .statusCode(200)
                                .extract()
                                .jsonPath()
                                .getList("$");
                        if (instances != null && !instances.isEmpty()) {
                            return (String) instances.get(0).get("id");
                        }
                        return null;
                    } catch (Exception e) {
                        LOGGER.debug("Waiting for subflow instance...", e);
                        return null;
                    }
                }, Objects::nonNull);
    }

    private void sendLoudEvent(String subflowInstanceId) {
        LOGGER.info("Sending loud event to subflow: {}", subflowInstanceId);
        given()
                .header("ce-specversion", "1.0")
                .header("ce-id", UUID.randomUUID().toString())
                .header("ce-source", "org.persistence")
                .header("ce-type", "loud")
                .header("ce-kogitoprocrefid", subflowInstanceId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(ContentType.JSON)
                .body("{}")
                .post("/loud")
                .then()
                .statusCode(202);
    }

    private void sendLoudEventWithShortTimeout(String subflowInstanceId) {
        LOGGER.info("Sending loud event to subflow with short timeout: {}", subflowInstanceId);
        given()
                .header("ce-specversion", "1.0")
                .header("ce-id", UUID.randomUUID().toString())
                .header("ce-source", "org.persistence")
                .header("ce-type", "loud")
                .header("ce-kogitoprocrefid", subflowInstanceId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(ContentType.JSON)
                .body("{}")
                .when()
                .post("/loud")
                .then()
                .statusCode(202);
    }

    private void sendQuietEvent(String processInstanceId) {
        LOGGER.info("Sending quiet event to main workflow: {}", processInstanceId);
        given()
                .header("ce-specversion", "1.0")
                .header("ce-id", UUID.randomUUID().toString())
                .header("ce-source", "org.persistence")
                .header("ce-type", "quiet")
                .header("ce-kogitoprocrefid", processInstanceId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(ContentType.JSON)
                .body("{}")
                .post("/quiet")
                .then()
                .statusCode(202);
    }

    private void awaitWorkflowCompletion(String processInstanceId) {
        await().atMost(TIMEOUT).untilAsserted(() -> {
            given()
                    .contentType(ContentType.JSON)
                    .get("/yamlgreet/{id}", processInstanceId)
                    .then()
                    .statusCode(404);
        });
        LOGGER.info("Workflow {} completed", processInstanceId);
    }

    private Map<String, List<SpanData>> groupSpansByTraceId(List<SpanData> spans) {
        return spans.stream()
                .collect(Collectors.groupingBy(SpanData::getTraceId));
    }

    private void validateTraceCount(Map<String, List<SpanData>> traceGroups, boolean isRestartScenario, String context) {
        LOGGER.info("{}: Found {} trace(s), isRestartScenario={}", context, traceGroups.size(), isRestartScenario);

        // In restart scenarios, trace context may be persisted and reused, resulting in 1 trace
        // Or it may create a new trace after restart, resulting in 2 traces
        // Both are valid behaviors depending on trace context persistence
        if (isRestartScenario) {
            assertThat(traceGroups.size())
                    .withFailMessage(context + ": Restart scenario should have 1 or 2 traces, found " + traceGroups.size())
                    .isBetween(1, 2);
        } else {
            assertThat(traceGroups.size())
                    .withFailMessage(context + ": Normal scenario should have 1 trace, found " + traceGroups.size())
                    .isEqualTo(1);
        }
    }

    private void validateFlatHierarchyPerTrace(List<SpanData> workflowSpans, String context) {
        if (workflowSpans.isEmpty()) {
            return;
        }

        Set<String> workflowSpanIds = workflowSpans.stream()
                .map(SpanData::getSpanId)
                .collect(Collectors.toSet());

        Set<String> parentSpanIds = workflowSpans.stream()
                .map(SpanData::getParentSpanId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        LOGGER.info("{}: {} workflow spans with {} unique parent(s)",
                context, workflowSpans.size(), parentSpanIds.size());

        // All workflow spans should share exactly 1 parent
        assertThat(parentSpanIds)
                .withFailMessage(context + ": All workflow spans should share the same parent. Found parents: " + parentSpanIds)
                .hasSize(1);

        String parentSpanId = parentSpanIds.iterator().next();

        // The parent should NOT be a workflow span (should be HTTP request span)
        assertThat(workflowSpanIds)
                .withFailMessage(context + ": Parent span " + parentSpanId + " should not be a workflow span")
                .doesNotContain(parentSpanId);

        // No workflow span should be parent of another workflow span
        workflowSpans.forEach(span -> {
            assertThat(workflowSpanIds)
                    .withFailMessage(context + ": Workflow span " + span.getSpanId() + " should not be parent of another workflow span")
                    .doesNotContain(span.getParentSpanId());
        });
    }

    private void validateSpanCounts(List<SpanData> mainSpans, List<SpanData> subflowSpans, String context) {
        LOGGER.info("{}: Main workflow spans: {}, Subflow spans: {}",
                context, mainSpans.size(), subflowSpans.size());

        assertThat(mainSpans.size())
                .withFailMessage(context + ": Expected at least " + MIN_YAMLGREET_SPANS +
                        " spans for yamlgreet, found " + mainSpans.size())
                .isGreaterThanOrEqualTo(MIN_YAMLGREET_SPANS);

        assertThat(subflowSpans.size())
                .withFailMessage(context + ": Expected at least " + MIN_ANOTHERGREET_SPANS +
                        " spans for anothergreet, found " + subflowSpans.size())
                .isGreaterThanOrEqualTo(MIN_ANOTHERGREET_SPANS);
    }

    private void validateAllSpanAttributes(List<SpanData> spans, String expectedProcessId, String context) {
        spans.forEach(span -> {
            String nodeName = span.getAttributes().get(SONATAFLOW_PROCESS_INSTANCE_NODE);

            assertThat(span.getAttributes().get(SONATAFLOW_PROCESS_INSTANCE_ID))
                    .withFailMessage(context + ": Missing process instance ID for node " + nodeName)
                    .isNotNull();

            assertThat(span.getAttributes().get(SONATAFLOW_PROCESS_ID))
                    .withFailMessage(context + ": Wrong process ID for node " + nodeName)
                    .isEqualTo(expectedProcessId);

            assertThat(span.getAttributes().get(SONATAFLOW_PROCESS_VERSION))
                    .withFailMessage(context + ": Missing process version for node " + nodeName)
                    .isEqualTo("1.0");

            assertThat(span.getAttributes().get(SONATAFLOW_PROCESS_INSTANCE_STATE))
                    .withFailMessage(context + ": Missing process state for node " + nodeName)
                    .isNotNull();

            assertThat(nodeName)
                    .withFailMessage(context + ": Missing node name")
                    .isNotNull();

            assertThat(span.getName())
                    .withFailMessage(context + ": Span name should follow convention")
                    .startsWith("sonataflow.process." + expectedProcessId);
        });
    }

    private void validateExpectedNodes(List<SpanData> spans, String language, String context) {
        Set<String> actualNodes = spans.stream()
                .map(span -> span.getAttributes().get(SONATAFLOW_PROCESS_INSTANCE_NODE))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        LOGGER.info("{}: Actual yamlgreet nodes: {}", context, actualNodes);

        // Validate key nodes that should always be present (including internal nodes)
        Set<String> expectedCoreNodes = Set.of("ChooseOnLanguage", "SetMessage");
        assertThat(actualNodes)
                .withFailMessage(context + ": Should contain core yamlgreet nodes. Found: " + actualNodes)
                .containsAll(expectedCoreNodes);

        // Validate language-specific greeting node
        if ("English".equals(language)) {
            assertThat(actualNodes)
                    .withFailMessage(context + ": English path should contain GreetInEnglish. Found: " + actualNodes)
                    .contains("GreetInEnglish");
        } else {
            assertThat(actualNodes)
                    .withFailMessage(context + ": Spanish path should contain GreetInSpanish. Found: " + actualNodes)
                    .contains("GreetInSpanish");
        }

        // Validate minimum number of nodes (workflow produces internal nodes too)
        assertThat(actualNodes.size())
                .withFailMessage(context + ": Should have at least 5 yamlgreet nodes. Found: " + actualNodes.size())
                .isGreaterThanOrEqualTo(5);
    }

    private void validateSubflowNodes(List<SpanData> spans, String context) {
        Set<String> actualNodes = spans.stream()
                .map(span -> span.getAttributes().get(SONATAFLOW_PROCESS_INSTANCE_NODE))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        LOGGER.info("{}: Actual anothergreet nodes: {}", context, actualNodes);

        // Validate minimum number of subflow nodes
        assertThat(actualNodes.size())
                .withFailMessage(context + ": Should have at least 3 anothergreet nodes. Found: " + actualNodes.size())
                .isGreaterThanOrEqualTo(3);

        // Validate at least some expected nodes are present (internal nodes may vary)
        boolean hasExpectedNodes = actualNodes.stream()
                .anyMatch(node -> ANOTHERGREET_NODES.contains(node) ||
                        node.contains("Subflow") || node.contains("Start") || node.contains("Event"));

        assertThat(hasExpectedNodes)
                .withFailMessage(context + ": Should contain at least one expected subflow node. Found: " + actualNodes)
                .isTrue();
    }

    private List<EventData> collectEventsByName(List<SpanData> spans, String eventName) {
        return spans.stream()
                .flatMap(span -> span.getEvents().stream())
                .filter(event -> eventName.equals(event.getName()))
                .collect(Collectors.toList());
    }

    private void validateProcessLifecycleEvents(List<SpanData> spans, String processId, String context) {
        // Note: In restart scenarios, lifecycle events might be split across traces
        // or may not be fully captured depending on when the restart occurred

        List<EventData> startEvents = collectEventsByName(spans, Events.PROCESS_INSTANCE_START);
        List<EventData> completeEvents = collectEventsByName(spans, Events.PROCESS_INSTANCE_COMPLETE);

        LOGGER.info("{}: Process {} has {} start events and {} complete events",
                context, processId, startEvents.size(), completeEvents.size());

        // Validate at least one lifecycle event is present (start or complete)
        boolean hasLifecycleEvents = !startEvents.isEmpty() || !completeEvents.isEmpty();
        assertThat(hasLifecycleEvents)
                .withFailMessage(context + ": Process " + processId + " should have lifecycle events (start or complete)")
                .isTrue();

        // Validate start events if present
        startEvents.forEach(event -> {
            assertThat(event.getAttributes().get(PROCESS_INSTANCE_ID))
                    .withFailMessage(context + ": Start event missing process instance ID")
                    .isNotNull();
        });

        // Validate complete events if present
        completeEvents.forEach(event -> {
            assertThat(event.getAttributes().get(PROCESS_INSTANCE_ID))
                    .withFailMessage(context + ": Complete event missing process instance ID")
                    .isNotNull();
            assertThat(event.getAttributes().get(OUTCOME))
                    .withFailMessage(context + ": Complete event should have COMPLETED outcome")
                    .isEqualTo("COMPLETED");
        });
    }

    private void validateNodeEvents(List<SpanData> spans, String context) {
        List<EventData> nodeStartedEvents = collectEventsByName(spans, Events.NODE_STARTED);
        List<EventData> nodeCompletedEvents = collectEventsByName(spans, Events.NODE_COMPLETED);

        LOGGER.info("{}: Found {} node.started events, {} node.completed events",
                context, nodeStartedEvents.size(), nodeCompletedEvents.size());

        assertThat(nodeStartedEvents)
                .withFailMessage(context + ": Should have node.started events")
                .isNotEmpty();

        assertThat(nodeCompletedEvents)
                .withFailMessage(context + ": Should have node.completed events")
                .isNotEmpty();
    }

    private void validateLogMessages(List<SpanData> spans, String language, String context, boolean isRestartScenario) {
        List<EventData> logEvents = collectEventsByName(spans, Events.LOG_MESSAGE);

        LOGGER.info("{}: Found {} log.message events", context, logEvents.size());

        Set<String> logMessages = logEvents.stream()
                .map(event -> event.getAttributes().get(LOG_MESSAGE))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        LOGGER.info("{}: Log messages found: {}", context, logMessages);

        // In restart scenarios, some log messages may be in different traces
        // that occur after restart. We validate what's present rather than requiring all.
        if (!isRestartScenario) {
            // For normal scenarios, validate all expected messages
            assertThat(logMessages)
                    .withFailMessage(context + ": Should contain 'Subflow started' message")
                    .anyMatch(msg -> msg.contains("Subflow started"));

            assertThat(logMessages)
                    .withFailMessage(context + ": Should contain 'Received loud event in subflow' message")
                    .anyMatch(msg -> msg.contains("Received loud event in subflow"));

            assertThat(logMessages)
                    .withFailMessage(context + ": Should contain 'Subflow completed after wait' message")
                    .anyMatch(msg -> msg.contains("Subflow completed after wait"));

            assertThat(logMessages)
                    .withFailMessage(context + ": Should contain 'Received quiet event' message")
                    .anyMatch(msg -> msg.contains("Received quiet event"));

            String expectedGreeting = "English".equals(language) ? "Hello from YAML Workflow" : "Saludos desde YAML Workflow";
            assertThat(logMessages)
                    .withFailMessage(context + ": Should contain greeting message '" + expectedGreeting + "'")
                    .anyMatch(msg -> msg.contains(expectedGreeting));
        } else {
            // For restart scenarios, just validate that we have some log messages
            // and at least the early messages (before restart) are present
            assertThat(logMessages)
                    .withFailMessage(context + ": Should have log messages")
                    .isNotEmpty();

            // Validate early messages that happen before potential restart
            assertThat(logMessages)
                    .withFailMessage(context + ": Should contain 'Subflow started' message")
                    .anyMatch(msg -> msg.contains("Subflow started"));
        }
    }

    private void validateSubflowParentRelationship(List<SpanData> subflowSpans, List<SpanData> mainSpans, String context) {
        if (subflowSpans.isEmpty() || mainSpans.isEmpty()) {
            return;
        }

        Set<String> mainProcessInstanceIds = mainSpans.stream()
                .map(span -> span.getAttributes().get(SONATAFLOW_PROCESS_INSTANCE_ID))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        LOGGER.info("{}: Main workflow process instance IDs: {}", context, mainProcessInstanceIds);

        subflowSpans.forEach(span -> {
            String parentProcessInstanceId = span.getAttributes().get(SONATAFLOW_PARENT_PROCESS_INSTANCE_ID);
            if (parentProcessInstanceId != null) {
                assertThat(mainProcessInstanceIds)
                        .withFailMessage(context + ": Subflow parent process instance ID " + parentProcessInstanceId +
                                " should match main workflow. Main IDs: " + mainProcessInstanceIds)
                        .contains(parentProcessInstanceId);
            }
        });
    }

    /**
     * Comprehensive validation for all workflow spans.
     * Validates trace structure, span counts, attributes, hierarchy, events, and log messages.
     *
     * @param testContext descriptive context for error messages
     * @param language the language used in the workflow (English or Spanish)
     * @param isRestartScenario true if this is a restart scenario (expects 2 traces), false otherwise (expects 1 trace)
     */
    private void validateSpansComprehensively(String testContext, String language, boolean isRestartScenario) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            List<SpanData> spans = OtlpDataParser.extractSpansFromTraceRequests(
                    otlpCollector.getReceivedTracesRequests());

            List<SpanData> workflowSpans = filterWorkflowSpans(spans);

            LOGGER.info("{}: Total spans: {}, Workflow spans: {}",
                    testContext, spans.size(), workflowSpans.size());

            assertThat(workflowSpans)
                    .withFailMessage(testContext + ": Expected workflow spans to be present")
                    .isNotEmpty();

            // 1. Group spans by trace ID
            Map<String, List<SpanData>> traceGroups = groupSpansByTraceId(workflowSpans);

            // 2. Validate trace count
            validateTraceCount(traceGroups, isRestartScenario, testContext);

            // 3. Validate each trace independently
            List<SpanData> allMainSpans = new ArrayList<>();
            List<SpanData> allSubflowSpans = new ArrayList<>();

            for (Map.Entry<String, List<SpanData>> traceEntry : traceGroups.entrySet()) {
                String traceId = traceEntry.getKey();
                List<SpanData> traceSpans = traceEntry.getValue();
                String traceContext = testContext + " [trace:" + traceId.substring(0, 8) + "]";

                LOGGER.info("{}: Processing trace with {} spans", traceContext, traceSpans.size());

                // 3.1 Validate flat hierarchy within this trace
                validateFlatHierarchyPerTrace(traceSpans, traceContext);

                // 3.2 Separate spans by process ID
                Map<String, List<SpanData>> spansByProcessId = traceSpans.stream()
                        .collect(Collectors.groupingBy(span -> span.getAttributes().get(SONATAFLOW_PROCESS_ID)));

                List<SpanData> mainSpans = spansByProcessId.getOrDefault(MAIN_WORKFLOW_ID, List.of());
                List<SpanData> subflowSpans = spansByProcessId.getOrDefault(SUBFLOW_ID, List.of());

                allMainSpans.addAll(mainSpans);
                allSubflowSpans.addAll(subflowSpans);

                // 3.3 Validate span attributes for this trace
                validateAllSpanAttributes(mainSpans, MAIN_WORKFLOW_ID, traceContext);
                validateAllSpanAttributes(subflowSpans, SUBFLOW_ID, traceContext);
            }

            // 4. Validate total span counts across all traces
            validateSpanCounts(allMainSpans, allSubflowSpans, testContext);

            // 5. Validate expected nodes across all traces
            validateExpectedNodes(allMainSpans, language, testContext);
            validateSubflowNodes(allSubflowSpans, testContext);

            // 6. Validate process lifecycle events
            validateProcessLifecycleEvents(allMainSpans, MAIN_WORKFLOW_ID, testContext);
            validateProcessLifecycleEvents(allSubflowSpans, SUBFLOW_ID, testContext);

            // 7. Validate node events
            validateNodeEvents(workflowSpans, testContext);

            // 8. Validate log messages (from greetFunction/greetSubflowFunction calls)
            validateLogMessages(workflowSpans, language, testContext, isRestartScenario);

            // 9. Validate subflow parent relationship
            validateSubflowParentRelationship(allSubflowSpans, allMainSpans, testContext);
        });
    }

    private List<SpanData> filterWorkflowSpans(List<SpanData> spans) {
        return spans.stream()
                .filter(span -> span.getName().startsWith("sonataflow.process"))
                .toList();
    }
}
