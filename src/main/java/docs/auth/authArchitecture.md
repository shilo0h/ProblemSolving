### Authentication & Authorization Architecture — Comprehensive Analysis

**Project:** `chat-backend` (Spring Boot)  
**Date:** 2026-04-17

---

### Component Overview (Mermaid Diagram)

```mermaid
graph TB
    subgraph Client
        FE[Frontend / Mobile Client]
    end

    subgraph Controllers
        AC[AuthController<br>/api/auth/*]
        UC[UserController<br>/api/user/*]
        CC[ChatController<br>/api/chatrooms/*/messages]
        CRC[ChatroomController<br>/api/chatrooms/*]
    end

    subgraph Security Layer
        SC[SecurityConfig<br>SecurityFilterChain + @EnableMethodSecurity]
        JWT_FILTER[OAuth2 Resource Server<br>JWT Filter]
        CORS[CORS Config]
        ENTRY[BearerTokenAuthenticationEntryPoint]
        DENIED[BearerTokenAccessDeniedHandler]
    end

    subgraph Auth Services
        AS[AuthenticationService]
        TS[TokenService]
        AM[AuthenticationManager<br>DaoAuthenticationProvider]
        UDS[UserDetailsService]
        PE[PasswordEncoder<br>BCrypt]
        ES[EmailService]
    end

    subgraph Key Management
        JWKS[Jwks]
        RSA[RSAKey<br>generated at runtime]
        ENC[NimbusJwtEncoder]
        DEC[NimbusJwtDecoder]
    end

    subgraph Data Layer
        UR[(UserRepository)]
        RTR[(RefreshTokenRepository)]
        RT_ENTITY[RefreshToken Entity<br>token, user, expiryDate, revoked]
        USER_ENTITY[User Entity<br>email, password, role, resetToken*]
        ROLE_ENUM[Role Enum<br>ADMIN / MODERATOR / USER]
    end

    subgraph WebSocket
        WSC[WebSocketConfig]
        ACIA[AuthChannelInterceptorAdapter<br>JWT auth on CONNECT + membership on SUBSCRIBE]
    end

    FE -->|HTTP| SC
    SC --> JWT_FILTER
    SC --> CORS
    SC --> ENTRY
    SC --> DENIED
    JWT_FILTER -->|permitAll| AC
    JWT_FILTER -->|authenticated| UC
    JWT_FILTER -->|authenticated| CC
    JWT_FILTER -->|authenticated| CRC

    AC --> AS
    AS --> TS
    AS --> AM
    AS --> RTR
    AS --> PE
    AS --> ES
    AM --> UDS
    UDS --> UR

    TS --> ENC
    ENC --> RSA
    DEC --> RSA
    JWKS --> RSA

    RTR --> RT_ENTITY
    UR --> USER_ENTITY
    USER_ENTITY --> ROLE_ENUM

    FE -->|WebSocket /ws| WSC
    WSC --> ACIA
    ACIA --> DEC
```

---

### API Call Flows (Major Actions in Execution Order)

#### Registration Flow

```mermaid
sequenceDiagram
    participant C as Client
    participant AC as AuthController
    participant AS as AuthenticationService
    participant UR as UserRepository
    participant PE as PasswordEncoder
    participant TS as TokenService
    participant RTR as RefreshTokenRepository

    C->>AC: POST /api/auth/register {nickname, name, lastName, email, password}
    AC->>AS: register(RegisterRequest)
    AS->>UR: existsByEmail(email)
    alt Email exists
        AS-->>C: 400 BadRequest "Email already in use"
    end
    AS->>PE: encode(password)
    AS->>UR: save(User with Role.USER)
    AS->>TS: generateToken(user) → signs JWT with RSA private key
    AS->>RTR: save(new RefreshToken with UUID, 30-day expiry)
    AS-->>C: {id, nickname, accessToken, refreshToken, role}
```

#### Login Flow

```mermaid
sequenceDiagram
    participant C as Client
    participant AC as AuthController
    participant AS as AuthenticationService
    participant AM as AuthenticationManager
    participant UDS as UserDetailsService
    participant PE as PasswordEncoder
    participant TS as TokenService
    participant RTR as RefreshTokenRepository

    C->>AC: POST /api/auth/login {email, password}
    AC->>AS: login(AuthenticationRequest)
    AS->>AM: authenticate(UsernamePasswordAuthenticationToken)
    AM->>UDS: loadUserByUsername(email)
    AM->>PE: matches(rawPassword, encodedPassword)
    AM-->>AS: Authentication (with User as principal)
    AS->>TS: generateToken(authentication) → JWT [sub=email, userId, nickname, scope=ROLE_X, exp=10h]
    AS->>RTR: save(new RefreshToken UUID, 30-day expiry)
    AS-->>C: {id, nickname, accessToken, refreshToken, role}
```

