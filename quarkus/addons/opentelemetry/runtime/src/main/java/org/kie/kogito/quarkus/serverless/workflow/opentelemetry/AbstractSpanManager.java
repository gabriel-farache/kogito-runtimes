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

import org.kie.kogito.quarkus.serverless.workflow.opentelemetry.config.SonataFlowOtelConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;

import static org.kie.kogito.quarkus.serverless.workflow.opentelemetry.SonataFlowOtelAttributes.EVENT_DESCRIPTION;

/**
 * Abstract base class for OpenTelemetry span managers in SonataFlow.
 * Provides common implementations for span operations shared between
 * node-level and process-level span management strategies.
 */
public abstract class AbstractSpanManager implements SpanManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractSpanManager.class);

    protected final Tracer tracer;
    protected final SonataFlowOtelConfig config;

    protected AbstractSpanManager() {
        this.tracer = null;
        this.config = null;
    }

    protected AbstractSpanManager(Tracer tracer, SonataFlowOtelConfig config) {
        this.tracer = tracer;
        this.config = config;
    }

    protected boolean isSpanCreationEnabled() {
        return config.spans().enabled();
    }

    @Override
    public void addProcessEvent(Span span, String eventName, String description) {
        if (span != null) {
            if (description != null) {
                Attributes eventAttributes = Attributes.of(
                        EVENT_DESCRIPTION, description);
                span.addEvent(eventName, eventAttributes);
            } else {
                span.addEvent(eventName);
            }
            LOGGER.debug("Added event {} to span", eventName);
        } else {
            LOGGER.debug("Cannot add event {} - span is null", eventName);
        }
    }

    @Override
    public void addProcessEvent(Span span, String eventName, Attributes attributes) {
        if (span != null) {
            span.addEvent(eventName, attributes);
            LOGGER.debug("Added process event {} to span", eventName);
        } else {
            LOGGER.debug("Cannot add event {} - span is null", eventName);
        }
    }

    @Override
    public void setSpanError(Span span, Throwable exception, String description) {
        if (span != null) {
            span.setStatus(StatusCode.ERROR, description);
            if (exception != null) {
                span.recordException(exception);
            }
        }
    }
}
