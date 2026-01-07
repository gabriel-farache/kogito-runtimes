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

import java.nio.file.Path;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.images.builder.ImageFromDockerfile;

/**
 * Custom container for running the Quarkus application with support for start/stop/restart.
 * This container is built from the target/quarkus-app directory and can be restarted
 * while PostgreSQL container persists, enabling workflow stop/resume testing.
 */
public class QuarkusAppContainer extends GenericContainer<QuarkusAppContainer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuarkusAppContainer.class);
    public static final int HTTP_PORT = 8080;
    public static final String NETWORK_ALIAS = "quarkus-app";

    public QuarkusAppContainer(Network network,
            String postgresJdbcUrl,
            String postgresUsername,
            String postgresPassword,
            String otlpEndpoint) {
        super(createImage());

        // Use NeverPull policy to prevent image name substitution for locally built images
        withImagePullPolicy(PullPolicy.ageBased(Duration.ofDays(365)));

        withNetwork(network);
        withNetworkAliases(NETWORK_ALIAS);
        withExposedPorts(HTTP_PORT);
        withLogConsumer(new Slf4jLogConsumer(LOGGER));

        // Database configuration
        withEnv("QUARKUS_DATASOURCE_JDBC_URL", postgresJdbcUrl);
        withEnv("QUARKUS_DATASOURCE_USERNAME", postgresUsername);
        withEnv("QUARKUS_DATASOURCE_PASSWORD", postgresPassword);
        withEnv("QUARKUS_DATASOURCE_DB_KIND", "postgresql");

        // OpenTelemetry configuration
        withEnv("QUARKUS_OTEL_ENABLED", "true");
        withEnv("QUARKUS_OTEL_TRACES_EXPORTER", "otlp");
        withEnv("QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT", otlpEndpoint);
        withEnv("QUARKUS_OTEL_EXPORTER_OTLP_PROTOCOL", "http/protobuf");
        withEnv("QUARKUS_OTEL_TRACES_SAMPLER", "always_on");

        // Persistence configuration
        withEnv("KOGITO_PERSISTENCE_TYPE", "jdbc");
        withEnv("KOGITO_PERSISTENCE_HEADERS_ENABLED", "true");
        withEnv("KIE_FLYWAY_ENABLED", "true");

        // Disable DevServices in container mode
        withEnv("QUARKUS_DATASOURCE_DEVSERVICES_ENABLED", "false");
        withEnv("QUARKUS_KAFKA_DEVSERVICES_ENABLED", "false");
        withEnv("QUARKUS_KUBERNETES_CLIENT_DEVSERVICES_ENABLED", "false");
        withEnv("QUARKUS_KEYCLOAK_DEVSERVICES_ENABLED", "false");

        waitingFor(Wait.forHttp("/q/health/ready")
                .forPort(HTTP_PORT)
                .withStartupTimeout(Duration.ofMinutes(3)));
    }

    private static ImageFromDockerfile createImage() {
        Path projectRoot = Path.of(System.getProperty("user.dir"));
        Path quarkusAppPath = projectRoot.resolve("target/quarkus-app");

        LOGGER.info("Building Quarkus app image from: {}", quarkusAppPath);

        // Use localhost/ prefix to prevent image name substitution from adding registry prefixes
        // The 'true' parameter enables deleteOnExit which cleans up after the test
        String imageName = "localhost/kogito-otel-stop-resume-test:" + System.currentTimeMillis();

        return new ImageFromDockerfile(imageName, true)
                .withDockerfileFromBuilder(builder -> builder
                        .from("eclipse-temurin:17-jre-alpine")
                        .copy("quarkus-app/lib/", "/deployments/lib/")
                        .copy("quarkus-app/*.jar", "/deployments/")
                        .copy("quarkus-app/app/", "/deployments/app/")
                        .copy("quarkus-app/quarkus/", "/deployments/quarkus/")
                        .workDir("/deployments")
                        .expose(8080)
                        .entryPoint("java", "-jar", "quarkus-run.jar")
                        .build())
                .withFileFromPath("quarkus-app", quarkusAppPath);
    }

    /**
     * Returns the URL to access the Quarkus application from the host machine.
     */
    public String getAppUrl() {
        return String.format("http://%s:%d", getHost(), getMappedPort(HTTP_PORT));
    }
}
