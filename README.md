# Setting up Kubernetes readiness and liveness probes with Spring Boot

This project shows how to leverage the
[Health indicator groups](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-2.2-Release-Notes#health-indicator-groups)
feature (available in Spring Boot 2.2) to set up
[readiness and liveness probes](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/)
when deploying apps to Kubernetes.

As a good Kubernetes citizen, your app should provide probes to monitor service
availability. A readiness probe tells Kubernetes if your app is ready to serve
traffic: if your app is not ready, then no traffic will be sent to your app.
Usually, an app is ready when external dependencies (such as databases) are ready to
be used. A readiness probe should verify that these dependencies are up.

A liveness probe is used by Kubernetes to verify that your app is "alive".
Being alive means that the main thread in your app is not frozen, and that new
requests can be processed. There should no logic in a liveness probe: for example
you don't need to check for external dependencies availability.

For more information about best practices around Kubernetes probes, please read
[this article](https://medium.com/metrosystemsro/kubernetes-readiness-liveliness-probes-best-practices-86c3cd9f0b4a).

## How does it work?

You can easily create readiness and liveness probes using
[Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-features.html#production-ready).
You don't need to write any source code.

Make sure you added the Spring Boot Actuator dependency:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

Let's create two healthcheck groups in your `application.properties` (or YAML):
```ini
management.endpoint.health.group.readiness.include=*
management.endpoint.health.group.readiness.show-details=always

management.endpoint.health.group.liveness.include=ping
management.endpoint.health.group.liveness.show-details=never
```

The `readiness` group includes all existing health indicators, with details.
For example, if you use Spring Data JPA a database indicator will be automatically
added for you, and this indicator will be included in this probe. 

The `readiness` probe will only return a `200 OK` status when all health indicators
are up. When your app is deployed to Kubernetes, you don't want to serve traffic
until you're ready to process incoming requests: you need to make sure that external
services (such as databases) are available.
This is exactly what this probe is doing here.

The `liveness` probe is pretty simple: the idea is to verify that your app is "alive".
In this case, this probe is just returning a `200 OK` status with no logic.
We want this probe to be fast and simple, since it will be periodically called by
Kubernetes (typically every 1 sec).

Using Spring Boot Actuator, these probes are made available under these endpoints:
 - `readiness` probe: `/actuator/health/readiness`
 - `liveness` probe: `/actuator/health/liveness`

## How to use it?

Compile this app using JDK 11+:
```bash
$ ./mvnw clean package
```

Start this app:
```bash
$ java -jar target/spring-k8s-probes-demo.jar
```

Hit the readiness probe:
```bash
$ curl localhost:8080/actuator/health/readiness
{"status":"DOWN","components":{"diskSpace":{"status":"UP","details":{"total":499963174912,"free":163210244096,"threshold":10485760}},"fake":{"status":"DOWN"},"ping":{"status":"UP"}}}%
```

This app includes a fake health indicator which takes about 30 seconds before it gets ready.
This indicator simulates a connection to a slow database:
that's why you see the status is down.

Relaunch the same command after 30 seconds:
```bash
$ curl localhost:8080/actuator/health/readiness
{"status":"UP","components":{"diskSpace":{"status":"UP","details":{"total":499963174912,"free":163208421376,"threshold":10485760}},"fake":{"status":"UP"},"ping":{"status":"UP"}}}%
```

In the meantime, you can check the liveness probe:
```bash
$ curl localhost:8080/actuator/health/liveness
{"status":"UP"}%
```

As you can see, this probe simply returns a basic status.

You need to include these probes in your Kubernetes deployment:
```yaml
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: app
  namespace: spring-k8s-probes-demo
spec:
  replicas: 1
  selector:
    matchLabels:
      role: app
  template:
    metadata:
      labels:
        role: app
    spec:
      containers:
        - name: app
          image: alexandreroman/spring-k8s-probes-demo
          imagePullPolicy: Always
          resources:
            limits:
              memory: 1Gi
          ports:
            - name: healthcheck
              containerPort: 8080
          readinessProbe:
            # Readiness probe is used to check if this app is ready to serve traffic.
            httpGet:
              port: healthcheck
              path: /actuator/health/readiness
            initialDelaySeconds: 10
          livenessProbe:
            # Liveness probe is used to check if this app is responding to requests
            # (after it is marked as "ready").
            httpGet:
              port: healthcheck
              path: /actuator/health/liveness
            initialDelaySeconds: 60
            periodSeconds: 1
```

## Deploying this app to Kubernetes

You're ready to deploy this app to Kubernetes.

Use the Kubernetes manifest files in directory `k8s` to deploy this app:
```bash
$ kubectl apply -f k8s
```

The app is deployed to the namespace `spring-k8s-probes-demo`.

Let's have a look at the pods:
```bash
$ kubectl -n spring-k8s-probes-demo get pods
NAME                   READY   STATUS    RESTARTS   AGE
app-8564f6cdbb-p2dlz   0/1     Running   0          4s
```

This pod is not running yet, since the readiness probe is still `DOWN`.
Let's see app logs:
```bash
$ kubectl -n spring-k8s-probes-demo logs app-8564f6cdbb-p2dlz

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::        (v2.2.2.RELEASE)

2019-12-19 13:35:46.351  WARN 1 --- [           main] pertySourceApplicationContextInitializer : Skipping 'cloud' property source addition because not in a cloud
2019-12-19 13:35:46.357  WARN 1 --- [           main] nfigurationApplicationContextInitializer : Skipping reconfiguration because not in a cloud
2019-12-19 13:35:46.367  INFO 1 --- [           main] f.a.demos.springk8sprobes.Application    : Starting Application on app-8564f6cdbb-p2dlz with PID 1 (/workspace/BOOT-INF/classes started by vcap in /workspace)
...
2019-12-19 13:36:02.573  INFO 1 --- [nio-8080-exec-1] o.s.web.servlet.DispatcherServlet        : Completed initialization in 23 ms
2019-12-19 13:36:02.593 DEBUG 1 --- [nio-8080-exec-1] o.s.web.servlet.DispatcherServlet        : GET "/actuator/health/readiness", parameters={}
2019-12-19 13:36:02.744 DEBUG 1 --- [nio-8080-exec-1] o.s.web.servlet.DispatcherServlet        : Completed 503 SERVICE_UNAVAILABLE
2019-12-19 13:36:12.468 DEBUG 1 --- [nio-8080-exec-2] o.s.web.servlet.DispatcherServlet        : GET "/actuator/health/readiness", parameters={}
2019-12-19 13:36:12.475 DEBUG 1 --- [nio-8080-exec-2] o.s.web.servlet.DispatcherServlet        : Completed 503 SERVICE_UNAVAILABLE
```

As expected, the readiness probe is used by Kubernetes to check if this app is ready.

Wait for the pod to be ready:
```bash
$ kubectl -n spring-k8s-probes-demo get pods
NAME                   READY   STATUS    RESTARTS   AGE
app-8564f6cdbb-p2dlz   1/1     Running   0          2m59s
```

Now let's have a look at the logs again:
```bash
...
2019-12-19 13:36:02.573  INFO 1 --- [nio-8080-exec-1] o.s.web.servlet.DispatcherServlet        : Completed initialization in 23 ms
2019-12-19 13:36:02.593 DEBUG 1 --- [nio-8080-exec-1] o.s.web.servlet.DispatcherServlet        : GET "/actuator/health/readiness", parameters={}
2019-12-19 13:36:02.744 DEBUG 1 --- [nio-8080-exec-1] o.s.web.servlet.DispatcherServlet        : Completed 503 SERVICE_UNAVAILABLE
2019-12-19 13:36:12.468 DEBUG 1 --- [nio-8080-exec-2] o.s.web.servlet.DispatcherServlet        : GET "/actuator/health/readiness", parameters={}
2019-12-19 13:36:12.475 DEBUG 1 --- [nio-8080-exec-2] o.s.web.servlet.DispatcherServlet        : Completed 503 SERVICE_UNAVAILABLE
2019-12-19 13:36:22.468 DEBUG 1 --- [nio-8080-exec-3] o.s.web.servlet.DispatcherServlet        : GET "/actuator/health/readiness", parameters={}
2019-12-19 13:36:22.471 DEBUG 1 --- [nio-8080-exec-3] o.s.web.servlet.DispatcherServlet        : Completed 200 OK
2019-12-19 13:36:32.468 DEBUG 1 --- [nio-8080-exec-4] o.s.web.servlet.DispatcherServlet        : GET "/actuator/health/readiness", parameters={}
2019-12-19 13:36:32.473 DEBUG 1 --- [nio-8080-exec-4] o.s.web.servlet.DispatcherServlet        : Completed 200 OK
2019-12-19 13:36:42.468 DEBUG 1 --- [nio-8080-exec-5] o.s.web.servlet.DispatcherServlet        : GET "/actuator/health/readiness", parameters={}
2019-12-19 13:36:42.472 DEBUG 1 --- [nio-8080-exec-5] o.s.web.servlet.DispatcherServlet        : Completed 200 OK
2019-12-19 13:36:44.106 DEBUG 1 --- [nio-8080-exec-6] o.s.web.servlet.DispatcherServlet        : GET "/actuator/health/liveness", parameters={}
2019-12-19 13:36:44.111 DEBUG 1 --- [nio-8080-exec-6] o.s.web.servlet.DispatcherServlet        : Completed 200 OK
2019-12-19 13:36:45.106 DEBUG 1 --- [nio-8080-exec-7] o.s.web.servlet.DispatcherServlet        : GET "/actuator/health/liveness", parameters={}
2019-12-19 13:36:45.109 DEBUG 1 --- [nio-8080-exec-7] o.s.web.servlet.DispatcherServlet        : Completed 200 OK
2019-12-19 13:36:46.106 DEBUG 1 --- [nio-8080-exec-8] o.s.web.servlet.DispatcherServlet        : GET "/actuator/health/liveness", parameters={}
```

As soon as the app is ready to serve traffic, then Kubernetes will also check for the
liveness probe every 1 second.

## Contribute

Contributions are always welcome!

Feel free to open issues & send PR.

## License

Copyright &copy; 2019 [Pivotal Software, Inc](https://pivotal.io).

This project is licensed under the [Apache Software License version 2.0](https://www.apache.org/licenses/LICENSE-2.0).
