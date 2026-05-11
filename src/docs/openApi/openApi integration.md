# OpenAPI Integration with Spring Boot (Maven)

---

## Why springdoc-openapi?

The two main contenders are **springfox** and **springdoc-openapi**. Here's why springdoc wins:

| | springdoc-openapi | springfox |
|---|---|---|
| **OpenAPI spec** | 3.x (current standard) | 2.x (Swagger, legacy) |
| **Spring Boot 3.x support** | ✅ Full | ❌ Broken |
| **Maintenance** | Actively maintained | Abandoned since 2020 |
| **Spring WebFlux** | ✅ | ❌ |
| **Kotlin support** | ✅ | Partial |
| **Spring Security integration** | ✅ Auto-detected | Manual |
| **Actuator integration** | ✅ | ❌ |

**springfox is effectively dead.** Its last release was 3.0.0 in July 2020 and it does not work with Spring Boot 3.x due to breaking changes in Spring MVC. If you start a project on springfox today, you will hit a wall.

**springdoc-openapi** is the de facto standard for Spring Boot OpenAPI documentation.

---

## Maven Setup

### The Right Plugin Combination

There are two separate concerns, each with its own plugin:

| Plugin | Purpose |
|---|---|
| `springdoc-openapi-maven-plugin` | Generates `openapi.json` / `openapi.yaml` at build time by starting your app |
| `openapi-generator-maven-plugin` | Generates client SDKs, server stubs, or model classes from a spec file |

Most projects need the **first** plugin (spec generation). The **second** is for contract-first development or SDK generation. This guide covers both.

---

## 1. Core Dependency

```xml
<!-- pom.xml -->
<properties>
    <java.version>21</java.version>
    <springdoc.version>2.5.0</springdoc.version>
</properties>

<dependencies>
    <!-- Spring Boot Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- OpenAPI UI + spec generation (springdoc) -->
    <dependency>
        <groupId>org.springdoc</groupId>
        <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        <version>${springdoc.version}</version>
    </dependency>
</dependencies>
```

This single dependency gives you:
- Swagger UI at `/swagger-ui.html`
- OpenAPI JSON at `/v3/api-docs`
- OpenAPI YAML at `/v3/api-docs.yaml`

---

## 2. Build-Time Spec Generation Plugin

The `springdoc-openapi-maven-plugin` boots your application during the `integration-test` phase, hits the `/v3/api-docs` endpoint, and writes the output to a file. This is the most accurate approach since it reflects the actual running app.

```xml
<build>
    <plugins>

        <!-- Required: boots the app during integration-test phase -->
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            <executions>
                <execution>
                    <id>pre-integration-test</id>
                    <goals>
                        <goal>start</goal>
                    </goals>
                </execution>
                <execution>
                    <id>post-integration-test</id>
                    <goals>
                        <goal>stop</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>

        <!-- Generates openapi.json at build time -->
        <plugin>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-maven-plugin</artifactId>
            <version>1.4</version>
            <executions>
                <execution>
                    <id>generate-openapi-spec</id>
                    <goals>
                        <goal>generate</goal>
                    </goals>
                </execution>
            </executions>
            <configuration>
                <!-- URL of the running app's spec endpoint -->
                <apiDocsUrl>http://localhost:8080/v3/api-docs</apiDocsUrl>
                <!-- Output file name -->
                <outputFileName>openapi.json</outputFileName>
                <!-- Output directory -->
                <outputDir>${project.build.directory}</outputDir>
                <!-- Fail the build if the spec can't be generated -->
                <skip>false</skip>
            </configuration>
        </plugin>

    </plugins>
</build>
```

Run it:

```bash
mvn verify
# Outputs: target/openapi.json
```

To get YAML instead, change `apiDocsUrl`:

```xml
<apiDocsUrl>http://localhost:8080/v3/api-docs.yaml</apiDocsUrl>
<outputFileName>openapi.yaml</outputFileName>
```

---

## 3. OpenAPI Generator Plugin (Contract-First / SDK Generation)

If you want to generate Java client SDKs, server stubs, or models from a spec file, use the **OpenAPI Generator** plugin. This is the most capable and widely used code generation tool in the ecosystem.

