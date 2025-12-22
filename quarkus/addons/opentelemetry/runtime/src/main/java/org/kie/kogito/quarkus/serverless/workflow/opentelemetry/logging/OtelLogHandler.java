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

import java.util.logging.Level;

import org.jboss.logmanager.ExtHandler;
import org.jboss.logmanager.ExtLogRecord;
import org.kie.kogito.quarkus.serverless.workflow.opentelemetry.OtelContextHolder;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;

import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.*;

public class OtelLogHandler extends ExtHandler {

    private Level minimumLevel = Level.INFO;

    @Override
    protected void doPublish(ExtLogRecord record) {
        if (!shouldCapture(record)) {
            return;
        }

        Span targetSpan = OtelContextHolder.getCurrentWorkflowSpan();

        if (targetSpan == null || !targetSpan.getSpanContext().isValid()) {
            targetSpan = Span.current();
        }
        if (targetSpan == null || !targetSpan.getSpanContext().isValid()) {
            return;
        }

        String formattedMessage = record.getFormattedMessage();
        if (formattedMessage == null) {
            formattedMessage = record.getMessage();
        }

        targetSpan.addEvent(Events.LOG_MESSAGE, Attributes.of(
                LOG_LEVEL, record.getLevel().getName(),
                LOG_LOGGER, record.getLoggerName(),
                LOG_MESSAGE, formattedMessage,
                LOG_THREAD_NAME, Thread.currentThread().getName(),
                LOG_THREAD_ID, Thread.currentThread().getId()));
    }

    private boolean shouldCapture(ExtLogRecord record) {
        return record.getLevel().intValue() >= minimumLevel.intValue();
    }

    public void setMinimumLevel(String levelName) {
        this.minimumLevel = Level.parse(levelName);
    }
}
