
package io.micronaut.servlet.jetty

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.server.types.files.StreamedFile
import io.micronaut.http.server.types.files.SystemFile
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Named
import spock.lang.Specification

import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ExecutorService

import static io.micronaut.http.HttpHeaders.CACHE_CONTROL
import static io.micronaut.http.HttpHeaders.CONTENT_DISPOSITION
import static io.micronaut.http.HttpHeaders.CONTENT_LENGTH
import static io.micronaut.http.HttpHeaders.CONTENT_TYPE
import static io.micronaut.http.HttpHeaders.DATE
import static io.micronaut.http.HttpHeaders.EXPIRES
import static io.micronaut.http.HttpHeaders.LAST_MODIFIED

@MicronautTest
@Property(name = "spec.name", value = "FileTypeHandlerSpec")
class JettyFileTypeHandlerSpec extends Specification {

    private static File tempFile
    private static String tempFileContents = "<html><head></head><body>HTML Page</body></html>"

    static {
        tempFile = File.createTempFile("fileTypeHandlerSpec", ".html")
        tempFile.write(tempFileContents)
        tempFile
    }

    @Inject
    @Client("/")
    HttpClient rxClient

    void "test returning a file from a controller"() {
        when:
        def response = rxClient.toBlocking().exchange('/test/html', String)

        then:
        response.code() == HttpStatus.OK.code
        response.header(CONTENT_TYPE) == "text/html"
        response.header(CONTENT_LENGTH) == null // ideally would be right length
        response.headers.getDate(DATE) < response.headers.getDate(EXPIRES)
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.headers.getDate(LAST_MODIFIED) == ZonedDateTime.ofInstant(Instant.ofEpochMilli(tempFile.lastModified()), ZoneId.of("GMT")).truncatedTo(ChronoUnit.SECONDS)
        response.body() == tempFileContents
    }

    void "test 304 is returned if the correct header is sent"() {
        when:
        MutableHttpRequest<?> request = HttpRequest.GET('/test/html')
        request.headers.ifModifiedSince(tempFile.lastModified())
        def response = rxClient.toBlocking().exchange(request, String)

        then:
        response.code() == HttpStatus.NOT_MODIFIED.code
        response.header(DATE)
    }

    void "test cache control can be overridden"() {
        when:
        MutableHttpRequest<?> request = HttpRequest.GET('/test/custom-cache-control')
        def response = rxClient.toBlocking().exchange(request, String)

        then:
        response.code() == HttpStatus.OK.code
        response.getHeaders().getAll(CACHE_CONTROL).size() == 1
        response.header(CACHE_CONTROL) == "public, immutable, max-age=31556926"
    }

    void "test what happens when a file isn't found"() {
        when:
        rxClient.toBlocking().exchange('/test/not-found', String)

        then:
        def e = thrown(HttpClientResponseException)

        when:
        def response = e.response

        then:
        response.code() == HttpStatus.NOT_FOUND.code
    }


    void "test when a system file is returned"() {
        when:
        def response = rxClient.toBlocking().exchange('/test-system/download', String)

        then:
        response.code() == HttpStatus.OK.code
        response.header(CONTENT_TYPE) == "text/html"
        response.header(CONTENT_DISPOSITION).startsWith("attachment; filename=\"fileTypeHandlerSpec")
        response.header(CONTENT_LENGTH) == null // ideally would be right length
        response.headers.getDate(DATE) < response.headers.getDate(EXPIRES)
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.headers.getDate(LAST_MODIFIED) == ZonedDateTime.ofInstant(Instant.ofEpochMilli(tempFile.lastModified()), ZoneId.of("GMT")).truncatedTo(ChronoUnit.SECONDS)
        response.body() == tempFileContents
    }

    void "test when an attached streamed file is returned"() {
        when:
        def response = rxClient.toBlocking().exchange('/test-stream/download', String)

        then:
        response.code() == HttpStatus.OK.code
        response.header(CONTENT_TYPE) == "text/html"
        response.header(CONTENT_DISPOSITION).startsWith("attachment; filename=\"fileTypeHandlerSpec")
        response.header(CONTENT_LENGTH) == null // ideally would be right length
        response.headers.getDate(DATE) < response.headers.getDate(EXPIRES)
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.body() == tempFileContents
    }