```xml
<plugin>
    <groupId>org.openapitools</groupId>
    <artifactId>openapi-generator-maven-plugin</artifactId>
    <version>7.6.0</version>
    <executions>
        <execution>
            <goals>
                <goal>generate</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <!-- Path to your spec file (local or URL) -->
        <inputSpec>${project.basedir}/src/main/resources/openapi.yaml</inputSpec>

        <!-- Generator type: spring, java, typescript-fetch, python, etc. -->
        <generatorName>spring</generatorName>

        <!-- Output directory -->
        <output>${project.build.directory}/generated-sources/openapi</output>

        <!-- Base package for generated code -->
        <apiPackage>com.example.api</apiPackage>
        <modelPackage>com.example.model</modelPackage>
        <invokerPackage>com.example.invoker</invokerPackage>

        <configOptions>
            <!-- Generate interfaces instead of concrete controllers -->
            <interfaceOnly>true</interfaceOnly>
            <!-- Use Spring Boot 3 / Jakarta EE -->
            <useSpringBoot3>true</useSpringBoot3>
            <!-- Use java.time types instead of legacy Date -->
            <dateLibrary>java8</dateLibrary>
            <!-- Avoid generating unnecessary files -->
            <skipDefaultInterface>true</skipDefaultInterface>
            <useTags>true</useTags>
        </configOptions>
    </configuration>
</plugin>
```

This generates `@Api` interfaces that your controllers implement, making your controller signatures perfectly aligned with the spec:

```java
// Your controller implements the generated interface
@RestController
public class UserController implements UsersApi {

    @Override
    public ResponseEntity<UserDto> getUserById(Long id) {
        // your implementation
    }
}
```

---

## 4. Recommended `pom.xml` Structure

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.0</version>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>my-api</artifactId>
    <version>1.0.0</version>

    <properties>
        <java.version>21</java.version>
        <springdoc.version>2.5.0</springdoc.version>
        <openapi-generator.version>7.6.0</openapi-generator.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>${springdoc.version}</version>
        </dependency>

        <!-- Validation (for @Valid on request bodies) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>

            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <id>pre-integration-test</id>
                        <goals><goal>start</goal></goals>
                    </execution>
                    <execution>
                        <id>post-integration-test</id>
                        <goals><goal>stop</goal></goals>
                    </execution>
                </executions>
            </plugin>

            <plugin>
                <groupId>org.springdoc</groupId>
                <artifactId>springdoc-openapi-maven-plugin</artifactId>
                <version>1.4</version>
                <executions>
                    <execution>
                        <goals><goal>generate</goal></goals>
                    </execution>
                </executions>
                <configuration>
                    <apiDocsUrl>http://localhost:8080/v3/api-docs</apiDocsUrl>
                    <outputFileName>openapi.json</outputFileName>
                    <outputDir>${project.build.directory}</outputDir>
                </configuration>
            </plugin>

        </plugins>
    </build>

</project>
```

---

## 5. application.yml Configuration

```yaml
springdoc:
  api-docs:
    path: /v3/api-docs
    enabled: true
  swagger-ui:
    path: /swagger-ui.html
    enabled: true
    try-it-out-enabled: true
    operations-sorter: alpha
    tags-sorter: alpha
    filter: true                 # Enables search box in UI
  show-actuator: false           # Hide Spring Actuator endpoints from docs
```

For production (`application-prod.yml`):

```yaml
springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
```

---

## 6. Minimal Annotated Controller

```java
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "User management endpoints")
public class UserController {

    @Operation(summary = "List all users")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK"),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<List<UserDto>> getUsers() {
        return ResponseEntity.ok(List.of());
    }

    @Operation(summary = "Get user by ID")
    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUser(
            @Parameter(description = "User ID", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(new UserDto());
    }

    @Operation(summary = "Create user")
    @PostMapping
    public ResponseEntity<UserDto> createUser(@RequestBody @Valid CreateUserRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new UserDto());
    }
}
```

---

## 7. Global OpenAPI Bean

```java
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("My API")
                .version("1.0.0")
                .description("API documentation")
                .contact(new Contact()
                    .name("Team Name")
                    .email("team@example.com")))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .components(new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .name("bearerAuth")
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")));
    }
}
```

---

## Plugin Decision Guide

```
Are you starting from code and want to document it?
  └─► springdoc-openapi-maven-plugin   → generates spec from your running app

Are you starting from a spec file and want to generate code?
  └─► openapi-generator-maven-plugin   → generates controllers, models, clients

Do you need both?
  └─► Use both plugins. Generate spec with springdoc, version it, then use
      openapi-generator in downstream projects (e.g., frontend, other services)
      to consume it.
```

---

## Version Reference

| Spring Boot | springdoc artifact | springdoc version |
|---|---|---|
| 3.x | `springdoc-openapi-starter-webmvc-ui` | 2.x |
| 2.7.x | `springdoc-openapi-ui` | 1.7.x |

---

*Targets Spring Boot 3.3.x · Java 21 · springdoc-openapi 2.5.0 · openapi-generator 7.6.0*