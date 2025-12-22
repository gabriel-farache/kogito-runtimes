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

import java.lang.reflect.Field;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class OtelHeaderFilterTest {

    private static final String VALID_TRACE_ID = "0123456789abcdef0123456789abcdef";
    private static final String VALID_SPAN_ID = "0123456789abcdef";

    private OtelHeaderFilter filter;
    private HeaderContextExtractor mockHeaderExtractor;
    private ContainerRequestContext mockRequestContext;
    private ContainerResponseContext mockResponseContext;

    @BeforeEach
    public void setup() throws Exception {
        filter = new OtelHeaderFilter();
        mockHeaderExtractor = mock(HeaderContextExtractor.class);
        mockRequestContext = mock(ContainerRequestContext.class);
        mockResponseContext = mock(ContainerResponseContext.class);

        setField(filter, "headerExtractor", mockHeaderExtractor);
        setField(filter, "otelEnabled", true);

        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        when(mockRequestContext.getHeaders()).thenReturn(headers);
        when(mockHeaderExtractor.extractHeaders(headers)).thenReturn(Map.of());

        OtelContextHolder.clearHttpRequestContext();
        OtelContextHolder.clear();
    }

    @AfterEach
    public void cleanup() {
        OtelContextHolder.clearHttpRequestContext();
        OtelContextHolder.clear();
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private SpanContext createValidSpanContext() {
        return SpanContext.create(VALID_TRACE_ID, VALID_SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());
    }

    @Test
    public void shouldCaptureHttpRequestSpanContextOnRequestEntry() throws Exception {
        SpanContext spanContext = createValidSpanContext();
        Span testSpan = Span.wrap(spanContext);

        try (Scope ignored = Context.current().with(testSpan).makeCurrent()) {
            assertNull(OtelContextHolder.getHttpRequestSpanContext(),
                    "HTTP span context should be null before filter");

            filter.filter(mockRequestContext);

            SpanContext captured = OtelContextHolder.getHttpRequestSpanContext();
            assertNotNull(captured, "HTTP span context should be captured after request filter");
            assertEquals(VALID_TRACE_ID, captured.getTraceId());
            assertEquals(VALID_SPAN_ID, captured.getSpanId());
        }
    }

    @Test
    public void shouldClearHttpRequestContextOnResponseExit() throws Exception {
        SpanContext spanContext = createValidSpanContext();
        Span testSpan = Span.wrap(spanContext);
        Context context = Context.current().with(testSpan);
        OtelContextHolder.setHttpRequestContext(context);

        assertNotNull(OtelContextHolder.getHttpRequestSpanContext(),
                "HTTP span context should be set before response filter");

        filter.filter(mockRequestContext, mockResponseContext);

        assertNull(OtelContextHolder.getHttpRequestSpanContext(),
                "HTTP span context should be cleared after response filter");
    }

    @Test
    public void shouldNotCaptureHttpContextWhenOtelDisabled() throws Exception {
        setField(filter, "otelEnabled", false);

        SpanContext spanContext = createValidSpanContext();
        Span testSpan = Span.wrap(spanContext);

        try (Scope ignored = Context.current().with(testSpan).makeCurrent()) {
            filter.filter(mockRequestContext);

            assertNull(OtelContextHolder.getHttpRequestSpanContext(),
                    "HTTP span context should not be captured when OTel is disabled");
        }
    }

    @Test
    public void shouldCaptureHttpContextBeforeHeaderExtraction() throws Exception {
        SpanContext spanContext = createValidSpanContext();
        Span testSpan = Span.wrap(spanContext);

        final SpanContext[] capturedSpanContext = new SpanContext[1];

        when(mockHeaderExtractor.extractHeaders(any())).thenAnswer(invocation -> {
            capturedSpanContext[0] = OtelContextHolder.getHttpRequestSpanContext();
            return Map.of();
        });

        try (Scope ignored = Context.current().with(testSpan).makeCurrent()) {
            filter.filter(mockRequestContext);

            assertNotNull(capturedSpanContext[0],
                    "HTTP span context should be captured BEFORE header extraction to ensure it's the pure HTTP span");
            assertEquals(VALID_TRACE_ID, capturedSpanContext[0].getTraceId());
        }
    }

    @Test
    public void shouldNotCaptureContextWithInvalidSpan() throws Exception {
        assertNull(OtelContextHolder.getHttpRequestSpanContext(),
                "HTTP span context should be null before filter");

        filter.filter(mockRequestContext);

        assertNull(OtelContextHolder.getHttpRequestSpanContext(),
                "HTTP span context should remain null when current context has no valid span");
    }

    @Test
    public void shouldMaintainHttpContextThroughRequestLifecycle() throws Exception {
        SpanContext spanContext = createValidSpanContext();
        Span testSpan = Span.wrap(spanContext);

        try (Scope ignored = Context.current().with(testSpan).makeCurrent()) {
            filter.filter(mockRequestContext);

            SpanContext capturedContext = OtelContextHolder.getHttpRequestSpanContext();
            assertNotNull(capturedContext, "Span context should be available after request filter");
            assertEquals(VALID_TRACE_ID, capturedContext.getTraceId());

            filter.filter(mockRequestContext, mockResponseContext);

            assertNull(OtelContextHolder.getHttpRequestSpanContext(),
                    "Span context should be cleared after response filter");
        }
    }
}
