# Spring Security: Login, JWT, and RSA

This document explains how the current Spring Security implementation
works, from the moment a user logs in until they send a JWT with a later
API request.

## 1. The two jobs of security

Your security has two main jobs:

1.  **Authentication:** Prove that the user is who they claim to be.
2.  **Authorization:** Decide what that authenticated user is allowed to
    do.

Your implementation uses Spring Security, BCrypt, JWT, and RSA keys.

------------------------------------------------------------------------

# Part A: Login

## 2. The login endpoint

``` java
@Override
public ResponseEntity<AuthenticationResponse> loginUser(
        AuthenticationRequest authenticationRequest) {

    AuthenticationResponse response =
            authenticationService.login(authenticationRequest);

    return ResponseEntity.ok(response);
}
```

This is your controller.

The frontend sends the user's email and password. The controller
receives the request and delegates the actual authentication work to:

``` java
authenticationService.login(authenticationRequest);
```

The controller itself does not check the password. The authentication
service asks Spring Security to do that.

## 3. AuthenticationManager: checking credentials

``` java
Authentication authentication =
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                request.getEmail(),
                request.getPassword()
            )
        );
```

First, this creates an authentication request containing:

-   **Principal:** the email, meaning who is trying to log in.
-   **Credentials:** the raw password supplied by the user.

At this point, the user is not authenticated. This object is only a
request to authenticate.

Then:

``` java
authenticationManager.authenticate(...);
```

asks Spring Security to verify those credentials.

Your `SecurityConfig` creates the manager:

``` java
@Bean
public AuthenticationManager authManager(
        UserDetailsService userDetailsService) {

    var authProvider =
        new DaoAuthenticationProvider(userDetailsService);

    authProvider.setPasswordEncoder(passwordEncoder());

    return new ProviderManager(authProvider);
}
```

The components have these responsibilities:

  -----------------------------------------------------------------------
Component                           Responsibility
  ----------------------------------- -----------------------------------
`AuthenticationManager`             Coordinates authentication

`ProviderManager`                   Delegates the request to an
authentication provider

`DaoAuthenticationProvider`         Performs username/password
authentication

`UserDetailsService`                Retrieves the user from the
database

`PasswordEncoder`                   Checks the supplied password
against the stored password hash
  -----------------------------------------------------------------------

## 4. UserDetailsService: finding the user

Your implementation:

``` java
@Bean
public UserDetailsService userDetailsService() {
    return email -> userRepository.findByEmail(email)
            .orElseThrow(() ->
                new UsernameNotFoundException("User Not Found"));
}
```

During authentication, Spring calls this service with the email from the
authentication request.

It searches PostgreSQL:

``` java
userRepository.findByEmail(email);
```

If the user exists, Spring receives your `User` object. If not,
authentication fails with a `UsernameNotFoundException`.

Your `User` class needs to implement Spring Security's `UserDetails`
interface for this setup.

## 5. BCrypt: checking the password

You configured:

