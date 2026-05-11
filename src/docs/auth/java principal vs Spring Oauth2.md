
### `Principal principal` vs `@AuthenticationPrincipal Jwt jwt`

Both give you access to the currently authenticated user, but at very different levels of abstraction.

---

#### `Principal principal` — the generic Java interface

```java
public ResponseEntity<?> sendMessage(..., Principal principal) {
    String email = principal.getName(); // returns the JWT "sub" claim
}
```

- `Principal` is a plain **Java SE interface** (`java.security.Principal`).
- Spring resolves it by calling `SecurityContextHolder.getContext().getAuthentication()` and returning the `Authentication` object itself (which also implements `Principal`).
- `.getName()` returns whatever was set as the **`subject` (`sub`) claim** of the JWT — in your case, the user's **email**.
- You get nothing else from it without casting to a Spring-specific type.
- To get `userId` or `nickname`, you had no choice but to do a **database lookup by email**, which is exactly the redundancy we eliminated.

---

#### `@AuthenticationPrincipal Jwt jwt` — the type-safe Spring OAuth2 object

```java
public ResponseEntity<?> sendMessage(..., @AuthenticationPrincipal Jwt jwt) {
    Long userId   = jwt.getClaim("userId");
    String nickname = jwt.getClaim("nickname");
}
```

- `@AuthenticationPrincipal` is a Spring Security annotation that tells Spring to inject the **actual principal object** stored inside the `Authentication`.
- When you configure `.oauth2ResourceServer(oauth2 -> oauth2.jwt(...))` (as your `SecurityConfig` does), Spring parses the Bearer token and stores a `org.springframework.security.oauth2.jwt.Jwt` object as the principal.
- The `Jwt` object gives you **direct, typed access to every JWT claim** — no DB round-trip needed.
- `jwt.getSubject()` → email (same as `principal.getName()`)
- `jwt.getClaim("userId")` → `Long` — pulled directly from the token payload
- `jwt.getClaim("nickname")` → `String` — pulled directly from the token payload

---

#### Side-by-side comparison

| | `Principal principal` | `@AuthenticationPrincipal Jwt jwt` |
|---|---|---|
| **Type** | `java.security.Principal` (generic) | `org.springframework.security.oauth2.jwt.Jwt` (specific) |
| **What you get** | Only `.getName()` → the `sub` claim | All JWT claims via `.getClaim(key)` |
| **To get userId/nickname** | Must query the database | Read directly from token — **zero DB hit** |
| **Type safety** | None — just a `String` | Claim map with typed getters |
| **Works without JWT?** | Yes — works with any auth mechanism | No — requires JWT/OAuth2 resource server setup |
| **Requires Spring Security import?** | No — standard Java | Yes — `spring-security-oauth2-resource-server` |

---

#### Why the change matters for your app

Your `TokenService` now embeds `userId` and `nickname` directly in the JWT payload at login time:

```java
JwtClaimsSet claims = JwtClaimsSet.builder()
    .subject(user.getUsername())       // email → for Principal.getName()
    .claim("nickname", user.getNickname())
    .claim("userId", user.getId())
    .claim("scope", scope)
    .build();
```

So every authenticated request already **carries** the user's identity in the token. With `@AuthenticationPrincipal Jwt jwt`, you read it straight out of the token — the database is never consulted just to identify the caller. With the old `Principal` approach, `principal.getName()` gave you only the email, forcing a `SELECT * FROM app_user WHERE email = ?` on every single message send.
