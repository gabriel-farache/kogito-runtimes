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

import java.util.Arrays;
import java.util.logging.Handler;
import java.util.logging.Level;

import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.runtime.StartupEvent;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
public class OtelLogHandlerInitializer {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(OtelLogHandlerInitializer.class);
    private static final OtelLogHandler handler = new OtelLogHandler();

    void onStart(@Observes StartupEvent ev) {
        Logger rootLogger = LogContext.getLogContext().getLogger("");
        handler.setMinimumLevel("INFO");
        handler.setLevel(Level.INFO);

        Handler[] handlers = rootLogger.getHandlers();
        if (Arrays.stream(handlers).noneMatch(h -> h instanceof OtelLogHandler)) {
            rootLogger.addHandler(handler);
            LOGGER.info("OtelLogHandler registered with JBoss LogManager root logger. Handler count: {}", rootLogger.getHandlers().length);
        } else {
            LOGGER.debug("OtelLogHandler already registered with JBoss LogManager root logger");
        }
    }
}
