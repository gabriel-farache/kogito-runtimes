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
package org.kie.kogito.quarkus.serverless.workflow.opentelemetry.mode.processspan;

import org.kie.kogito.quarkus.serverless.workflow.opentelemetry.config.SonataFlowOtelConfig;
import org.kie.kogito.services.uow.CollectingUnitOfWorkFactory;
import org.kie.kogito.services.uow.DefaultUnitOfWorkManager;
import org.kie.kogito.uow.UnitOfWorkManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class OtelUnitOfWorkManagerProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(OtelUnitOfWorkManagerProducer.class);

    @Inject
    SonataFlowOtelConfig config;

    @Produces
    @Singleton
    public UnitOfWorkManager produceUnitOfWorkManager() {
        if (config.isShortTransitionMode()) {
            LOGGER.info("Producing OtelUnitOfWorkManager for short transition mode");
            return new DefaultUnitOfWorkManager(new OtelUnitOfWorkFactory());
        } else {
            LOGGER.debug("Using default CollectingUnitOfWorkFactory for long/default transition mode");
            return new DefaultUnitOfWorkManager(new CollectingUnitOfWorkFactory());
        }
    }
}
