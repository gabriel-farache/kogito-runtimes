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

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;

/**
 * Common interface for managing OpenTelemetry spans in SonataFlow.
 * Provides core operations for adding events and handling errors across both
 * long mode (node-level) and short mode (process-level) span management.
 */
public interface SpanManager {

    /**
     * Adds an event to the span with a description.
     *
     * @param span the span to add the event to
     * @param eventName the name of the event
     * @param description the event description (may be null)
     */
    void addProcessEvent(Span span, String eventName, String description);

    /**
     * Adds an event to the span with custom attributes.
     *
     * @param span the span to add the event to
     * @param eventName the name of the event
     * @param attributes the attributes for the event
     */
    void addProcessEvent(Span span, String eventName, Attributes attributes);

    /**
     * Sets an error on the span and optionally records an exception.
     *
     * @param span the span to mark as error
     * @param exception the exception to record (may be null)
     * @param description the error description
     */
    void setSpanError(Span span, Throwable exception, String description);

    /**
     * Performs cleanup of all active spans during shutdown.
     * Implementations should end all active spans and clear internal state.
     */
    void cleanup();
}
