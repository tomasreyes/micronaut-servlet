/*
 * Copyright 2017-2023 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.tck.poja;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.poja.test.TestingServerlessEmbeddedApplication;
import io.micronaut.http.tck.ServerUnderTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Optional;

@SuppressWarnings("java:S2187")
public class PojaApacheServerUnderTest implements ServerUnderTest {

    private static final Logger LOG = LoggerFactory.getLogger(PojaApacheServerUnderTest.class);

    private final ApplicationContext applicationContext;
    private final TestingServerlessEmbeddedApplication application;
    private final BlockingHttpClient client;
    private final int port;

    public PojaApacheServerUnderTest(Map<String, Object> properties) {
        properties.put("micronaut.server.context-path", "/");
        properties.put("endpoints.health.service-ready-indicator-enabled", StringUtils.FALSE);
        properties.put("endpoints.refresh.enabled", StringUtils.FALSE);
        properties.put("micronaut.security.enabled", StringUtils.FALSE);
        applicationContext = ApplicationContext
            .builder(Environment.FUNCTION, Environment.TEST)
            .eagerInitConfiguration(true)
            .eagerInitSingletons(true)
            .properties(properties)
            .deduceEnvironment(false)
            .start();
        application = applicationContext.findBean(TestingServerlessEmbeddedApplication.class)
            .orElseThrow(() -> new IllegalStateException("TestingServerlessApplication bean is required"));
        application.start();
        port = application.getPort();
        client = applicationContext.createBean(HttpClient.class, URI.create("http://localhost:" + port)).toBlocking();
    }

    @Override
    public <I, O> HttpResponse<O> exchange(HttpRequest<I> request, Argument<O> bodyType) {
        HttpResponse<O> response = client.exchange(request, bodyType);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Response status: {}", response.getStatus());
        }
        return response;
    }

    @Override
    public <I, O, E> HttpResponse<O> exchange(HttpRequest<I> request, Argument<O> bodyType, Argument<E> errorType) {
        return exchange(request, bodyType);
    }

    @Override
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    @Override
    public Optional<Integer> getPort() {
        return Optional.of(port);
    }

    @Override
    public void close() throws IOException {
        applicationContext.close();
        client.close();
    }
}