``` java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

And connected it to the provider:

``` java
authProvider.setPasswordEncoder(passwordEncoder());
```

Spring now has:

-   The raw password supplied by the frontend.
-   The user's stored BCrypt password hash from the database.

BCrypt checks whether the raw password matches the stored hash. It does
not decrypt the stored password. BCrypt is a one-way password hashing
function.

If they match, authentication succeeds. If they do not, Spring throws an
authentication exception.

## 6. Getting the authenticated user

After successful authentication:

``` java
User user = (User) authentication.getPrincipal();
```

Before authentication, the principal in the request was just the email.

After successful authentication, the returned `Authentication` contains
the authenticated user's principal. In your implementation, that
principal is your `User` object.

You can now access:

``` java
user.getId();
user.getNickname();
user.getAuthorities();
```

The user has passed the login check.

------------------------------------------------------------------------

# Part B: Creating the access token

## 7. Generating the JWT

Your login service calls:

``` java
String accessToken = tokenService.generateToken(user);
```

Your token-generation method:

``` java
public String generateToken(User user) {
    Instant now = Instant.now();

    String scope = user.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.joining(" "));

    JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer("self")
            .issuedAt(now)
            .expiresAt(now.plus(10, ChronoUnit.HOURS))
            .subject(user.getUsername())
            .claim("nickname", user.getNickname())
            .claim("userId", user.getId())
            .claim("scope", scope)
            .build();

    return this.encoder.encode(
            JwtEncoderParameters.from(claims)
    ).getTokenValue();
}
```

A JWT is a token containing claims, which are pieces of information
about the token and its subject.

Your token contains:

Claim        Meaning
  ------------ -------------------------------------------------
`iss`        Issuer, who created the token
`iat`        Issued-at time
`exp`        Expiration time
`sub`        Subject, in your case the user's username/email
`nickname`   User's nickname
`userId`     User's database ID
`scope`      User's authorities

Your access token expires after **10 hours**.

### What is the scope?

``` java
String scope = user.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .collect(Collectors.joining(" "));
```

This takes the user's authorities and joins them into one
space-separated string.

For example, if the user has:

``` text
ROLE_USER
ROLE_MODERATOR
```

the scope becomes:

``` text
ROLE_USER ROLE_MODERATOR
```

These authorities can later be used when Spring checks access to
protected operations.

### What does the encoder do?

``` java
this.encoder.encode(
    JwtEncoderParameters.from(claims)
).getTokenValue();
```

The encoder creates the JWT and signs it. Your application uses RSA keys
for that signature.

------------------------------------------------------------------------

# Part C: RSA keys and JWT signing

## 8. Public and private keys

Your application has an RSA key pair:

-   **Private key:** used to sign JWTs when your application creates
    them.
-   **Public key:** used to verify JWT signatures later.

The private key must remain secret. The public key can be shared with
systems that need to verify tokens.

The signature allows the server to detect whether the token has been
changed. It does not encrypt the JWT's claims.

## 9. Generating the RSA key pair

Your `KeyGeneratorUtils` does this:

``` java
KeyPairGenerator keyPairGenerator =
        KeyPairGenerator.getInstance("RSA");

keyPairGenerator.initialize(2048);

KeyPair keyPair = keyPairGenerator.generateKeyPair();
```

This generates an RSA key pair with a 2048-bit key size.

Then you extract the keys:

``` java
RSAPublicKey publicKey =
        (RSAPublicKey) keyPair.getPublic();

RSAPrivateKey privateKey =
        (RSAPrivateKey) keyPair.getPrivate();
```

## 10. How Jwks.java loads or generates keys

Your `Jwks.load()` method first checks whether the public and private
key files exist.

``` java
if (Files.exists(publicKeyFile)
        && Files.exists(privateKeyFile)) {
    return loadFromFile(keyDir);
}
```

If they exist, it loads them.

If they do not exist:

-   If `generateIfMissing` is `false`, the application fails to start.
-   If it is `true`, new keys are generated and saved.

Your files are:

``` text
public.key
private.key
key-id.txt
```

The public and private keys are stored as Base64-encoded DER data.

### Why this matters for Kubernetes

Imagine you have three backend pods:

-   Pod A
-   Pod B
-   Pod C

Pod A signs a JWT using its private key.

If Pod B has a different public key, it cannot verify that JWT.

Therefore, all pods need the same signing key material. Your code
expects the key files to come from a shared mounted Kubernetes Secret.

The `key-id.txt` file stores the key ID (`kid`). The ID identifies which
key was used to sign a token and is useful when managing key rotation.

## 11. How the encoder gets the RSA key

In `SecurityConfig`:

``` java
@Bean
public RSAKey rsaKey() {
    RSAKey rsaKey = Jwks.load(
            Path.of(keyDir),
            generateKeyIfMissing
    );

    return rsaKey;
}
```

This loads the RSA key as a Spring bean.

Then:

``` java
@Bean
public JWKSource<SecurityContext> jwkSource(RSAKey rsaKey) {
    JWKSet jwkSet = new JWKSet(rsaKey);

    return (jwkSelector, securityContext) ->
            jwkSelector.select(jwkSet);
}
```

A JWK is a standardized representation of a cryptographic key. A JWK set
is a collection of such keys.

Finally:

``` java
@Bean
public JwtEncoder jwtEncoder(
        JWKSource<SecurityContext> jwkSource) {

    return new NimbusJwtEncoder(jwkSource);
}
```

Your `JwtEncoder` uses Nimbus to create and sign JWTs using the
available RSA private key.

The chain is:

``` text
RSA key files
     |
     v
