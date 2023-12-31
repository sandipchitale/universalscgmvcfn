# Description

A universal streaming proxy using Spring Cloud Gateway MVC.fn.

## How to try this

- Set the header X-METHOD to the method to proxy (GET, POST, PUT, DELETE, etc)
    - If the header is not set, method of `HttpServletRequest` is used.
- Send the request with proxy url and arguments as path and query params and post body
    - The proxy request url may contain a pattern `{method}` that will be replaced by the lowercase value of the final HttpMethod.
    - The proxy request url may contain a pattern `{METHOD}` that will be replaced by the uppercase value of the final HttpMethod.

## Optional features

- `X-CONNECT-TIMEOUT-MILLIS` header - set the connect timeout in milliseconds. A value of 0 means infinite connect timeout. On connect timeout, a `Gateway Timeout: I/O error on GET request for "proxyuri": Connect timed out` response is returned.
- `X-READ-TIMEOUT-MILLIS` header - set the read timeout in milliseconds. A value of 0 means infinite read timeout. On connect timeout, a `Gateway Timeout: I/O error on GET request for "proxyuri": Read timed out` response is returned.

## Examples

### Request

- `X-METHOD=GET`
- `http://localhost:8080/https://jsonplaceholder.typicode.com/todos/1`

### Response

```json
{
  "userId": 1,
  "id": 1,
  "title": "delectus aut autem",
  "completed": false
}
```

### Request

- `X-METHOD=POST`
- `http://localhost:8080/https://postman-echo.com/{method}?onekey=onevalue&sort=fn,desc&sort=ln,asc`
- Raw body

```text
Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
```

### Response

```json
{
  "args": {
    "onekey": "onevalue",
    "sort": [
      "fn,desc",
      "ln,asc"
    ]
  },
  "data": "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.",
  "files": {},
  "form": {},
  "headers": {
    "x-forwarded-proto": "https",
    "x-forwarded-port": "443",
    "host": "postman-echo.com",
    "x-amzn-trace-id": "Root=1-657574ac-3c37ba827decba786f8f4231",
    "content-length": "123",
    "authorization": "Basic bWFtYTptaWE=",
    "content-type": "text/plain",
    "user-agent": "PostmanRuntime/7.34.0",
    "accept": "*/*",
    "cache-control": "no-cache",
    "postman-token": "87b294ea-451f-4b90-9fdc-df729f4ead1b",
    "accept-encoding": "gzip, deflate, br",
    "cookie": "sails.sid=s%3APYDfY3YLLCcPgTa6IC9yl7UEqGTrE0SY.WY82el8WJEea0fo92drVvFvYOpTdEQ90B8R%2FOZHA8dM"
  },
  "json": null,
  "url": "https://postman-echo.com/POST?onekey=onevalue&sort=fn,desc&sort=ln,asc"
}
```

### Reference Documentation
For further reference, please consider the following sections:

* [Official Gradle documentation](https://docs.gradle.org)
* [Spring Boot Gradle Plugin Reference Guide](https://docs.spring.io/spring-boot/docs/3.2.0/gradle-plugin/reference/html/)
* [Create an OCI image](https://docs.spring.io/spring-boot/docs/3.2.0/gradle-plugin/reference/html/#build-image)
* [Spring Web](https://docs.spring.io/spring-boot/docs/3.2.0/reference/htmlsingle/index.html#web)
* [Gateway](https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/)
* [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/3.2.0/reference/htmlsingle/index.html#actuator)

### Guides
The following guides illustrate how to use some features concretely:

* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)
* [Building a RESTful Web Service with Spring Boot Actuator](https://spring.io/guides/gs/actuator-service/)

### Additional Links
These additional references should also help you:

* [Gradle Build Scans – insights for your project's build](https://scans.gradle.com#gradle)

