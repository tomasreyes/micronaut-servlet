/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.servlet.jetty;

import static io.micronaut.core.util.StringUtils.isEmpty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.env.Environment;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.ssl.ClientAuthentication;
import io.micronaut.http.ssl.SslConfiguration;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.scheduling.LoomSupport;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.servlet.engine.MicronautServletConfiguration;
import io.micronaut.servlet.engine.server.ServletServerFactory;
import io.micronaut.servlet.engine.server.ServletStaticResourceConfiguration;
import jakarta.inject.Singleton;
import jakarta.servlet.ServletContainerInitializer;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;
import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

/**
 * Factory for the Jetty server.
 *
 * @author graemerocher
 * @since 1.0
 */
@Factory
public class JettyFactory extends ServletServerFactory {

    public static final String RESOURCE_BASE = "resourceBase";

    private final JettyConfiguration jettyConfiguration;

    /**
     * Default constructor.
     *
     * @param resourceResolver             The resource resolver
     * @param serverConfiguration          The server config
     * @param sslConfiguration             The SSL config
     * @param applicationContext           The app context
     * @param staticResourceConfigurations The static resource configs
     */
    public JettyFactory(
        ResourceResolver resourceResolver,
        JettyConfiguration serverConfiguration,
        SslConfiguration sslConfiguration,
        ApplicationContext applicationContext,
        List<ServletStaticResourceConfiguration> staticResourceConfigurations) {
        super(
            resourceResolver,
            serverConfiguration,
            sslConfiguration,
            applicationContext,
            staticResourceConfigurations
        );
        this.jettyConfiguration = serverConfiguration;
    }

    /**
     * Builds the Jetty server bean.
     *
     * @param applicationContext    This application context
     * @param configuration         The servlet configuration
     * @param jettySslConfiguration The Jetty SSL config
     * @return The Jetty server bean
     */
    protected Server jettyServer(
        ApplicationContext applicationContext,
        MicronautServletConfiguration configuration,
        JettyConfiguration.JettySslConfiguration jettySslConfiguration
    ) {
        return jettyServer(
            applicationContext,
            configuration,
            jettySslConfiguration,
            applicationContext.getBeansOfType(ServletContainerInitializer.class)
        );
    }

    /**
     * Builds the Jetty server bean.
     *
     * @param applicationContext          This application context
     * @param configuration               The servlet configuration
     * @param jettySslConfiguration       The Jetty SSL config
     * @param servletContainerInitializers The micronaut servlet initializer
     * @return The Jetty server bean
     */
    @Singleton
    @Primary
    protected Server jettyServer(
        ApplicationContext applicationContext,
        MicronautServletConfiguration configuration,
        JettyConfiguration.JettySslConfiguration jettySslConfiguration,
        Collection<ServletContainerInitializer> servletContainerInitializers
    ) {
        final String host = getConfiguredHost();
        final Integer port = getConfiguredPort();
        String contextPath = getContextPath();

        Server server = newServer(applicationContext, configuration);

        jettyConfiguration.getRequestLog().ifPresent(requestLog -> {
            if (requestLog.isEnabled()) {
                server.setRequestLog(new CustomRequestLog(
                    requestLog.requestLogWriter,
                    requestLog.getPattern()
                ));
            }
        });

        final ServletContextHandler contextHandler = newJettyContext(server, contextPath);
        server.setHandler(contextHandler);
        configureServletInitializer(server, contextHandler, servletContainerInitializers);
        ResourceFactory resourceFactory = ResourceFactory.of(server);

        final SslConfiguration sslConfiguration = getSslConfiguration();
        ServerConnector https = null;
        if (sslConfiguration.isEnabled()) {
            https = newHttpsConnector(server, sslConfiguration, jettySslConfiguration, resourceFactory);

        }
        final ServerConnector http = newHttpConnector(server, host, port);
        configureConnectors(server, http, https);

        return server;
    }

    /**
     * Create the HTTP connector.
     * @param server The server
     * @param host The host
     * @param port The port
     * @return The server connector.
     */
    protected @NonNull ServerConnector newHttpConnector(@NonNull Server server, @NonNull String host, @NonNull Integer port) {
        HttpConfiguration httpConfig = jettyConfiguration.getHttpConfiguration();
        HttpConnectionFactory http11 = new HttpConnectionFactory(httpConfig);
        HttpServerConfiguration serverConfiguration = getServerConfiguration();
        final ServerConnector http;
        if (serverConfiguration.getHttpVersion() == io.micronaut.http.HttpVersion.HTTP_2_0) {
            HTTP2CServerConnectionFactory h2c = new HTTP2CServerConnectionFactory(httpConfig);
            http = new ServerConnector(server, http11, h2c);
        } else {
            http = new ServerConnector(server, http11);
        }

        http.setPort(port);
        http.setHost(host);
        return http;
    }

