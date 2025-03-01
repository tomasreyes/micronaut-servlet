package io.micronaut.http.server.tck.undertow.tests;

import org.junit.platform.suite.api.ExcludeClassNamePatterns;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@SelectPackages("io.micronaut.http.server.tck.tests")
@SuiteDisplayName("HTTP Server TCK for Undertow")
@ExcludeClassNamePatterns({
    "io.micronaut.http.server.tck.tests.RemoteAddressTest", // Undertow.getHost() reports an ipv6 address, not 127.0.0.1
    "io.micronaut.http.server.tck.tests.staticresources.StaticResourceTest", // Graal fails to see /assets from the TCK as a resource https://ge.micronaut.io/s/ufuhtbe5sgmxi
    "io.micronaut.http.server.tck.tests.filter.ClientResponseFilterTest", // responseFilterThrowableParameter fails under Graal https://ge.micronaut.io/s/ufuhtbe5sgmxi
    "io.micronaut.http.server.tck.tests.StreamTest", // The outputstream in Undertow is marked ready asynchronously, and we throw the error early, so sometimes there's no body for statusErrorAsFirstItem.
    "io.micronaut.http.server.tck.tests.FilterProxyTest", // see https://github.com/micronaut-projects/micronaut-core/issues/9725
    "io.micronaut.http.server.tck.tests.LocalErrorReadingBodyTest", // Cannot read body as text once stream is exhausted trying to read it as a different type See https://github.com/micronaut-projects/micronaut-servlet/pull/548
    "io.micronaut.http.server.tck.tests.jsonview.JsonViewsTest", // Not serdeable
})
public class UndertowHttpServerTestSuite {
}
