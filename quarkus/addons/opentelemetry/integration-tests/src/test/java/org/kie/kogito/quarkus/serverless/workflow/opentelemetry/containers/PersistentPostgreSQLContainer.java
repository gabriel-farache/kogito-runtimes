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
package org.kie.kogito.quarkus.serverless.workflow.opentelemetry.containers;

import java.time.Duration;

import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL container wrapper for workflow stop/resume testing.
 * This container persists across Quarkus application restarts to maintain workflow state.
 */
public class PersistentPostgreSQLContainer extends PostgreSQLContainer<PersistentPostgreSQLContainer> {

    public static final String NETWORK_ALIAS = "postgres";
    public static final int POSTGRESQL_PORT = 5432;

    public PersistentPostgreSQLContainer(Network network) {
        super(DockerImageName.parse("mirror.gcr.io/postgres:15.9-alpine3.20")
                .asCompatibleSubstituteFor("postgres"));

        withNetwork(network);
        withNetworkAliases(NETWORK_ALIAS);
        withDatabaseName("kogito");
        withUsername("kogito");
        withPassword("kogito");
        withStartupTimeout(Duration.ofMinutes(2));
    }

    /**
     * Returns the JDBC URL that can be used by containers within the same Docker network.
     * Uses the network alias instead of the actual host to enable container-to-container communication.
     */
    public String getNetworkJdbcUrl() {
        return String.format("jdbc:postgresql://%s:%d/%s?stringtype=unspecified",
                NETWORK_ALIAS, POSTGRESQL_PORT, getDatabaseName());
    }
}
