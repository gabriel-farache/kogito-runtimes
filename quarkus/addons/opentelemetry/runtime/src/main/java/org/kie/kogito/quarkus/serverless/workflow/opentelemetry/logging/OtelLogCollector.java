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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class OtelLogCollector {

    private static final ThreadLocal<List<LogEventData>> COLLECTED_LOGS =
            ThreadLocal.withInitial(ArrayList::new);

    private OtelLogCollector() {
    }

    public static void collect(String level, String logger, String message, String threadName, long threadId) {
        COLLECTED_LOGS.get().add(new LogEventData(level, logger, message, threadName, threadId, Instant.now()));
    }

    public static List<LogEventData> getAndClear() {
        List<LogEventData> logs = new ArrayList<>(COLLECTED_LOGS.get());
        COLLECTED_LOGS.get().clear();
        return logs;
    }

    public static void clear() {
        COLLECTED_LOGS.get().clear();
    }

    public record LogEventData(String level, String logger, String message, String threadName, long threadId, Instant timestamp) {
    }
}