#### Authenticated API Call Flow (e.g., ChatController)

```mermaid
sequenceDiagram
    participant C as Client
    participant SF as SecurityFilterChain
    participant JD as JwtDecoder (RSA Public Key)
    participant CC as ChatController
    participant CS as ChatService

    C->>SF: POST /api/chatrooms/{id}/messages<br>Authorization: Bearer <accessToken>
    SF->>JD: decode & verify JWT signature + expiry
    alt Invalid/Expired JWT
        SF-->>C: 401 Unauthorized (BearerTokenAuthenticationEntryPoint)
    end
    JD-->>SF: Jwt principal (sub, userId, nickname, scope)
    SF->>CC: request with @AuthenticationPrincipal Jwt
    CC->>CC: extract userId = jwt.getClaim("userId")
    CC->>CS: sendMessage(message, userId, nickname)
    CC-->>C: 200 OK
```

#### Token Refresh Flow (Rotation)

```mermaid
sequenceDiagram
    participant C as Client
    participant AC as AuthController
    participant AS as AuthenticationService
    participant RTR as RefreshTokenRepository
    participant TS as TokenService

    C->>AC: POST /api/auth/refresh-token {refreshToken}
    AC->>AS: refreshToken(refreshTokenValue)
    AS->>RTR: findByTokenAndRevokedFalse(value)
    alt Not found or revoked
        AS-->>C: 400 "Invalid or revoked refresh token"
    end
    AS->>AS: check expiryDate > now
    alt Expired
        AS->>RTR: set revoked=true, save
        AS-->>C: 400 "Refresh token has expired"
    end
    AS->>RTR: revoke old token (revoked=true)
    AS->>TS: generateToken(user) → new access JWT
    AS->>RTR: save(new RefreshToken) → rotation
    AS-->>C: {id, nickname, newAccessToken, newRefreshToken, role}
```

#### Password Reset Flow

```mermaid
sequenceDiagram
    participant C as Client
    participant AC as AuthController
    participant AS as AuthenticationService
    participant UR as UserRepository
    participant PE as PasswordEncoder
    participant ES as EmailService
    participant RTR as RefreshTokenRepository
    participant TS as TokenService

    Note over C,ES: Step 1 — Request Reset
    C->>AC: POST /api/auth/reset-password {email}
    AC->>AS: resetPassword(email)
    AS->>UR: findByEmail(email)
    AS->>PE: encode(rawResetToken)
    AS->>UR: save(user with resetTokenHash, resetTokenExpiry=15min)
    AS->>ES: sendHtmlEmail(email, reset code)
    AS-->>C: 200 OK (void)

    Note over C,TS: Step 2 — Redeem New Password
    C->>AC: POST /api/auth/redeem-password {email, token, newPassword}
    AC->>AS: redeemPassword(email, token, password)
    AS->>UR: findByEmail(email)
    AS->>AS: validate: resetTokenHash exists, not expired
    AS->>PE: matches(token, resetTokenHash) → verify code
    AS->>PE: encode(newPassword)
    AS->>UR: save(user with new password, clear resetToken fields)
    AS->>RTR: revokeAllByUserId(userId) → invalidate ALL sessions
    AS->>TS: generateToken(user) → fresh access JWT
    AS->>RTR: save(new RefreshToken)
    AS-->>C: {id, nickname, accessToken, refreshToken, role}
```

---

### How the Client Makes API Calls

The client authentication lifecycle works as follows:

1. **Initial Authentication:** Client calls `POST /api/auth/login` or `POST /api/auth/register` with credentials. The server returns an `accessToken` (JWT, 10-hour TTL) and a `refreshToken` (opaque UUID, 30-day TTL).

2. **Storing Tokens:** The client stores both tokens (typically in memory or secure storage — never in localStorage for the access token if possible).

3. **Making Authenticated Requests:** Every subsequent API call includes the header:
   ```
   Authorization: Bearer <accessToken>
   ```
   Spring's `oauth2ResourceServer` JWT filter intercepts the request, decodes and verifies the JWT signature using the RSA public key, checks expiry, and populates the `SecurityContext` with the authenticated principal.