    /**
     * Create the HTTPS connector.
     *
     * @param server                The server
     * @param sslConfiguration      The SSL configuration
     * @param jettySslConfiguration The Jetty SSL configuration
     * @param resourceFactory
     * @return The server connector
     */
    protected @NonNull ServerConnector newHttpsConnector(
        @NonNull Server server,
        @NonNull SslConfiguration sslConfiguration,
        @NonNull JettyConfiguration.JettySslConfiguration jettySslConfiguration, ResourceFactory resourceFactory) {
        ServerConnector https;
        final HttpConfiguration httpConfig = jettyConfiguration.getHttpConfiguration();
        int securePort = sslConfiguration.getPort();
        if (securePort == SslConfiguration.DEFAULT_PORT && getEnvironment().getActiveNames().contains(Environment.TEST)) {
            securePort = 0; // random port
        }
        httpConfig.setSecurePort(securePort);

        SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();

        ClientAuthentication clientAuth = sslConfiguration.getClientAuthentication().orElse(ClientAuthentication.NEED);
        switch (clientAuth) {
            case WANT:
                sslContextFactory.setWantClientAuth(true);
                break;
            case NEED:
            default:
                sslContextFactory.setNeedClientAuth(true);
        }

        sslConfiguration.getProtocol().ifPresent(sslContextFactory::setProtocol);
        sslConfiguration.getProtocols().ifPresent(sslContextFactory::setIncludeProtocols);
        sslConfiguration.getCiphers().ifPresent(sslConfiguration::setCiphers);
        final SslConfiguration.KeyStoreConfiguration keyStoreConfig = sslConfiguration.getKeyStore();
        keyStoreConfig.getPassword().ifPresent(sslContextFactory::setKeyStorePassword);
        keyStoreConfig.getPath().ifPresent(path -> {
            if (path.startsWith(ServletStaticResourceConfiguration.CLASSPATH_PREFIX)) {
                String cp = path.substring(ServletStaticResourceConfiguration.CLASSPATH_PREFIX.length());
                sslContextFactory.setKeyStorePath(resourceFactory.newClassLoaderResource(cp).getURI().toString());
            } else {
                sslContextFactory.setKeyStorePath(path);
            }
        });
        keyStoreConfig.getProvider().ifPresent(sslContextFactory::setKeyStoreProvider);
        keyStoreConfig.getType().ifPresent(sslContextFactory::setKeyStoreType);
        SslConfiguration.TrustStoreConfiguration trustStore = sslConfiguration.getTrustStore();
        trustStore.getPassword().ifPresent(sslContextFactory::setTrustStorePassword);
        trustStore.getType().ifPresent(sslContextFactory::setTrustStoreType);
        trustStore.getPath().ifPresent(path -> {
            if (path.startsWith(ServletStaticResourceConfiguration.CLASSPATH_PREFIX)) {
                String cp = path.substring(ServletStaticResourceConfiguration.CLASSPATH_PREFIX.length());
                sslContextFactory.setTrustStorePath(resourceFactory.newClassLoaderResource(cp).getURI().toString());
            } else {
                sslContextFactory.setTrustStorePath(path);
            }
        });
        trustStore.getProvider().ifPresent(sslContextFactory::setTrustStoreProvider);

        HttpConfiguration httpsConfig = new HttpConfiguration(httpConfig);
        httpsConfig.addCustomizer(jettySslConfiguration);

        // The ConnectionFactory for HTTP/1.1.
        HttpConnectionFactory http11 = new HttpConnectionFactory(httpsConfig);

        if (getServerConfiguration().getHttpVersion() == io.micronaut.http.HttpVersion.HTTP_2_0) {
            // The ConnectionFactory for HTTP/2.
            HTTP2ServerConnectionFactory h2 = new HTTP2ServerConnectionFactory(httpConfig);
            // The ALPN ConnectionFactory.
            ALPNServerConnectionFactory alpn = new ALPNServerConnectionFactory();
            // The default protocol to use in case there is no negotiation.
            alpn.setDefaultProtocol(http11.getProtocol());
            // The ConnectionFactory for TLS.
            SslConnectionFactory tls = new SslConnectionFactory(sslContextFactory, alpn.getProtocol());
            // The ServerConnector instance.
            https = new ServerConnector(server, tls, alpn, h2, http11);
        } else {
            SslConnectionFactory sslConnectionFactory = new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString());
            https = new ServerConnector(server,
                sslConnectionFactory,
                http11
            );
        }

