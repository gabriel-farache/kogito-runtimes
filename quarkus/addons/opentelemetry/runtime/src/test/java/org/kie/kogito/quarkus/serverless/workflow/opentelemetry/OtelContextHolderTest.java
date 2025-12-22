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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;

import static org.junit.jupiter.api.Assertions.*;

public class OtelContextHolderTest {

    private static final String VALID_TRACE_ID = "0123456789abcdef0123456789abcdef";
    private static final String VALID_SPAN_ID = "0123456789abcdef";

    @BeforeEach
    public void setup() {
        OtelContextHolder.clearHttpRequestContext();
        OtelContextHolder.clear();
    }

    @AfterEach
    public void cleanup() {
        OtelContextHolder.clearHttpRequestContext();
        OtelContextHolder.clear();
        OtelContextHolder.clearProcessContexts("test-process-1");
        OtelContextHolder.clearProcessContexts("test-process-2");
    }

    private SpanContext createValidSpanContext(String traceId, String spanId) {
        return SpanContext.create(traceId, spanId, TraceFlags.getSampled(), TraceState.getDefault());
    }

    private Context createContextWithValidSpan(SpanContext spanContext) {
        Span span = Span.wrap(spanContext);
        return Context.current().with(span);
    }

    @Test
    public void shouldStoreAndRetrieveHttpRequestSpanContext() {
        SpanContext spanContext = createValidSpanContext(VALID_TRACE_ID, VALID_SPAN_ID);
        Context context = createContextWithValidSpan(spanContext);

        OtelContextHolder.setHttpRequestContext(context);

        SpanContext retrieved = OtelContextHolder.getHttpRequestSpanContext();
        assertNotNull(retrieved, "Should retrieve stored HTTP request span context");
        assertEquals(spanContext.getTraceId(), retrieved.getTraceId());
        assertEquals(spanContext.getSpanId(), retrieved.getSpanId());
    }

    @Test
    public void shouldReturnNullWhenHttpRequestContextNotSet() {
        SpanContext retrieved = OtelContextHolder.getHttpRequestSpanContext();
        assertNull(retrieved, "Should return null when HTTP request context not set");
    }

    @Test
    public void shouldClearHttpRequestContext() {
        SpanContext spanContext = createValidSpanContext(VALID_TRACE_ID, VALID_SPAN_ID);
        Context context = createContextWithValidSpan(spanContext);
        OtelContextHolder.setHttpRequestContext(context);

        assertNotNull(OtelContextHolder.getHttpRequestSpanContext(), "Context should be set before clear");

        OtelContextHolder.clearHttpRequestContext();

        assertNull(OtelContextHolder.getHttpRequestSpanContext(), "Context should be null after clear");
    }

    @Test
    public void shouldNotStoreNullHttpRequestContext() {
        OtelContextHolder.setHttpRequestContext(null);

        assertNull(OtelContextHolder.getHttpRequestSpanContext(),
                "Should not store null context");
    }

    @Test
    public void shouldNotStoreContextWithInvalidSpan() {
        Context contextWithoutValidSpan = Context.current();
        OtelContextHolder.setHttpRequestContext(contextWithoutValidSpan);

        assertNull(OtelContextHolder.getHttpRequestSpanContext(),
                "Should not store context with invalid span");
    }

