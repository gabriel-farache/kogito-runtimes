package org.kie.kogito.serverless.workflow.openapi;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.oidc.token.propagation.AccessTokenRequestFilter;

import jakarta.ws.rs.client.ClientRequestContext;

public class OpenApiTokenRequestFilter extends AccessTokenRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenApiTokenRequestFilter.class);

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        LOGGER.error("====> HERE");
        requestContext.getConfiguration().getProperties().forEach((k, v) -> LOGGER.error("=======> {} => {}", k, v));

    }
}
