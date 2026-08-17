# Log4j2 Sensitive Data Masking

## 1. Why Do We Need Log Masking?

Application logs are extremely useful for debugging, monitoring, and investigating production issues.

However, applications often log data that should **never appear in plain text in log files or console output**.

Examples include:

- Passwords
- Access tokens
- Refresh tokens
- JWTs
- API keys
- Client secrets
- Authorization headers
- Email addresses
- Phone numbers
- Credit/debit card numbers
- Session tokens
- Other authentication credentials

For example, an application might accidentally produce:

```text
INFO User login request: username=john password=super-secret-password

or:

DEBUG Request headers: Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...

This creates a security risk because logs can be accessed by:

Developers
System administrators
CI/CD systems
Monitoring platforms
Log aggregation systems
Cloud providers
Support teams
Attackers who gain access to log storage

The goal of log masking is therefore:

Sensitive information should never be exposed in application logs, even if application code accidentally logs it.

2. Why Mask at the Logging Layer?

Masking can be implemented directly in application code.

For example:

log.info("User login: username={}, password={}",
        username,
        "***MASKED***");

However, relying only on developers to remember this everywhere is dangerous.

A large application can contain hundreds or thousands of log statements.

A developer might accidentally write:

log.info("Login request: {}", request);

If request.toString() contains a password, the password is immediately written to the logs.

A centralized logging solution provides a second security boundary.

Instead of relying on every developer to correctly mask sensitive data:

Application
    |
    v
Log statement
    |
    v
Log4j2
    |
    v
MaskingRewritePolicy
    |
    v
SensitiveDataMasker
    |
    v
Masked log output

This means masking happens immediately before the log event reaches the final appender.

3. Recommended Architecture

The recommended architecture is:

Application
    |
    | log.info(...)
    v
Log4j2 LogEvent
    |
    v
RewriteAppender
    |
    v
MaskingRewritePolicy
    |
    v
SensitiveDataMasker
    |
    v
Console / File / Other Appender

The important idea is that the application does not need to know about the masking implementation.

For example:

log.info(
    "Login request password={}",
    password
);

The application produces:

password=super-secret-password

The logging layer transforms it into:

password=***MASKED***

before it reaches the configured appender.

4. Why Use Log4j2 RewritePolicy?

Log4j2 provides a RewritePolicy mechanism that allows a LogEvent to be modified before it reaches an appender.

This makes it a good location for centralized log masking.

The flow is:

LogEvent
   |
   v
RewritePolicy
   |
   +--> inspect message
   |
   +--> detect sensitive data
   |
   +--> replace sensitive values
   |
   v
Masked LogEvent
   |
   v
Appender

The main components are:

MaskingRewritePolicy

Responsible for:

Receiving the Log4j2 event
Extracting the formatted message
Passing the message to the masker
Creating a new masked LogEvent
Failing closed if masking itself fails
SensitiveDataMasker

Responsible for:

Detecting sensitive information
Applying masking rules
Returning the sanitized message

Keeping these responsibilities separate makes the implementation easier to test and maintain.

5. Log4j2 Configuration

Create or update:

src/main/resources/log4j2-spring.xml

Example:

<?xml version="1.0" encoding="UTF-8"?>

<Configuration status="WARN">

    <Appenders>

        <Console
                name="Console"
                target="SYSTEM_OUT">

            <PatternLayout
                    pattern="%d{yyyy-MM-dd HH:mm:ss} %-5level [%t] %logger{36} - %msg%n"/>

        </Console>

        <Rewrite
                name="MaskedConsole"
                ignoreExceptions="false">

            <MaskingRewritePolicy/>

            <AppenderRef ref="Console"/>

        </Rewrite>

    </Appenders>

    <Loggers>

        <Root level="INFO">
            <AppenderRef ref="MaskedConsole"/>
        </Root>

    </Loggers>

</Configuration>

The important part is:

<Rewrite
        name="MaskedConsole"
        ignoreExceptions="false">

    <MaskingRewritePolicy/>

    <AppenderRef ref="Console"/>

</Rewrite>

Instead of sending logs directly to:

<AppenderRef ref="Console"/>

the root logger sends them to:

<AppenderRef ref="MaskedConsole"/>

The MaskedConsole rewrite appender processes the event first.

6. MaskingRewritePolicy

Create:

src/main/java/com/viatech/chat_backend/utils/logging/MaskingRewritePolicy.java

Implementation:

package com.viatech.chat_backend.utils.logging;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.appender.rewrite.RewritePolicy;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;

@Plugin(
        name = "MaskingRewritePolicy",
        category = Node.CATEGORY,
        printObject = true
)
public class MaskingRewritePolicy implements RewritePolicy {

    private static final String MASKING_FAILURE_MESSAGE =
            "***LOG EVENT MASKED DUE TO MASKING FAILURE***";

    public MaskingRewritePolicy() {
    }

    @PluginFactory
    public static MaskingRewritePolicy createPolicy() {
        return new MaskingRewritePolicy();
    }

    @Override
    public LogEvent rewrite(LogEvent event) {

        if (event == null) {
            return null;
        }

        try {

            String originalMessage = event.getMessage() != null
                    ? event.getMessage().getFormattedMessage()
                    : null;

            if (originalMessage == null || originalMessage.isEmpty()) {
                return event;
            }

            String maskedMessage =
                    SensitiveDataMasker.mask(originalMessage);

            if (originalMessage.equals(maskedMessage)) {
                return event;
            }

            return new Log4jLogEvent.Builder(event)
                    .setMessage(new SimpleMessage(maskedMessage))
                    .build();

        } catch (Exception exception) {

            /*
             * Security requirement:
             *
             * Never return the original event when masking fails.
             */
            return new Log4jLogEvent.Builder(event)
                    .setMessage(
                            new SimpleMessage(MASKING_FAILURE_MESSAGE)
                    )
                    .setThrown(null)
                    .build();
        }
    }
}
7. Important Security Decision: Fail Closed

The masking implementation should fail closed.

This is extremely important.

A dangerous implementation would be:

try {
    // mask message
} catch (Exception e) {
    return event;
}

Why is this dangerous?

Suppose the original event contains:

password=super-secret-password

and masking fails.

Returning the original event means:

password=super-secret-password

would still be logged.

Instead, the implementation should replace the entire message:

***LOG EVENT MASKED DUE TO MASKING FAILURE***

This prevents sensitive data from being accidentally exposed when the masking mechanism itself encounters an unexpected error.

8. SensitiveDataMasker

Create:

src/main/java/com/viatech/chat_backend/utils/logging/SensitiveDataMasker.java

Example:

package com.viatech.chat_backend.utils.logging;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SensitiveDataMasker {

    private static final String FULL_MASK = "***MASKED***";

    private static final List<MaskingRule> RULES = List.of(

            /*
             * Authentication secrets.
             */
            new MaskingRule(
                    Pattern.compile(
                            "(?i)(\\\"?(?:password|passwd|pwd|client_secret|clientSecret|refresh_token|refreshToken|access_token|accessToken|session_token|sessionToken|api_key|apiKey|secret_key|secretKey)\\\"?\\s*[:=]\\s*)(\\\"[^\\\"]*\\\"|'[^']*'|[^,\\s}\\]]+)"
                    ),
                    true
            ),

            /*
             * Authorization header.
             */
            new MaskingRule(
                    Pattern.compile(
                            "(?i)(authorization\\s*[:=]\\s*)(?:bearer\\s+)?([^\\s,}\\]]+)"
                    ),
                    true
            ),

            /*
             * Standalone Bearer token.
             */
            new MaskingRule(
                    Pattern.compile(
                            "(?i)(bearer\\s+)([^\\s,}\\]]+)"
                    ),
                    true
            ),

            /*
             * JWT.
             */
            new MaskingRule(
                    Pattern.compile(
                            "\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b"
                    ),
                    false
            ),

            /*
             * Credit/debit card.
             */
            new MaskingRule(
                    Pattern.compile(
                            "(?<!\\d)(\\d[ -]*\\d[ -]*\\d[ -]*\\d[ -]*\\d[ -]*\\d[ -]*\\d[ -]*\\d[ -]*\\d[ -]*\\d[ -]*\\d[ -]*\\d[ -]*\\d[ -]*\\d{1,7})(?!\\d)"
                    ),
                    false
            ),

            /*
             * Email.
             */
            new MaskingRule(
                    Pattern.compile(
                            "\\b([A-Za-z0-9._%+-]{2})[A-Za-z0-9._%+-]*(@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})\\b"
                    ),
                    false
            ),

            /*
             * Phone number.
             */
            new MaskingRule(
                    Pattern.compile(
                            "(?<!\\d)(\\+?\\d[\\d\\s().-]{7,}\\d)(?!\\d)"
                    ),
                    false
            )
    );

    private SensitiveDataMasker() {
    }

    public static String mask(String input) {

        if (input == null || input.isEmpty()) {
            return input;
        }

        String result = input;

        for (MaskingRule rule : RULES) {
            result = applyRule(result, rule);
        }

        return result;
    }

    private static String applyRule(
            String input,
            MaskingRule rule
    ) {

        Matcher matcher = rule.pattern().matcher(input);

        if (!matcher.find()) {
            return input;
        }

        StringBuffer output = new StringBuffer();

        do {

            String replacement;

            if (rule.keepPrefix()) {

                replacement = matcher.group(1)
                        + FULL_MASK;

            } else if (isEmailRule(rule)) {

                replacement = matcher.group(1)
                        + "***"
                        + matcher.group(2);

            } else if (isCardRule(rule)) {

                replacement = maskCardNumber(
                        matcher.group()
                );

            } else if (isPhoneRule(rule)) {

                replacement = maskPhoneNumber(
                        matcher.group()
                );

            } else {

                replacement = FULL_MASK;
            }

            matcher.appendReplacement(
                    output,
                    Matcher.quoteReplacement(replacement)
            );

        } while (matcher.find());

        matcher.appendTail(output);

        return output.toString();
    }

    private static boolean isEmailRule(
            MaskingRule rule
    ) {

        return rule.pattern()
                .pattern()
                .contains("@[A-Za-z0-9.-]+");
    }

    private static boolean isCardRule(
            MaskingRule rule
    ) {

        return rule.pattern()
                .pattern()
                .contains("\\d{1,7}");
    }

    private static boolean isPhoneRule(
            MaskingRule rule
    ) {

        return rule.pattern()
                .pattern()
                .contains("\\+?\\d[\\d\\s().-]");
    }

    private static String maskCardNumber(
            String value
    ) {

        String digitsOnly =
                value.replaceAll("\\D", "");

        if (digitsOnly.length() < 13) {
            return FULL_MASK;
        }

        String lastFour =
                digitsOnly.substring(
                        digitsOnly.length() - 4
                );

        return "*".repeat(
                digitsOnly.length() - 4
        ) + lastFour;
    }

    private static String maskPhoneNumber(
            String value
    ) {

        String digitsOnly =
                value.replaceAll("\\D", "");

        if (digitsOnly.length() <= 4) {
            return FULL_MASK;
        }

        String lastFour =
                digitsOnly.substring(
                        digitsOnly.length() - 4
                );

        return FULL_MASK + lastFour;
    }

    private record MaskingRule(
            Pattern pattern,
            boolean keepPrefix
    ) {
    }
}
9. What Data Is Masked?

The masking implementation should cover the application's known sensitive data.

Passwords

Input:

password=super-secret-password

Output:

password=***MASKED***
Authorization Header

Input:

Authorization: Bearer abc123secret

Output:

Authorization: ***MASKED***
Bearer Token

Input:

Bearer abc123secret

Output:

***MASKED***
JWT

Input:

token=eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.signature

Output:

token=***MASKED***
Email

Input:

email=john.smith@example.com

Output:

email=jo***@example.com

Email masking intentionally keeps enough information to help identify the account during debugging without exposing the complete email address.

Credit Card

Input:

card=4111111111111111

Output:

card=************1111

Only the last four digits remain visible.

Phone Number

Input:

phone=+355691234567

Output:

phone=***MASKED***4567
10. Why Keep Some Information?

Masking everything with:

***MASKED***

is secure, but sometimes makes debugging difficult.

For example:

User email: ***MASKED***

does not tell a developer which account was involved.

Instead:

User email: jo***@example.com

still provides useful debugging information while hiding most of the email address.

Similarly, keeping the last four digits of a card can help identify which card was involved:

************1111

without exposing the complete card number.

The amount of information retained should always be based on the application's security requirements.

11. Example

Suppose application code contains:

log.info(
        "Login request password={} Authorization: Bearer {} email={}",
        "super-secret-password",
        "abc123secret",
        "john.smith@example.com"
);

Without masking:

Login request password=super-secret-password Authorization: Bearer abc123secret email=john.smith@example.com

With masking:

Login request password=***MASKED*** Authorization: ***MASKED*** email=jo***@example.com

The application code does not need to change.

12. Testing

Log masking should be tested at two levels.

Unit Tests

Test the masking engine directly.

Example:

@Test
void shouldMaskPassword() {

    String input =
            "password=super-secret-password";

    String result =
            SensitiveDataMasker.mask(input);

    assertThat(result)
            .contains("password=***MASKED***");

    assertThat(result)
            .doesNotContain("super-secret-password");
}
Authorization Test
@Test
void shouldMaskAuthorizationHeader() {

    String input =
            "Authorization: Bearer abc123secret";

    String result =
            SensitiveDataMasker.mask(input);

    assertThat(result)
            .contains("Authorization: ***MASKED***");

    assertThat(result)
            .doesNotContain("abc123secret");
}
Email Test
@Test
void shouldMaskEmail() {

    String input =
            "email=john.smith@example.com";

    String result =
            SensitiveDataMasker.mask(input);

    assertThat(result)
            .contains("jo***@example.com");

    assertThat(result)
            .doesNotContain("john.smith@example.com");
}
13. Integration Testing

Unit tests prove that:

SensitiveDataMasker

works.

However, that is not enough.

We also need to verify:

Log4j2
   |
   v
RewriteAppender
   |
   v
MaskingRewritePolicy
   |
   v
Console/File Appender

The integration test should verify that a real Log4j2 logging event gets masked before reaching the output.

A useful test message is:

TEST password=super-secret-password Authorization: Bearer abc123secret email=john.smith@example.com

Expected output:

TEST password=***MASKED*** Authorization: ***MASKED*** email=jo***@example.com

The test should also assert that the original secrets do not appear in the captured output.

14. Important Testing Consideration

Avoid relying on Log4j2 internal classes unnecessarily in tests.

For example, OutputStreamManager#setOutputStream(...) is protected and should not be manipulated directly from a test.

Similarly, Log4j2 has several classes with similar names:

org.apache.logging.log4j.core.Logger
org.apache.logging.log4j.core.config.LoggerConfig

These are different types and should not be mixed.

When writing integration tests, prefer testing through the public logging API:

Logger logger =
        LogManager.getLogger(Log4j2MaskingIntegrationTest.class);

rather than depending heavily on Log4j2 internal implementation details.

This makes the tests less fragile when the Log4j2 version changes.

15. Fail-Closed Requirement

The masking implementation must never accidentally expose the original message.

Bad:

catch (Exception exception) {
    return event;
}

Good:

catch (Exception exception) {

    return new Log4jLogEvent.Builder(event)
            .setMessage(
                    new SimpleMessage(
                            "***LOG EVENT MASKED DUE TO MASKING FAILURE***"
                    )
            )
            .setThrown(null)
            .build();
}

The security principle is:

Masking failure
      |
      v
Do NOT output original event
      |
      v
Replace with safe message
16. Multiple Appenders

A common mistake is to protect only the console.

For example:

<Rewrite name="MaskedConsole">
    <MaskingRewritePolicy/>
    <AppenderRef ref="Console"/>
</Rewrite>

but then configure:

<Root level="INFO">
    <AppenderRef ref="MaskedConsole"/>
    <AppenderRef ref="File"/>
</Root>

In this configuration, the console is masked but the file appender may receive the original event.

The safer architecture is to ensure every output path receives the masked event.

For example:

                         +--> Console
                        /
LogEvent -> Masking -> Rewrite
                        \
                         +--> File

All destinations should receive the sanitized event.

17. Logging Should Still Follow Secure Coding Practices

Log masking is a safety net.

It should not become an excuse to log sensitive information.

Prefer:

log.info(
        "User {} authenticated successfully",
        userId
);

instead of:

log.info(
        "Authentication request: {}",
        authenticationRequest
);

Especially avoid logging complete:

HTTP requests
HTTP headers
Authentication objects
Security contexts
JWT claims
Password-containing DTOs
Database entities containing secrets

The preferred strategy is:

1. Don't log sensitive data
        +
2. Centrally mask sensitive data

rather than relying entirely on:

Centrally mask sensitive data
18. Logging Sensitive Data Is Different From Logging Identifiers

Not every identifier needs to be completely hidden.

For example:

userId=12345
requestId=abc-123
chatroomId=42
mediaId=789

may be perfectly reasonable to log.

These identifiers can be essential for troubleshooting.

The masking rules should therefore focus on information that creates a meaningful security or privacy risk.

19. Performance Considerations

Regex-based masking runs for every log event that reaches the masking policy.

Therefore:

Compile Pattern objects once.
Do not compile regexes inside mask().
Keep the number of rules reasonable.
Avoid unnecessarily complex regex patterns.
Avoid masking very large log payloads where possible.
Prefer structured logging with explicit fields when possible.
Do not log huge request/response bodies unnecessarily.

The implementation should use:

private static final List<MaskingRule> RULES = List.of(...);

rather than:

public static String mask(String input) {

    Pattern pattern =
            Pattern.compile("...");

    ...
}

This prevents regex compilation for every log event.

20. Regex Maintenance

Regex-based masking is not perfect.

For example, applications may introduce new sensitive fields:

privateKey
private_key
firebaseToken
deviceToken
turnSecret
databasePassword
encryptionKey

These should be evaluated and added when appropriate.

For example:

(?:password
|passwd
|pwd
|client_secret
|clientSecret
|refresh_token
|refreshToken
|access_token
|accessToken
|api_key
|apiKey
|secret_key
|secretKey
)

can be expanded as the application evolves.

The masking rules should therefore be treated as security configuration that requires maintenance.

21. Structured Logging Consideration

If the project eventually moves to structured JSON logging, masking should be designed around structured fields rather than only raw strings.

For example:

{
    "userId": "123",
    "email": "john.smith@example.com",
    "accessToken": "eyJ..."
}

A structured masking solution can selectively mask:

{
    "userId": "123",
    "email": "jo***@example.com",
    "accessToken": "***MASKED***"
}

This is generally more reliable than trying to infer field boundaries from arbitrary text.

However, for an existing application using normal Log4j2 messages, a RewritePolicy plus centralized regex masking is a practical solution.

22. Security Requirements

The implementation should satisfy the following requirements:

Requirement 1

Sensitive authentication credentials must never appear in logs in plaintext.

Requirement 2

Authorization headers must be masked.

Requirement 3

JWTs must be masked.

Requirement 4

Passwords and secrets must be masked regardless of whether they appear in:

JSON
key=value
key: value
Requirement 5

Email addresses should be partially masked where debugging value is required.

Requirement 6

Phone numbers should be partially masked where debugging value is required.

Requirement 7

Card numbers should expose only the last four digits.

Requirement 8

If masking fails, the original event must never be returned.

Requirement 9

All log output destinations must receive masked events.

Requirement 10

Masking behavior must be covered by automated tests.

23. Example Project Structure

A possible project structure is:

src/
├── main/
│   ├── java/
│   │   └── com/viatech/chat_backend/
│   │       └── utils/
│   │           └── logging/
│   │               ├── MaskingRewritePolicy.java
│   │               └── SensitiveDataMasker.java
│   │
│   └── resources/
│       └── log4j2-spring.xml
│
└── test/
    └── java/
        └── com/viatech/chat_backend/
            ├── SensitiveDataMaskerTest.java
            └── Log4j2MaskingIntegrationTest.java
24. Example End-to-End Flow

Application code:

log.info(
        "Authentication request password={} email={} Authorization: Bearer {}",
        password,
        email,
        token
);

↓

Log4j2 creates:

LogEvent

↓

MaskingRewritePolicy receives the event.

↓

SensitiveDataMasker.mask() processes the message.

↓

Original:

Authentication request password=secret123 email=john.smith@example.com Authorization: Bearer abc123

↓

Masked:

Authentication request password=***MASKED*** email=jo***@example.com Authorization: ***MASKED***

↓

RewriteAppender forwards the masked event.

↓

Console/File/Log collector receives:

Authentication request password=***MASKED*** email=jo***@example.com Authorization: ***MASKED***

The original sensitive values never reach the final logging destination.

25. What This Protects Against

Centralized log masking helps protect against accidental exposure through:

Debug logs
Authentication logs
Request logging
Exception messages
DTO toString() output
HTTP header logging
Security-related logging
Third-party libraries that log data
Developer mistakes

It does not guarantee that every secret is detected.

For example, if a developer logs:

the secret is: banana123

there is no reliable way for a generic masking system to know that banana123 is a secret.

Therefore:

Log masking is a defense-in-depth mechanism, not a replacement for secure logging practices.

26. Recommended Approach

For a Spring Boot + Log4j2 project, the recommended approach is:

                    Application
                         |
                         v
                    SLF4J Logger
                         |
                         v
                       Log4j2
                         |
                         v
               MaskingRewritePolicy
                         |
                         v
                 SensitiveDataMasker
                         |
                         v
                   Masked Event
                         |
              +----------+----------+
              |                     |
              v                     v
           Console                File
              |                     |
              +----------+----------+
                         |
                         v
                  Log Aggregator

The key security principle is:

Do not trust application code alone to protect secrets.

Use centralized masking as an additional security layer.

27. Final Checklist

Before considering the implementation complete, verify:

 Log4j2 is the active logging implementation.
 MaskingRewritePolicy is registered as a Log4j2 plugin.
 log4j2-spring.xml contains the rewrite appender.
 Root logger uses the masked appender.
 Passwords are masked.
 API keys are masked.
 Client secrets are masked.
 Access tokens are masked.
 Refresh tokens are masked.
 Authorization headers are masked.
 Bearer tokens are masked.
 JWTs are masked.
 Email addresses are masked.
 Phone numbers are masked.
 Card numbers are masked.
 Masking failure fails closed.
 Tests verify the original sensitive value is absent.
 All log destinations are protected.
 New sensitive fields are added to the masking rules when introduced.
 Developers are instructed not to intentionally log secrets.
28. Conclusion

Log4j2 masking provides a centralized security layer that prevents sensitive information from being written to application logs in plaintext.

The implementation consists of three main pieces:

log4j2-spring.xml
        |
        v
MaskingRewritePolicy
        |
        v
SensitiveDataMasker

The RewritePolicy intercepts Log4j2 events, while the SensitiveDataMasker detects and replaces sensitive values.

The most important security rule is:

If masking succeeds:
    write the masked event.

If masking fails:
    do NOT write the original event.

This approach provides defense in depth and significantly reduces the risk of credentials, tokens, personal information, and payment information leaking through application logs.