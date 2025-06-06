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
package org.kie.kogito.it;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.kie.kogito.Address;
import org.kie.kogito.AddressType;
import org.kie.kogito.Person;
import org.kie.kogito.Status;
import org.kie.kogito.usertask.model.TransitionInfo;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

import static io.restassured.RestAssured.given;
import static java.time.format.DateTimeFormatter.ISO_ZONED_DATE_TIME;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.emptyOrNullString;

public abstract class PersistenceTest {
    private static final String USER_TASK_BASE_PATH = "/usertasks/instance";

    public static final Duration TIMEOUT = Duration.ofSeconds(10);
    public static final String PROCESS_ID = "hello";
    public static final String PROCESS_EMBEDDED_ID = "embedded";
    public static final String PROCESS_MULTIPLE_INSTANCES_EMBEDDED_ID = "MultipleInstanceEmbeddedSubProcess";
    public static final String PROCESS_MULTIPLE_INSTANCES_ID = "MultipleInstanceSubProcess";
    public static final String PROCESS_ASYNC_WIH = "AsyncWIH";

    static {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test
    public void testStartApprovalAuthorized() {
        // start new approval
        String id = given()
                .body("{}")
                .contentType(ContentType.JSON)
                .when()
                .post("/AddedTask")
                .then()
                .statusCode(201)
                .body("id", notNullValue()).extract().path("id");

        try {
            // get all active approvals
            given()
                    .accept(ContentType.JSON)
                    .when()
                    .get("/AddedTask")
                    .then()
                    .statusCode(200)
                    .body("size()", is(1), "[0].id", is(id));

            // get just started approval
            given()
                    .accept(ContentType.JSON)
                    .when()
                    .get("/AddedTask/" + id)
                    .then()
                    .statusCode(200)
                    .body("id", is(id));

            // tasks assigned in just started approval

            String userTaskId = given()
                    .basePath(USER_TASK_BASE_PATH)
                    .queryParam("user", "mary")
                    .queryParam("group", "managers")
                    .contentType(ContentType.JSON)
                    .when()
                    .get()
                    .then()
                    .statusCode(200)
                    .extract()
                    .body()
                    .path("[0].id");

            given()
                    .contentType(ContentType.JSON)
                    .basePath(USER_TASK_BASE_PATH)
                    .queryParam("user", "mary")
                    .queryParam("group", "managers")
                    .body(new TransitionInfo("complete"))
                    .when()
                    .post("/{userTaskId}/transition", userTaskId)
                    .then()
                    .statusCode(200);

            // get all active approvals
            given()
                    .accept(ContentType.JSON)
                    .when()
                    .get("/AddedTask")
                    .then()
                    .statusCode(200)
                    .body("size()", is(1));
        } finally {
            // Cleanup: Remove the AddedTask
            given()
                    .when()
                    .delete("/AddedTask/" + id)
                    .then()
                    .statusCode(200);
        }
    }

    @Test
    void testPersistence() {
        Person person = new Person("Name", 10, BigDecimal.valueOf(5.0), Instant.now().truncatedTo(ChronoUnit.MILLIS), ZonedDateTime.now(ZoneOffset.UTC));
        Person relative = new Person("relative", 5, BigDecimal.valueOf(5.0), Instant.now().truncatedTo(ChronoUnit.MILLIS), ZonedDateTime.now(ZoneOffset.UTC));
        relative.setStatus(Status.ARCHIVED);
        person.setRelatives(new Person[] { relative });
        person.setParent(relative);
        person.setAddresses(asList(new Address("Brisbane"), new Address(null, "Sydney", null, null, AddressType.SHIPPING, Status.ARCHIVED)));
        final String pid = given().contentType(ContentType.JSON)
                .when()
                .body(Map.of("var1", "Tiago", "person", person))
                .post("/{processId}", PROCESS_ID)
                .then()
                .statusCode(201)
                .header("Location", not(emptyOrNullString()))
                .body("id", not(emptyOrNullString()))
                .body("var1", equalTo("Tiago"))
                .body("var2", equalTo("Hello Tiago! Script"))
                .body("person.id", equalTo(person.getId()))
                .body("person.status", equalTo(person.getStatus().name()))
                .body("person.name", equalTo(person.getName()))
                .body("person.age", equalTo(person.getAge()))
                .body("person.score", equalTo(person.getScore().floatValue()))
                .body("person.created", equalTo(DateTimeFormatter.ISO_INSTANT.format(person.getCreated())))
                .body("person.updated", equalTo(person.getUpdated().format(ISO_ZONED_DATE_TIME)))
                .body("person.parent.name", equalTo(relative.getName()))
                .body("person.parent.age", equalTo(relative.getAge()))
                .body("person.parent.status", equalTo(relative.getStatus().name()))
                .body("person.relatives.size()", equalTo(1))
                .body("person.relatives[0].name", equalTo(relative.getName()))
                .body("person.relatives[0].age", equalTo(relative.getAge()))
                .body("person.relatives[0].status", equalTo(relative.getStatus().name()))
                .body("person.addresses.size()", equalTo(person.getAddresses().size()))
                .body("person.addresses[0].city", equalTo(person.getAddresses().get(0).getCity()))
                .body("person.addresses[0].type", equalTo(person.getAddresses().get(0).getType().name()))
                .body("person.addresses[0].status", equalTo(person.getAddresses().get(0).getStatus().name()))
                .body("person.addresses[1].city", equalTo(person.getAddresses().get(1).getCity()))
                .body("person.addresses[1].type", equalTo(person.getAddresses().get(1).getType().name()))
                .body("person.addresses[1].status", equalTo(person.getAddresses().get(1).getStatus().name()))
                .extract()
                .path("id");

        final String createdPid = given().contentType(ContentType.JSON)
                .when()
                .get("/{processId}/{id}", PROCESS_ID, pid)
                .then()
                .statusCode(200)
                .body("id", not(emptyOrNullString()))
                .body("var1", equalTo("Tiago"))
                .body("var2", equalTo("Hello Tiago! Script"))
                .body("person.id", equalTo(person.getId()))
                .body("person.name", equalTo(person.getName()))
                .body("person.age", equalTo(person.getAge()))
                .body("person.score", equalTo(person.getScore().floatValue()))
                .body("person.created", equalTo(DateTimeFormatter.ISO_INSTANT.format(person.getCreated().truncatedTo(ChronoUnit.MILLIS))))
                .body("person.updated", equalTo(person.getUpdated().format(ISO_ZONED_DATE_TIME)))
                .body("person.parent.name", equalTo(relative.getName()))
                .body("person.parent.age", equalTo(relative.getAge()))
                .body("person.parent.status", equalTo(relative.getStatus().name()))
                .body("person.relatives.size()", equalTo(1))
                .body("person.relatives[0].name", equalTo(relative.getName()))
                .body("person.relatives[0].age", equalTo(relative.getAge()))
                .body("person.relatives[0].status", equalTo(relative.getStatus().name()))
                .body("person.addresses.size()", equalTo(person.getAddresses().size()))
                .body("person.addresses[0].city", equalTo(person.getAddresses().get(0).getCity()))
                .body("person.addresses[0].type", equalTo(person.getAddresses().get(0).getType().name()))
                .body("person.addresses[0].status", equalTo(person.getAddresses().get(0).getStatus().name()))
                .body("person.addresses[1].city", equalTo(person.getAddresses().get(1).getCity()))
                .body("person.addresses[1].type", equalTo(person.getAddresses().get(1).getType().name()))
                .body("person.addresses[1].status", equalTo(person.getAddresses().get(1).getStatus().name()))
                .extract()
                .path("id");

        assertThat(createdPid).isEqualTo(pid);

        //signal to continue and complete the process instance
        given().contentType(ContentType.JSON)
                .when()
                .post("/{processId}/{id}/continue", PROCESS_ID, pid)
                .then()
                .statusCode(200);

        given().contentType(ContentType.JSON)
                .when()
                .get("/{processId}/{id}", PROCESS_ID, pid)
                .then()
                .statusCode(404);
    }

    @Test
    void testHealthCheck() {
        given().contentType(ContentType.JSON)
                .when()
                .get("/q/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }

    @Test
    void testEmbeddedProcess() {
        final String pId = given().contentType(ContentType.JSON)
                .pathParam("processId", PROCESS_EMBEDDED_ID)
                .when()
                .post("/{processId}")
                .then()
                .statusCode(201)
                .body("id", not(emptyOrNullString()))
                .extract()
                .path("id");

        String taskId = given()
                .contentType(ContentType.JSON)
                .queryParam("user", "admin")
                .queryParam("group", "managers")
                .pathParam("pId", pId)
                .pathParam("processId", PROCESS_EMBEDDED_ID)
                .when()
                .get("/{processId}/{pId}/tasks")
                .then()
                .statusCode(200)
                .extract()
                .path("[0].id");

        given().contentType(ContentType.JSON)
                .pathParam("pId", pId)
                .pathParam("taskId", taskId)
                .pathParam("processId", PROCESS_EMBEDDED_ID)
                .queryParam("user", "admin")
                .queryParam("group", "managers")
                .body("{}")
                .when()
                .post("/{processId}/{pId}/Task/{taskId}/phases/complete")
                .then()
                .statusCode(200);

    }

    @Test
    void testMultipleEmbeddedInstance() {
        String pId = given().contentType(ContentType.JSON)
                .pathParam("processId", PROCESS_MULTIPLE_INSTANCES_EMBEDDED_ID)
                .when()
                .post("/{processId}")
                .then()
                .statusCode(201)
                .body("id", not(emptyOrNullString()))
                .extract()
                .path("id");

        String taskId = given()
                .contentType(ContentType.JSON)
                .queryParam("user", "admin")
                .pathParam("pId", pId)
                .pathParam("processId", PROCESS_MULTIPLE_INSTANCES_EMBEDDED_ID)
                .when()
                .get("/{processId}/{pId}/tasks")
                .then()
                .statusCode(200)
                .extract()
                .path("[0].id");

        given().contentType(ContentType.JSON)
                .pathParam("pId", pId)
                .pathParam("taskId", taskId)
                .pathParam("processId", PROCESS_MULTIPLE_INSTANCES_EMBEDDED_ID)
                .queryParam("user", "admin")
                .queryParam("group", "admin")
                .body("{}")
                .when()
                .post("/{processId}/{pId}/Task/{taskId}/phases/complete")
                .then()
                .statusCode(200);

        given().contentType(ContentType.JSON)
                .pathParam("processId", PROCESS_MULTIPLE_INSTANCES_EMBEDDED_ID)
                .pathParam("pId", pId)
                .when()
                .get("/{processId}/{pId}")
                .then()
                .statusCode(404);
    }

    @Test
    void testMultipleInstance() {
        String pId = given().contentType(ContentType.JSON)
                .pathParam("processId", PROCESS_MULTIPLE_INSTANCES_ID)
                .when()
                .post("/{processId}")
                .then()
                .statusCode(201)
                .body("id", not(emptyOrNullString()))
                .extract()
                .path("id");

        given().contentType(ContentType.JSON)
                .pathParam("processId", PROCESS_MULTIPLE_INSTANCES_ID)
                .pathParam("pId", pId)
                .when()
                .get("/{processId}/{pId}")
                .then()
                .statusCode(404);
    }

    @Test
    void testAsyncWIH() {
        String pId = given().contentType(ContentType.JSON)
                .pathParam("processId", PROCESS_ASYNC_WIH)
                .when()
                .post("/{processId}")
                .then()
                .statusCode(201)
                .body("id", not(emptyOrNullString()))
                .extract()
                .path("id");

        await().atMost(TIMEOUT)
                .untilAsserted(() -> given().contentType(ContentType.JSON)
                        .pathParam("processId", PROCESS_ASYNC_WIH)
                        .pathParam("pId", pId)
                        .when()
                        .get("/{processId}/{pId}")
                        .then()
                        .statusCode(404));
    }

    @Test
    void testAdHocProcess() {
        String pId = given().contentType(ContentType.JSON)
                .when()
                .body(Map.of("status", "new"))
                .post("/AdHocProcess")
                .then()
                .statusCode(201)
                .body("id", not(emptyOrNullString()))
                .body("status", equalTo("new"))
                .extract()
                .path("id");

        String location = given().contentType(ContentType.JSON)
                .pathParam("pId", pId)
                .queryParam("user", "user")
                .queryParam("group", "agroup")
                .when()
                .post("/AdHocProcess/{pId}/CloseTask/trigger")
                .then()
                .log().everything()
                .statusCode(201)
                .header("Location", notNullValue())
                .extract()
                .header("Location");

        String taskId = location.substring(location.lastIndexOf("/") + 1);

        given()
                .queryParam("user", "user")
                .queryParam("group", "agroup")
                .contentType(ContentType.JSON)
                .when()
                .body(Map.of("status", "closed"))
                .post("/AdHocProcess/{pId}/CloseTask/{taskId}", pId, taskId)
                .then()
                .statusCode(200)
                .body("status", equalTo("closed"));

        given().contentType(ContentType.JSON)
                .pathParam("pId", pId)
                .when()
                .get("/AdHocProcess/{pId}")
                .then()
                .statusCode(404);
    }

    @Test
    void testReusableSubProcessWithServiceTaskPersistence() {
        final String pid = given().contentType(ContentType.JSON)
                .when()
                .body(Map.of("var1", "Arthur", "var3", "Tiago"))
                .post("/{processId}", "reusableSubprocessHello")
                .then()
                .statusCode(201)
                .header("Location", not(emptyOrNullString()))
                .body("id", not(emptyOrNullString()))
                .body("var1", equalTo("Arthur"))
                .body("var2", equalTo("Hello Arthur! Script > Script End !!!"))
                .body("var3", equalTo("Tiago"))
                .body("var4", equalTo("Hello Tiago! > Script End !!!"))
                .extract()
                .path("id");

        //check if completed
        given().contentType(ContentType.JSON)
                .when()
                .get("/reusableSubprocessHello/{id}", pid)
                .then()
                .statusCode(404);
    }
}
