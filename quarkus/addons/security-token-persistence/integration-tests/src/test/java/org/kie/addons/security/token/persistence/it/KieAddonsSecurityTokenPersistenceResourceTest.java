package org.kie.addons.security.token.persistence.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class KieAddonsSecurityTokenPersistenceResourceTest {

    @Test
    public void testHelloEndpoint() {
        given()
                .when().get("/kie-addons-security-token-persistence")
                .then()
                .statusCode(200)
                .body(is("Hello kie-addons-security-token-persistence"));
    }
}
