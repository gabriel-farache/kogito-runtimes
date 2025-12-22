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

import io.opentelemetry.context.Context;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class OtelHeaderFilterTest {

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

    @Test
    public void shouldCaptureHttpRequestContextOnRequestEntry() throws Exception {
        assertNull(OtelContextHolder.getHttpRequestContext(),
                "HTTP context should be null before filter");

        filter.filter(mockRequestContext);

        assertNotNull(OtelContextHolder.getHttpRequestContext(),
                "HTTP context should be captured after request filter");
    }

    @Test
    public void shouldClearHttpRequestContextOnResponseExit() throws Exception {
        Context context = Context.current();
        OtelContextHolder.setHttpRequestContext(context);
        assertNotNull(OtelContextHolder.getHttpRequestContext(),
                "HTTP context should be set before response filter");

        filter.filter(mockRequestContext, mockResponseContext);

        assertNull(OtelContextHolder.getHttpRequestContext(),
                "HTTP context should be cleared after response filter");
    }

    @Test
    public void shouldNotCaptureHttpContextWhenOtelDisabled() throws Exception {
        setField(filter, "otelEnabled", false);

        filter.filter(mockRequestContext);

        assertNull(OtelContextHolder.getHttpRequestContext(),
                "HTTP context should not be captured when OTel is disabled");
    }

    @Test
    public void shouldCaptureHttpContextBeforeHeaderExtraction() throws Exception {
        final Context[] capturedContext = new Context[1];

        when(mockHeaderExtractor.extractHeaders(any())).thenAnswer(invocation -> {
            capturedContext[0] = OtelContextHolder.getHttpRequestContext();
            return Map.of();
        });

        filter.filter(mockRequestContext);

        assertNotNull(capturedContext[0],
                "HTTP context should be captured BEFORE header extraction to ensure it's the pure HTTP span");
    }

    @Test
    public void shouldMaintainHttpContextThroughRequestLifecycle() throws Exception {
        filter.filter(mockRequestContext);

        Context capturedContext = OtelContextHolder.getHttpRequestContext();
        assertNotNull(capturedContext, "Context should be available after request filter");

        Context currentContext = Context.current();
        assertEquals(currentContext, capturedContext,
                "Captured context should match the current context at request entry");

        filter.filter(mockRequestContext, mockResponseContext);

        assertNull(OtelContextHolder.getHttpRequestContext(),
                "Context should be cleared after response filter");
    }
}