        https.setPort(securePort);
        return https;
    }

    /**
     * Configures the servlet initializer.
     *
     * @param server                      The server
     * @param contextHandler              The context handler
     * @param servletContainerInitializers The servlet initializers
     */
    protected void configureServletInitializer(Server server, ServletContextHandler contextHandler, Collection<ServletContainerInitializer> servletContainerInitializers) {
        for (ServletContainerInitializer servletContainerInitializer : servletContainerInitializers) {
            contextHandler.addServletContainerInitializer(servletContainerInitializer);
        }

        List<ContextHandler> resourceHandlers = Stream.concat(
            Stream.of(contextHandler),
            getStaticResourceConfigurations().stream().map(servletStaticResourceConfiguration -> toHandler(servletStaticResourceConfiguration, ResourceFactory.of(contextHandler)))
        ).toList();

        ContextHandlerCollection contextHandlerCollection = new ContextHandlerCollection(
            resourceHandlers.toArray(new ContextHandler[0])
        );
        server.setHandler(contextHandlerCollection);
    }

    /**
     * Create the Jetty context.
     *
     * @param server      The server
     * @param contextPath The context path
     * @return The handler
     */
    protected @NonNull ServletContextHandler newJettyContext(@NonNull Server server, @NonNull String contextPath) {
        return new ServletContextHandler(
            contextPath,
            false,
            false
        );
    }

    /**
     * Configures the server connectors.
     *
     * @param server        The server
     * @param http          The HTTP connector
     * @param https         The HTTPS connector if configured.
     */
    protected void configureConnectors(@NonNull Server server, @NonNull ServerConnector http, @Nullable ServerConnector https) {
        HttpServerConfiguration serverConfiguration = getServerConfiguration();
        if (https != null) {
            server.addConnector(https); // must be first
            if (serverConfiguration.isDualProtocol()) {
                server.addConnector(http);
            }
        } else {
            server.addConnector(http);
        }
    }

    /**
     * Create a new server instance.
     *
     * @param applicationContext The application context
     * @param configuration      The configuration
     * @return The server
     */
    protected @NonNull Server newServer(@NonNull ApplicationContext applicationContext, @NonNull MicronautServletConfiguration configuration) {
        QueuedThreadPool threadPool;
        if (configuration.getMaxThreads() != null) {
            if (configuration.getMinThreads() != null) {
                threadPool = new QueuedThreadPool(configuration.getMaxThreads(), configuration.getMinThreads());
            } else {
                threadPool = new QueuedThreadPool(configuration.getMaxThreads());
            }
        } else {
            threadPool = new QueuedThreadPool();
        }

        if (configuration.isEnableVirtualThreads() && LoomSupport.isSupported()) {
            threadPool.setVirtualThreadsExecutor(
                applicationContext.getBean(ExecutorService.class, Qualifiers.byName(TaskExecutors.BLOCKING))
            );
        }
        return new Server(threadPool);
    }

    /**
     * For each static resource configuration, create a {@link ContextHandler} that serves the static resources.
     *
     * @param config The static resource configuration
     * @return the context handler
     */
    private ContextHandler toHandler(ServletStaticResourceConfiguration config, ResourceFactory resourceFactory) {
        ResourceHandler resourceHandler = new ResourceHandler();
        Resource[] resourceArray = config.getPaths().stream()
            .map(path -> {
                Resource resource;
                if (path.startsWith(ServletStaticResourceConfiguration.CLASSPATH_PREFIX)) {
                    String cp = path.substring(ServletStaticResourceConfiguration.CLASSPATH_PREFIX.length());
                    resource = resourceFactory.newClassLoaderResource(cp);
                } else {
                    try {
                        resource = resourceFactory.newResource(path);
                    } catch (Exception e) {
                        throw new ConfigurationException("Static resource path doesn't exist: " + path, e);
                    }
                }
                if (resource == null || !resource.exists()) {
                    throw new ConfigurationException("Static resource path doesn't exist: " + path);
                }
                return resource;
            }).toArray(Resource[]::new);

        String path = config.getMapping();
        if (path.endsWith("/**")) {
            path = path.substring(0, path.length() - 3);
        }

        final String mapping = path;

        Resource combined = ResourceFactory.combine(resourceArray);
        resourceHandler.setBaseResource(combined);
        resourceHandler.setDirAllowed(false);
        if (!isEmpty(config.getCacheControl())) {
            resourceHandler.setCacheControl(config.getCacheControl());
        }

        ContextHandler contextHandler = new ContextHandler(path);
        contextHandler.setHandler(resourceHandler);
        contextHandler.setDisplayName("Static Resources " + mapping);

        return contextHandler;
    }
}