    @Test
    public void shouldIsolateHttpRequestContextPerThread() throws InterruptedException {
        SpanContext mainSpanContext = createValidSpanContext(VALID_TRACE_ID, VALID_SPAN_ID);
        Context mainContext = createContextWithValidSpan(mainSpanContext);
        OtelContextHolder.setHttpRequestContext(mainContext);

        final SpanContext[] threadRetrievedContext = new SpanContext[1];
        final SpanContext[] threadOwnContext = new SpanContext[1];

        Thread thread = new Thread(() -> {
            threadRetrievedContext[0] = OtelContextHolder.getHttpRequestSpanContext();

            SpanContext newSpanContext = createValidSpanContext("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "bbbbbbbbbbbbbbbb");
            Context newContext = createContextWithValidSpan(newSpanContext);
            OtelContextHolder.setHttpRequestContext(newContext);
            threadOwnContext[0] = OtelContextHolder.getHttpRequestSpanContext();
        });

        thread.start();
        thread.join();

        assertNull(threadRetrievedContext[0],
                "Other thread should not see main thread's HTTP context (ThreadLocal isolation)");
        assertNotNull(threadOwnContext[0], "Thread should be able to set its own context");

        SpanContext mainRetrieved = OtelContextHolder.getHttpRequestSpanContext();
        assertEquals(mainSpanContext.getTraceId(), mainRetrieved.getTraceId(),
                "Main thread's context should not be affected by other thread");
    }

    @Test
    public void shouldStoreAndRetrieveRootSpanContext() {
        String processInstanceId = "test-process-1";
        SpanContext spanContext = createValidSpanContext(VALID_TRACE_ID, VALID_SPAN_ID);
        Context context = createContextWithValidSpan(spanContext);

        OtelContextHolder.setRootContext(processInstanceId, context);

        SpanContext retrieved = OtelContextHolder.getRootSpanContext(processInstanceId);
        assertNotNull(retrieved, "Should retrieve stored root span context");
        assertEquals(spanContext.getTraceId(), retrieved.getTraceId());
        assertEquals(spanContext.getSpanId(), retrieved.getSpanId());
    }

    @Test
    public void shouldStoreRootSpanContextDirectly() {
        String processInstanceId = "test-process-1";
        SpanContext spanContext = createValidSpanContext(VALID_TRACE_ID, VALID_SPAN_ID);

        OtelContextHolder.setRootSpanContext(processInstanceId, spanContext);

        SpanContext retrieved = OtelContextHolder.getRootSpanContext(processInstanceId);
        assertNotNull(retrieved, "Should retrieve stored root span context");
        assertEquals(spanContext.getTraceId(), retrieved.getTraceId());
        assertEquals(spanContext.getSpanId(), retrieved.getSpanId());
    }

    @Test
    public void shouldReturnNullWhenRootContextNotSet() {
        SpanContext retrieved = OtelContextHolder.getRootSpanContext("non-existent-process");
        assertNull(retrieved, "Should return null when root context not set for process");
    }

    @Test
    public void shouldClearRootContext() {
        String processInstanceId = "test-process-1";
        SpanContext spanContext = createValidSpanContext(VALID_TRACE_ID, VALID_SPAN_ID);
        OtelContextHolder.setRootSpanContext(processInstanceId, spanContext);

        assertNotNull(OtelContextHolder.getRootSpanContext(processInstanceId), "Context should be set before clear");

        OtelContextHolder.clearRootContext(processInstanceId);

        assertNull(OtelContextHolder.getRootSpanContext(processInstanceId), "Context should be null after clear");
    }

    @Test
    public void shouldMaintainSeparateRootContextsPerProcessInstance() {
        String process1 = "test-process-1";
        String process2 = "test-process-2";
        SpanContext spanContext1 = createValidSpanContext("11111111111111111111111111111111", "1111111111111111");
        SpanContext spanContext2 = createValidSpanContext("22222222222222222222222222222222", "2222222222222222");

        OtelContextHolder.setRootSpanContext(process1, spanContext1);
        OtelContextHolder.setRootSpanContext(process2, spanContext2);

        SpanContext retrieved1 = OtelContextHolder.getRootSpanContext(process1);
        SpanContext retrieved2 = OtelContextHolder.getRootSpanContext(process2);

        assertEquals(spanContext1.getTraceId(), retrieved1.getTraceId(),
                "Process 1 should have its own context");
        assertEquals(spanContext2.getTraceId(), retrieved2.getTraceId(),
                "Process 2 should have its own context");

        OtelContextHolder.clearRootContext(process1);
        assertNull(OtelContextHolder.getRootSpanContext(process1),
                "Process 1 context should be cleared");
        assertNotNull(OtelContextHolder.getRootSpanContext(process2),
                "Process 2 context should not be affected");
    }

    @Test
    public void shouldNotStoreInvalidRootSpanContext() {
        String processInstanceId = "test-process-1";
        SpanContext invalidContext = SpanContext.getInvalid();

        OtelContextHolder.setRootSpanContext(processInstanceId, invalidContext);

        assertNull(OtelContextHolder.getRootSpanContext(processInstanceId),
                "Should not store invalid span context");
    }

    @Test
    public void shouldHandlePopulateFromExtractedContextWithNullMap() {
        assertDoesNotThrow(() -> OtelContextHolder.populateFromExtractedContext(null));
    }

    @Test
    public void shouldHandlePopulateFromExtractedContextWithEmptyValues() {
        OtelContextHolder.clear();

        java.util.Map<String, String> context = new java.util.HashMap<>();
        context.put("transaction.id", "");

        OtelContextHolder.populateFromExtractedContext(context);

        assertNull(OtelContextHolder.getTransactionId(), "Empty transaction ID should not be stored");
    }

    @Test
    public void shouldReturnEmptyMapFromGetTrackerAttributesWhenNoneSet() {
        OtelContextHolder.clear();

        java.util.Map<String, String> attributes = OtelContextHolder.getTrackerAttributes();

        assertNotNull(attributes, "Should return a map, not null");
        assertTrue(attributes.isEmpty(), "Map should be empty when no trackers are set");
    }
}