Jwks.load()
     |
     v
RSAKey
     |
     v
JWKSource
     |
     v
NimbusJwtEncoder
     |
     v
Signed JWT
```

------------------------------------------------------------------------

# Part D: Refresh token and login response

## 12. Refresh token

Your login service also calls:

``` java
String refreshToken = createRefreshToken(user);
```

The access token and refresh token have different purposes:

-   **Access token:** sent with API requests to authenticate the user.
-   **Refresh token:** presented to obtain a new access token when the
    current access token expires.

The exact refresh-token behavior cannot be determined from the login
code alone. We need to inspect `createRefreshToken()` and the refresh
endpoint to understand how it is stored, validated, rotated, and
revoked.

## 13. Returning the login response

Finally:

``` java
return AuthenticationResponse.builder()
        .id(user.getId())
        .nickname(user.getNickname())
        .token(accessToken)
        .refreshToken(refreshToken)
        .role(user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList())
        .build();
```

The frontend receives the user ID, nickname, access token, refresh
token, and roles/authorities.

For later protected requests, it sends the access token in the HTTP
Authorization header:

``` http
Authorization: Bearer eyJhbGciOiJSUzI1NiIs...
```

------------------------------------------------------------------------

# Part E: Authenticating later API requests

## 14. What happens when the frontend sends the JWT back?

Login is finished. Now the user wants to access a protected endpoint.

For example:

``` http
GET /api/chatrooms
Authorization: Bearer eyJhbGciOiJSUzI1NiIs...
```

Your `SecurityFilterChain` and Spring's resource-server support handle
this request.

You configured:

``` java
.oauth2ResourceServer(oauth2 ->
        oauth2.jwt(Customizer.withDefaults()))
```

This tells Spring Security to use JWT bearer-token authentication.

The general flow is:

``` text
Frontend sends HTTP request
          |
          v
BearerTokenAuthenticationFilter
          |
          | Extracts JWT from Authorization header
          v
JwtAuthenticationProvider
          |
          v
JwtDecoder
          |
          | Decodes and verifies token
          v
Authenticated JwtAuthenticationToken
          |
          v
SecurityContextHolder
          |
          v
Authorization checks
          |
          v
