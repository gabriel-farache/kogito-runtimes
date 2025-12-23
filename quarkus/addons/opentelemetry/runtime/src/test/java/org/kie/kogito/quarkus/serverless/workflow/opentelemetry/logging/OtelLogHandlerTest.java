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
package org.kie.kogito.quarkus.serverless.workflow.opentelemetry.logging;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OtelLogHandlerTest {

    private OtelLogHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OtelLogHandler();
        OtelLogCollector.clear();
    }

    @AfterEach
    void tearDown() {
        OtelLogCollector.clear();
    }

    @Test
    void shouldNotThrowWhenPublishingWithoutSpanInNormalMode() {
        LogRecord record = new LogRecord(Level.INFO, "Test message");
        record.setLoggerName("test.logger");

        handler.publish(record);

        List<OtelLogCollector.LogEventData> logs = OtelLogCollector.getAndClear();
        assertThat(logs).isEmpty();
    }

    @Test
    void shouldCollectLogsInShortMode() {
        handler.setShortMode(true);

        LogRecord record = new LogRecord(Level.INFO, "Test message");
        record.setLoggerName("test.logger");

        handler.publish(record);

        List<OtelLogCollector.LogEventData> logs = OtelLogCollector.getAndClear();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).level()).isEqualTo("INFO");
        assertThat(logs.get(0).logger()).isEqualTo("test.logger");
        assertThat(logs.get(0).message()).isEqualTo("Test message");
        assertThat(logs.get(0).threadName()).isNotNull();
        assertThat(logs.get(0).threadId()).isGreaterThan(0);
    }

    @Test
    void shouldFormatMessageWithParametersInShortMode() {
        handler.setShortMode(true);

        LogRecord record = new LogRecord(Level.WARNING, "User %s failed login attempt %d");
        record.setParameters(new Object[] { "alice", 3 });
        record.setLoggerName("security.logger");

        handler.publish(record);

        List<OtelLogCollector.LogEventData> logs = OtelLogCollector.getAndClear();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).message()).isEqualTo("User alice failed login attempt 3");
        assertThat(logs.get(0).level()).isEqualTo("WARNING");
        assertThat(logs.get(0).logger()).isEqualTo("security.logger");
    }

    @Test
    void shouldNotCollectLogsInNormalMode() {
        handler.setShortMode(false);

        LogRecord record = new LogRecord(Level.INFO, "Test message");
        record.setLoggerName("test.logger");

        handler.publish(record);

        List<OtelLogCollector.LogEventData> logs = OtelLogCollector.getAndClear();
        assertThat(logs).isEmpty();
    }

    @Test
    void shouldRespectMinimumLevelInfo() {
        handler.setMinimumLevel("INFO");
        handler.setShortMode(true);

        LogRecord debugRecord = new LogRecord(Level.FINE, "Debug message");
        LogRecord infoRecord = new LogRecord(Level.INFO, "Info message");
        LogRecord warningRecord = new LogRecord(Level.WARNING, "Warning message");

        handler.publish(debugRecord);
        handler.publish(infoRecord);
        handler.publish(warningRecord);

        List<OtelLogCollector.LogEventData> logs = OtelLogCollector.getAndClear();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).level()).isEqualTo("INFO");
        assertThat(logs.get(1).level()).isEqualTo("WARNING");
    }

    @Test
    void shouldRespectMinimumLevelWarning() {
        handler.setMinimumLevel("WARNING");
        handler.setShortMode(true);

        LogRecord infoRecord = new LogRecord(Level.INFO, "Info message");
        LogRecord warningRecord = new LogRecord(Level.WARNING, "Warning message");
        LogRecord severeRecord = new LogRecord(Level.SEVERE, "Severe message");

        handler.publish(infoRecord);
        handler.publish(warningRecord);
        handler.publish(severeRecord);

        List<OtelLogCollector.LogEventData> logs = OtelLogCollector.getAndClear();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).level()).isEqualTo("WARNING");
        assertThat(logs.get(1).level()).isEqualTo("SEVERE");
    }

    @Test
    void shouldRespectMinimumLevelSevere() {
        handler.setMinimumLevel("SEVERE");
        handler.setShortMode(true);

        LogRecord warningRecord = new LogRecord(Level.WARNING, "Warning message");
        LogRecord severeRecord = new LogRecord(Level.SEVERE, "Severe message");

        handler.publish(warningRecord);
        handler.publish(severeRecord);

        List<OtelLogCollector.LogEventData> logs = OtelLogCollector.getAndClear();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).level()).isEqualTo("SEVERE");
    }

    @Test
    void shouldFilterBelowMinimumLevelInNormalMode() {
        handler.setMinimumLevel("WARNING");

        LogRecord infoRecord = new LogRecord(Level.INFO, "Info message");
        LogRecord warningRecord = new LogRecord(Level.WARNING, "Warning message");

        handler.publish(infoRecord);
        handler.publish(warningRecord);
    }

    @Test
    void shouldHandleNullParametersArray() {
        handler.setShortMode(true);

        LogRecord record = new LogRecord(Level.INFO, "Simple message");
        record.setParameters(null);

        handler.publish(record);

        List<OtelLogCollector.LogEventData> logs = OtelLogCollector.getAndClear();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).message()).isEqualTo("Simple message");
    }

    @Test
    void shouldHandleEmptyParametersArray() {
        handler.setShortMode(true);

        LogRecord record = new LogRecord(Level.INFO, "Simple message");
        record.setParameters(new Object[] {});

        handler.publish(record);

        List<OtelLogCollector.LogEventData> logs = OtelLogCollector.getAndClear();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).message()).isEqualTo("Simple message");
    }

    @Test
    void shouldHandleFlushWithoutError() {
        handler.flush();
    }

    @Test
    void shouldHandleCloseWithoutError() throws Exception {
        handler.close();
    }

    @Test
    void shouldToggleBetweenNormalAndShortMode() {
        LogRecord record1 = new LogRecord(Level.INFO, "Message 1");
        LogRecord record2 = new LogRecord(Level.INFO, "Message 2");
        LogRecord record3 = new LogRecord(Level.INFO, "Message 3");

        handler.setShortMode(false);
        handler.publish(record1);

        handler.setShortMode(true);
        handler.publish(record2);

        handler.setShortMode(false);
        handler.publish(record3);

        List<OtelLogCollector.LogEventData> logs = OtelLogCollector.getAndClear();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).message()).isEqualTo("Message 2");
    }

    @Test
    void shouldFormatMultipleParametersInShortMode() {
        handler.setShortMode(true);

        LogRecord record = new LogRecord(Level.INFO, "Process %s completed in %d ms with status %s");
        record.setParameters(new Object[] { "workflow-123", 500, "SUCCESS" });
        record.setLoggerName("workflow.logger");

        handler.publish(record);

        List<OtelLogCollector.LogEventData> logs = OtelLogCollector.getAndClear();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).message()).isEqualTo("Process workflow-123 completed in 500 ms with status SUCCESS");
    }

    @Test
    void shouldNotCollectWhenBelowMinimumLevel() {
        handler.setMinimumLevel("SEVERE");
        handler.setShortMode(true);

        LogRecord infoRecord = new LogRecord(Level.INFO, "Info message");
        LogRecord warningRecord = new LogRecord(Level.WARNING, "Warning message");

        handler.publish(infoRecord);
        handler.publish(warningRecord);

        List<OtelLogCollector.LogEventData> logs = OtelLogCollector.getAndClear();
        assertThat(logs).isEmpty();
    }

    @Test
    void shouldUpdateMinimumLevelDynamically() {
        handler.setShortMode(true);

        handler.setMinimumLevel("INFO");
        LogRecord infoRecord = new LogRecord(Level.INFO, "Info message");
        handler.publish(infoRecord);

        handler.setMinimumLevel("SEVERE");
        LogRecord warningRecord = new LogRecord(Level.WARNING, "Warning message");
        handler.publish(warningRecord);

        List<OtelLogCollector.LogEventData> logs = OtelLogCollector.getAndClear();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).level()).isEqualTo("INFO");
    }
}