4. **Token Renewal:** When the access token expires (or is close to expiring), the client calls `POST /api/auth/refresh-token` with the stored refresh token. The server performs **refresh token rotation** — it revokes the old refresh token and issues both a new access token and a new refresh token.

5. **WebSocket Connections:** The client connects to `/ws` (SockJS + STOMP) and passes the JWT in the `Authorization` STOMP header. The `AuthChannelInterceptorAdapter.preSend()` is **fully active**:
    - On `CONNECT`: validates the Bearer JWT using `JwtDecoder` and sets the authenticated user on the STOMP session.
    - On `SUBSCRIBE` to `/topic/chatroom/{id}/**`: verifies the user is a member of that chatroom via `chatroomService.isUserMemberOfChatroom()`. Non-members receive a `MessageDeliveryException`.
    - The `/ws/**` path remains `permitAll` at the HTTP filter level (required for the WebSocket handshake), but STOMP-level auth is enforced by the interceptor.

---

### Role-Based Authorization on Controllers

#### `SecurityConfig` — `@EnableMethodSecurity` is active

`SecurityConfig` is annotated with both `@EnableWebSecurity` and `@EnableMethodSecurity`, enabling `@PreAuthorize` across all controllers.

#### `UserController` — Current State

| Endpoint | Method | Authorization |
|----------|--------|---------------|
| `GET /api/user/me` | `getProfileData` | Authenticated (scoped to `Principal`) |
| `PUT /api/user/me` | `updateProfileData` | Authenticated (scoped to `Principal`) |
| `PUT /api/user/{userId}` | `updateUserInfo` | `SCOPE_ROLE_ADMIN` **or** owner (`userId == jwt.getClaim('userId')`) |
| `DELETE /api/user/{userId}` | `delete` | `SCOPE_ROLE_ADMIN` **or** owner (`userId == jwt.getClaim('userId')`) |

Both `PUT /{userId}` and `DELETE /{userId}` use the owner-or-admin pattern:
```java
@PreAuthorize("hasAuthority('SCOPE_ROLE_ADMIN') or #userId == authentication.credentials.getClaim('userId')")
```

#### `ChatroomController` — Current State

| Endpoint | Method | Authorization |
|----------|--------|---------------|
| `POST /api/chatrooms/{chatroomId}/members/{userId}` | `addChatroomMember` | `SCOPE_ROLE_ADMIN` or chatroom moderator |
| `DELETE /api/chatrooms/{chatroomId}/members/{userId}` | `removeChatroomMember` | `SCOPE_ROLE_ADMIN` or chatroom moderator |
| `GET /api/chatrooms/{chatroomId}/members` | `getMembersByChatroomId` | Authenticated |
| `POST /api/chatrooms/directChat` | `createDirectChat` | Authenticated |
| `POST /api/chatrooms/group` | `createGroupChat` | Authenticated |

Member management endpoints use:
```java
@PreAuthorize("hasAuthority('SCOPE_ROLE_ADMIN') or @chatroomService.isUserModeratorOfChatroom(#chatroomId, #jwt.getClaim('userId'))")
```

#### `AuthController` — Current State

All endpoints under `/api/auth/*` are `permitAll` in the security filter chain (no JWT required):

| Endpoint | Notes |
|----------|-------|
| `POST /api/auth/login` | Open |
| `POST /api/auth/register` | Open — `@PreAuthorize("hasAuthority('SCOPE_ROLE_ADMIN')")` is present but **commented out** |
| `POST /api/auth/reset-password` | Open |
| `POST /api/auth/redeem-password` | Open |
| `POST /api/auth/refresh-token` | Open |

---

### Auth Token Generation — Client Perspective

From the client's viewpoint, token generation is a black box:

| Step | Client Action | Server Response |
|------|--------------|-----------------|
| 1 | Send credentials to `/api/auth/login` or `/api/auth/register` | `{accessToken, refreshToken, id, nickname, role}` |
| 2 | Store both tokens | — |
| 3 | Attach `Authorization: Bearer <accessToken>` to every API call | Protected resource data |
| 4 | When access token expires (401 response) | — |
| 5 | Call `/api/auth/refresh-token` with stored refresh token | New `{accessToken, refreshToken}` pair |
| 6 | Replace both stored tokens | — |

**What's inside the access token (JWT)?**
```json
{
  "iss": "self",
  "sub": "user@email.com",
  "iat": 1713000000,
  "exp": 1713036000,
  "userId": 42,
  "nickname": "johndoe",
  "scope": "ROLE_USER"
}
```
Signed with RSA-256 private key (generated at runtime via `Jwks.generateRsa()`). The client never needs the private key — it only sends the opaque token string.