Controller
```

### BearerTokenAuthenticationFilter

Extracts the token from the `Authorization: Bearer ...` header and
passes it into Spring Security's authentication process.

### JwtAuthenticationProvider

Handles authentication using the JWT.

### JwtDecoder

Decodes the JWT and verifies its signature and applicable claims.

### SecurityContextHolder

After successful authentication, Spring stores the resulting
`Authentication` in the `SecurityContextHolder` for the current request.

The controller and other security components can then access the
authenticated principal.

## 15. JwtDecoder: verifying the JWT

Your configuration:

``` java
@Bean
public JwtDecoder jwtDecoder(
        JWKSource<SecurityContext> jwkSource) {

    DefaultJWTProcessor<SecurityContext> jwtProcessor =
            new DefaultJWTProcessor<>();

    jwtProcessor.setJWSKeySelector(
            new JWSVerificationKeySelector<>(
                    JWSAlgorithm.RS256,
                    jwkSource
            )
    );

    jwtProcessor.setJWTClaimsSetVerifier((claims, context) -> {
    });

    return new NimbusJwtDecoder(jwtProcessor);
}
```

You configured `RS256`, which means RSA with SHA-256.

When a JWT arrives:

1.  Nimbus reads the token's signing algorithm and key ID.
2.  The key selector finds the appropriate verification key from your
    JWK source.
3.  The decoder verifies the signature using the public key.
4.  Spring applies its JWT validators, including timestamp validation
    such as expiration.

If somebody changes the token's claims, the signature no longer matches
and verification fails.

**Important detail about your current code:** the empty
`JWTClaimsSetVerifier` does not validate the issuer. Spring's default
timestamp validation does not automatically mean that the issuer is
checked. If you want to require `iss = "self"`, configure an issuer
validator explicitly.

Also remember: JWTs are signed, not encrypted. Their claims can be
decoded by anyone who possesses the token. Never put passwords or
secrets inside them.

------------------------------------------------------------------------

# Part F: SecurityFilterChain and endpoint access

## 16. Public and protected endpoints

Your configuration:

``` java
.authorizeHttpRequests(authorize -> authorize
    .requestMatchers(
        "/api/auth/login",
        "/api/auth/reset-password",
        "/api/auth/redeem-password",
        "/actuator/health",
        "/favicon.ico",
        "/ws",
        "/ws/**",
        "/api/auth/refresh-token",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/api"
    ).permitAll()
    .anyRequest().authenticated()
)
```

You have two broad categories of endpoints.

### Public endpoints

``` java
.permitAll()
```

These endpoints do not require the caller to be authenticated.

### Protected endpoints

``` java
.anyRequest().authenticated()
```

All other requests require authentication.

For example:

Endpoint                    Access rule
  --------------------------- ---------------
`/api/auth/login`           Public
`/api/auth/refresh-token`   Public
`/actuator/health`          Public
`/api/chatrooms`            Authenticated
`/api/messages`             Authenticated

`authenticated()` checks whether the user is authenticated. It does not
check whether the user is an ADMIN, MODERATOR, or USER.

Role-based authorization is a separate step, often implemented with
`@PreAuthorize`.

## 17. Why STATELESS?

``` java
.sessionManagement(session ->
    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
)
```

Traditional session-based authentication works roughly like this:

1.  User logs in.
2.  Server creates a session.
3.  Server stores session information.
4.  Browser sends a session cookie with later requests.

Your JWT architecture works differently:

1.  User logs in.
2.  Server generates a JWT.
3.  Frontend stores the token.
4.  Frontend sends the token with protected requests.
5.  Server verifies the token.

Your backend does not need to maintain an HTTP login session.

This is useful with multiple Kubernetes pods because any pod with the
correct shared signing key can verify the token.

Stateless does not mean your application has no database or Redis state.
It means Spring Security does not use an HTTP session to maintain the
user's authentication between requests.

------------------------------------------------------------------------

# Part G: Other SecurityConfig settings

## 18. What do the other configurations do?

  -----------------------------------------------------------------------
Configuration                       Purpose
  ----------------------------------- -----------------------------------
`@EnableWebSecurity`                Enables Spring Web Security
configuration

`@EnableMethodSecurity`             Enables method annotations such as
`@PreAuthorize`

`csrf.disable()`                    Disables CSRF protection

`formLogin.disable()`               Disables Spring's default HTML form
login

`httpBasic.disable()`               Disables HTTP Basic authentication

`cors(...)`                         Configures allowed browser origins,
methods, and headers
  -----------------------------------------------------------------------

### CORS

Your configuration allows specific origins, HTTP methods, and headers.

CORS is a browser access policy. It is not authentication and does not
replace JWT security.

### CSRF

Disabling CSRF is common in stateless APIs that authenticate using
bearer tokens sent in the Authorization header. However, the correct
choice depends on how tokens are stored and sent. If authentication
credentials are automatically attached by the browser, such as cookies,
CSRF protection needs separate consideration.

------------------------------------------------------------------------

# Part H: The complete security flow

## 19. Login

``` text
1. Frontend sends email and password
             |
             v
2. AuthenticationManager receives credentials
             |
             v
3. DaoAuthenticationProvider handles authentication
             |
             v
4. UserDetailsService retrieves the user from PostgreSQL
             |
             v
5. BCrypt verifies the password
             |
             v
6. Authentication succeeds and returns the User principal
             |
             v
7. JwtEncoder creates and signs the access token
             |
             v
8. Refresh token is created
             |
             v
9. Frontend receives both tokens
```

## 20. Subsequent API request

``` text
1. Frontend sends JWT in Authorization header
             |
             v
2. BearerTokenAuthenticationFilter extracts it
             |
             v
3. JwtDecoder verifies signature and applicable claims
             |
             v
4. Spring creates an authenticated JwtAuthenticationToken
             |
             v
5. Spring stores it in SecurityContextHolder
             |
             v
6. Authorization rules decide whether the request can continue
             |
             v
7. Controller handles the request
```

------------------------------------------------------------------------
