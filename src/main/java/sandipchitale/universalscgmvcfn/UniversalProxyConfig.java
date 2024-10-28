package sandipchitale.universalscgmvcfn;

import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

import java.net.URI;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
public class UniversalProxyConfig {
    private static final String X_METHOD = "X-METHOD";

    private final Set<HttpMethod> httpMethods = Set.of(HttpMethod.GET,
            HttpMethod.HEAD,
            HttpMethod.POST,
            HttpMethod.PUT,
            HttpMethod.PATCH,
            HttpMethod.DELETE,
            HttpMethod.OPTIONS);

    @Bean
    RouterFunction<ServerResponse> universalProxy() {
        return route("universal")
                .route(path("/**"), http())
                .before((ServerRequest serverRequest) -> {
                    String method = serverRequest.headers().firstHeader(X_METHOD);
                    if (method == null) {
                        method = serverRequest.method().name();
                    }
                    HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase());
                    if (!httpMethods.contains(httpMethod)) {
                        throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Invalid method value: " + method + " in " + X_METHOD + " header.");
                    }
                    String uriString = serverRequest.path().substring(1);
                    uriString = uriString.replaceAll(Pattern.quote("%7Bmethod%7D"), method.toLowerCase());
                    uriString = uriString.replaceAll(Pattern.quote("%7BMETHOD%7D"), method.toUpperCase());
                    URI uri = URI.create(uriString);
                    ServerRequest request = ServerRequest
                            .from(serverRequest)
                            .method(httpMethod)
                            .uri(uri)
                            .build();
                    MvcUtils.setRequestUrl(request, uri);
                    return request;
                })
                .before((ServerRequest serverRequest) -> {
                    return ServerRequest
                            .from(serverRequest).headers((HttpHeaders httpHeaders) -> {
                                httpHeaders.remove(X_METHOD);
                                httpHeaders.remove(PerRequestTimeoutRestClientProxyExchange.X_CONNECT_TIMEOUT_MILLIS);
                                httpHeaders.remove(PerRequestTimeoutRestClientProxyExchange.X_READ_TIMEOUT_MILLIS);
                            })
                            .build();
                })
                .build();
    }
}
