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
package org.kie.addons.security.token.persistence.runtime;

import java.util.logging.Logger;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

@Provider
public class RequestLoggingFilter implements ContainerRequestFilter {

    private static final Logger LOGGER = Logger.getLogger(RequestLoggingFilter.class.getName());

    @ConfigProperty(name = "quarkus.sonataflow.security.token-persistence", defaultValue = "false")
    boolean tokenPersistenceEnabled;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        // Log the headers
        if (tokenPersistenceEnabled) {
            LOGGER.info("Request Headers:");
            requestContext.getHeaders().forEach((headerName, headerValues) -> headerValues.forEach(headerValue -> LOGGER.info(headerName + ": " + headerValue)));
        }
    }
}