    void "test when a system file is returned with a name"() {
        when:
        def response = rxClient.toBlocking().exchange('/test-system/different-name', String)

        then: "the content type is still based on the file extension"
        response.code() == HttpStatus.OK.code
        response.header(CONTENT_TYPE) == "text/html"
        response.header(CONTENT_DISPOSITION) == "attachment; filename=\"abc.xyz\"; filename*=utf-8''abc.xyz"
        response.header(CONTENT_LENGTH) == null // ideally would be right length
        response.headers.getDate(DATE) < response.headers.getDate(EXPIRES)
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.headers.getDate(LAST_MODIFIED) == ZonedDateTime.ofInstant(Instant.ofEpochMilli(tempFile.lastModified()), ZoneId.of("GMT")).truncatedTo(ChronoUnit.SECONDS)
        response.body() == tempFileContents
    }

    void "test the content type is honored when a system file response is returned"() {
        when:
        def response = rxClient.toBlocking().exchange('/test-system/custom-content-type', String)

        then:
        response.code() == HttpStatus.OK.code
        response.header(CONTENT_TYPE) == "text/plain"
        response.header(CONTENT_DISPOSITION) == "attachment; filename=\"temp.html\"; filename*=utf-8''temp.html"
        response.header(CONTENT_LENGTH) == null // ideally would be right length
        response.headers.getDate(DATE) < response.headers.getDate(EXPIRES)
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.headers.getDate(LAST_MODIFIED) == ZonedDateTime.ofInstant(Instant.ofEpochMilli(tempFile.lastModified()), ZoneId.of("GMT")).truncatedTo(ChronoUnit.SECONDS)
        response.body() == tempFileContents
    }

    void "test the content type is honored when a streamed file response is returned"() {
        when:
        def response = rxClient.toBlocking().exchange('/test-stream/custom-content-type', String)

        then:
        response.code() == HttpStatus.OK.code
        response.header(CONTENT_TYPE) == "text/plain"
        response.header(CONTENT_DISPOSITION) == "attachment; filename=\"temp.html\"; filename*=utf-8''temp.html"
        response.header(CONTENT_LENGTH) == null // ideally would be right length
        response.headers.getDate(DATE) < response.headers.getDate(EXPIRES)
        response.header(CACHE_CONTROL) == "private, max-age=60"
        response.body() == tempFileContents
    }

    void "test using a piped stream"() {
        when:
        def response = rxClient.toBlocking().exchange('/test-stream/piped-stream', String)

        then:
        response.code() == HttpStatus.OK.code
        response.header(CONTENT_TYPE) == "text/plain"
        response.header(CONTENT_LENGTH) == null // ideally would be right length
        response.body() == ("a".."z").join('')
    }


    @Controller('/test')
    @Requires(property = 'spec.name', value = 'FileTypeHandlerSpec')
    static class TestController {

        @Get('/html')
        File html() {
            tempFile
        }

        @Get('/not-found')
        File notFound() {
            new File('/xyzabc')
        }

        @Get('/custom-cache-control')
        HttpResponse<File> cacheControl() {
            HttpResponse.ok(tempFile)
                    .header(CACHE_CONTROL, "public, immutable, max-age=31556926")
        }
    }

    @Controller('/test-system')
    @Requires(property = 'spec.name', value = 'FileTypeHandlerSpec')
    static class TestSystemController {

        @Get('/download')
        SystemFile download() {
            new SystemFile(tempFile).attach()
        }

        @Get('/different-name')
        SystemFile differentName() {
            new SystemFile(tempFile).attach("abc.xyz")
        }

        @Get('/custom-content-type')
        HttpResponse<SystemFile> customContentType() {
            HttpResponse.ok(new SystemFile(tempFile, MediaType.TEXT_PLAIN_TYPE).attach("temp.html"))
        }
    }

    @Controller('/test-stream')
    @Requires(property = 'spec.name', value = 'FileTypeHandlerSpec')
    static class TestStreamController {

        @Named("io")
        @Inject
        ExecutorService executorService

        @Get('/download')
        StreamedFile download() {
            StreamedFile file = new StreamedFile(Files.newInputStream(tempFile.toPath()), MediaType.TEXT_HTML_TYPE)
            file.attach("fileTypeHandlerSpec.html")
        }

        @Get('/custom-content-type')
        HttpResponse<StreamedFile> customContentType() {
            StreamedFile file = new StreamedFile(Files.newInputStream(tempFile.toPath()), MediaType.TEXT_PLAIN_TYPE)
            file.attach("temp.html")
            HttpResponse.ok(file).contentType(MediaType.TEXT_PLAIN_TYPE)
        }

        @Get('/piped-stream')
        StreamedFile pipedStream() {
            def output = new PipedOutputStream()
            def input = new PipedInputStream(output)
            executorService.execute({ ->
                ("a".."z").each {
                    output.write(it.bytes)
                }
                output.flush()
                output.close()
            })
            return new StreamedFile(input, MediaType.TEXT_PLAIN_TYPE)
        }

    }
}

