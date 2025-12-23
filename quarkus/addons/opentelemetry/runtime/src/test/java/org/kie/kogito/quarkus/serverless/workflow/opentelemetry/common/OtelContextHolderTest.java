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
package org.kie.kogito.quarkus.serverless.workflow.opentelemetry.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.opentelemetry.context.Context;

import static org.junit.jupiter.api.Assertions.*;

public class OtelContextHolderTest {

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

    @Test
    public void shouldStoreAndRetrieveHttpRequestContext() {
        Context context = Context.current();

        OtelContextHolder.setHttpRequestContext(context);

        Context retrieved = OtelContextHolder.getHttpRequestContext();
        assertNotNull(retrieved, "Should retrieve stored HTTP request context");
        assertEquals(context, retrieved, "Retrieved context should match stored context");
    }

    @Test
    public void shouldReturnNullWhenHttpRequestContextNotSet() {
        Context retrieved = OtelContextHolder.getHttpRequestContext();
        assertNull(retrieved, "Should return null when HTTP request context not set");
    }

    @Test
    public void shouldClearHttpRequestContext() {
        Context context = Context.current();
        OtelContextHolder.setHttpRequestContext(context);

        assertNotNull(OtelContextHolder.getHttpRequestContext(), "Context should be set before clear");

        OtelContextHolder.clearHttpRequestContext();

        assertNull(OtelContextHolder.getHttpRequestContext(), "Context should be null after clear");
    }

    @Test
    public void shouldNotStoreNullHttpRequestContext() {
        OtelContextHolder.setHttpRequestContext(null);

        assertNull(OtelContextHolder.getHttpRequestContext(),
                "Should not store null context");
    }

    @Test
    public void shouldIsolateHttpRequestContextPerThread() throws InterruptedException {
        Context mainContext = Context.current();
        OtelContextHolder.setHttpRequestContext(mainContext);

        final Context[] threadContext = new Context[1];
        final Context[] threadRetrievedMainContext = new Context[1];

        Thread thread = new Thread(() -> {
            threadRetrievedMainContext[0] = OtelContextHolder.getHttpRequestContext();

            Context newContext = Context.current();
            OtelContextHolder.setHttpRequestContext(newContext);
            threadContext[0] = OtelContextHolder.getHttpRequestContext();
        });

        thread.start();
        thread.join();

        assertNull(threadRetrievedMainContext[0],
                "Other thread should not see main thread's HTTP context (ThreadLocal isolation)");
        assertNotNull(threadContext[0], "Thread should be able to set its own context");

        assertEquals(mainContext, OtelContextHolder.getHttpRequestContext(),
                "Main thread's context should not be affected by other thread");
    }

    @Test
    public void shouldReturnStoredHttpRequestContext() {
        Context httpContext = Context.current();
        OtelContextHolder.setHttpRequestContext(httpContext);

        Context retrievedHttpContext = OtelContextHolder.getHttpRequestContext();
        assertNotNull(retrievedHttpContext, "HTTP context should be available");
        assertEquals(httpContext, retrievedHttpContext,
                "Should return the stored HTTP context, not a polluted Context.current()");
    }

    @Test
    public void shouldStoreAndRetrieveRootContext() {
        String processInstanceId = "test-process-1";
        Context context = Context.current();

        OtelContextHolder.setRootContext(processInstanceId, context);

        Context retrieved = OtelContextHolder.getRootContext(processInstanceId);
        assertNotNull(retrieved, "Should retrieve stored root context");
        assertEquals(context, retrieved, "Retrieved context should match stored context");
    }

    @Test
    public void shouldReturnNullWhenRootContextNotSet() {
        Context retrieved = OtelContextHolder.getRootContext("non-existent-process");
        assertNull(retrieved, "Should return null when root context not set for process");
    }

    @Test
    public void shouldClearRootContext() {
        String processInstanceId = "test-process-1";
        Context context = Context.current();
        OtelContextHolder.setRootContext(processInstanceId, context);

        assertNotNull(OtelContextHolder.getRootContext(processInstanceId), "Context should be set before clear");

        OtelContextHolder.clearRootContext(processInstanceId);

        assertNull(OtelContextHolder.getRootContext(processInstanceId), "Context should be null after clear");
    }

    @Test
    public void shouldMaintainSeparateRootContextsPerProcessInstance() {
        String process1 = "test-process-1";
        String process2 = "test-process-2";
        Context context1 = Context.current();
        Context context2 = Context.current();

        OtelContextHolder.setRootContext(process1, context1);
        OtelContextHolder.setRootContext(process2, context2);

        assertEquals(context1, OtelContextHolder.getRootContext(process1),
                "Process 1 should have its own context");
        assertEquals(context2, OtelContextHolder.getRootContext(process2),
                "Process 2 should have its own context");

        OtelContextHolder.clearRootContext(process1);
        assertNull(OtelContextHolder.getRootContext(process1),
                "Process 1 context should be cleared");
        assertNotNull(OtelContextHolder.getRootContext(process2),
                "Process 2 context should not be affected");
    }

    @Test
    public void shouldReturnEmptyMapFromGetTrackerAttributesWhenNoneSet() {
        OtelContextHolder.clear();

        java.util.Map<String, String> attributes = OtelContextHolder.getTrackerAttributes();

        assertNotNull(attributes, "Should return a map, not null");
        assertTrue(attributes.isEmpty(), "Map should be empty when no trackers are set");
    }
}
