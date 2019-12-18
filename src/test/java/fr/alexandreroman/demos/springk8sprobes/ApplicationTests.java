/*
 * Copyright (c) 2019 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.alexandreroman.demos.springk8sprobes;

import lombok.Data;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationTests {
    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void contextLoads() {
    }

    @Test
    void testReadinessProbe() throws InterruptedException {
        FullHealthStatusResponse resp = restTemplate.getForObject("/actuator/health/readiness", FullHealthStatusResponse.class);
        assertThat(resp.status).isEqualTo("DOWN");
        assertThat(resp.components).isNotEmpty();

        Thread.sleep(32 * 1000);
        resp = restTemplate.getForObject("/actuator/health/readiness", FullHealthStatusResponse.class);
        assertThat(resp.status).isEqualTo("UP");
        assertThat(resp.components).isNotEmpty();
    }

    @Test
    void testLivenessProbe() {
        final HealthStatusResponse resp = restTemplate.getForObject("/actuator/health/liveness", HealthStatusResponse.class);
        assertThat(resp.status).isEqualTo("UP");
    }
}

@Data
class HealthStatusResponse {
    String status;
}

@Data
class FullHealthStatusResponse {
    String status;
    Map<String, ?> components;
}
