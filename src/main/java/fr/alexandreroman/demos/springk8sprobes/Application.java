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

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

@RestController
class IndexController {
    @GetMapping(value = "/", produces = MediaType.TEXT_PLAIN_VALUE)
    String index() {
        return "Hello";
    }
}

@Component
@Slf4j
class FakeHealthIndicator implements HealthIndicator {
    private final long timeCreated = System.currentTimeMillis();

    @Override
    public Health health() {
        // This health indicator simulates a service indicator (such as a database)
        // which takes a lot of time to get initialized.
        final long now = System.currentTimeMillis();
        final boolean ready = (now - timeCreated) > 30000;
        log.debug("Fake health indicator: {}", ready ? "UP" : "DOWN");

        return ready ? Health.up().build() : Health.down().build();
    }
}