---

### Right Revocations & Password Resets

#### Token / Right Revocation Mechanisms

| Mechanism | How It Works | Scope |
|-----------|-------------|-------|
| **Refresh token rotation** | On every `/refresh-token` call, the old refresh token is marked `revoked=true` in DB. A new pair is issued. | Per-session |
| **Password reset revocation** | `redeemPassword()` calls `revokeAllByUserId(userId)` — all existing refresh tokens for the user are revoked. | All user sessions |
| **Refresh token expiry** | 30-day TTL checked at refresh time. Expired tokens are auto-revoked. | Per-token |
| **Access token expiry** | 10-hour TTL baked into the JWT. Cannot be revoked early (stateless). | Per-token |

**⚠️ Gap — Access Token Revocation:**  
Access tokens (JWTs) are **stateless** and cannot be individually revoked before expiry. If a user's role changes or account is compromised, the old JWT remains valid for up to 10 hours. Mitigation options:
- Shorten access token TTL (e.g., 15–30 minutes)
- Implement a token blacklist (in Redis) checked on every request
- Add a `tokenVersion` claim and check it against the DB

#### Password Reset Flow

1. Client requests reset via `POST /api/auth/reset-password` with email.
2. Server generates a random code (`ResetTokenGenerator`), hashes it with BCrypt, stores hash + 15-minute expiry on the `User` entity, and emails the raw code.
3. Client submits `POST /api/auth/redeem-password` with `{email, token, newPassword}`.
4. Server verifies the code against the stored hash, checks expiry, updates the password, **revokes ALL refresh tokens** (forcing all sessions to re-authenticate), clears the reset token fields, and returns a fresh token pair.

**Security properties:** Reset codes are hashed (not stored in plaintext), expire in 15 minutes, and a successful reset invalidates all existing sessions.

---

### Why Store Both accessToken and refreshToken in DB?

**Clarification:** The access token (JWT) is **NOT stored in the database**. Only the **refresh token** is persisted in the `refresh_token` table.

| Token | Storage | Format | Purpose |
|-------|---------|--------|---------|
| **Access Token** | Client-side only (memory/storage) | Signed JWT | Short-lived credential for API authorization (10h) |
| **Refresh Token** | Database (`refresh_token` table) + Client | Opaque UUID | Long-lived credential to obtain new access tokens (30 days) |

**Why the refresh token must be in the DB:**

1. **Revocation support:** JWTs are stateless — once issued, they can't be revoked. By storing refresh tokens in the DB with a `revoked` flag, the server can invalidate sessions on demand (password reset, account compromise, admin action).

2. **Rotation detection:** The server uses refresh token rotation — each refresh token is single-use. If a revoked token is presented (possible replay attack / token theft), the server can detect it and potentially revoke all tokens for that user.

3. **Session management:** The DB acts as a registry of active sessions. You can query all active refresh tokens per user, enforce max-session limits, or audit login history.

4. **Expiry enforcement:** While the client could tamper with local expiry checks, the server-side expiry in the DB is authoritative.

**Why NOT store the access token:**
- JWTs are self-contained and verified by signature — no DB lookup needed.
- Keeping API authorization stateless (no DB hit per request) is critical for performance, especially in a chat application.
- If revocation is needed, the short TTL + refresh token revocation provides an acceptable window.

---

### Security Gaps & Recommendations Summary

| # | Issue | Severity | Status | Recommendation |
|---|-------|----------|--------|----------------|
| 1 | `UserController` `PUT /{userId}` and `DELETE /{userId}` had no role checks | **Critical** | ✅ **Resolved** | Owner-or-admin `@PreAuthorize` applied |
| 2 | `ChatroomController` had no auth principal extraction or role checks | **High** | ✅ **Resolved** | Admin-or-moderator `@PreAuthorize` on member management endpoints |
| 3 | WebSocket `preSend` interceptor was commented out — no STOMP-level auth | **High** | ✅ **Resolved** | JWT validated on CONNECT, membership checked on SUBSCRIBE |
| 4 | `@EnableMethodSecurity` was missing on `SecurityConfig` | **Medium** | ✅ **Resolved** | Added — `@PreAuthorize` is now active across all controllers |
| 5 | Access tokens have 10-hour TTL with no revocation mechanism | **Medium** | ⚠️ **Open** | Reduce to 15–30 min or add a Redis blacklist |
| 6 | No rate limiting on `/api/auth/reset-password` | **Low** | ⚠️ **Open** | Add rate limiting to prevent email-bombing |
