# Java 17 Migration Guide for FEI

This guide documents the changes required to upgrade FEI from Java 8 to Java 17. This includes security policy updates and MySQL connector configuration changes.

## Overview of Changes

Two main issues needed to be addressed for Java 17 compatibility:

1. **Security Policy Updates** - Java 17 enforces stricter security checks on reflection APIs
2. **MySQL Connector Driver Class** - The connector underwent a major restructuring in version 8.0

---

## Issue 1: StackWalker Permission (Security Policy)

### What Changed

Java 17 enforces stricter security policy checks on the `StackWalker` API, which is used internally by logging frameworks (like Log4j2) and reflection mechanisms. This permission was not actively enforced in Java 8.

### Error Symptom

```
java.lang.SecurityException: access denied ("java.lang.RuntimePermission" "getStackWalkerWithClassReference")
```

### Solution

**File**: `mdms-komodo-server/etc/config/komodo.policy`

Add the following line in the `grant` block:

```
permission java.lang.RuntimePermission "getStackWalkerWithClassReference";
```

**Complete example**:
```
grant {
    /* Java 7 to 8 permission for Log4j2 Class Loaders */
    permission java.lang.RuntimePermission "getClassLoader";
    permission java.lang.RuntimePermission "getenv.*";
    permission javax.management.MBeanServerPermission "createMBeanServer";

    /* Java 17+ permission for StackWalker API (used by logging and reflection) */
    permission java.lang.RuntimePermission "getStackWalkerWithClassReference";

    /* ... rest of permissions ... */
};
```

This change has already been applied to the repository.

---

## Issue 2: MySQL Connector Driver Class

### What Changed

MySQL Connector/J 8.0 restructured its package naming:

| Aspect | Version 5.x | Version 8.0+ |
|--------|-------------|------------|
| Driver Class | `com.mysql.jdbc.Driver` | `com.mysql.cj.jdbc.Driver` |
| Package Structure | `com.mysql.jdbc.*` | `com.mysql.cj.jdbc.*` |

Your `pom.xml` already specifies the correct version (`mysql-connector-j:8.0.33`), but configuration files may still reference the old driver class name.

### Error Symptom

```
com.mysql.cj.jdbc.exceptions.CommunicationsException: Communications link failure
The last packet sent successfully to the server was 0 milliseconds ago.
The driver has not received any packets from the server.
...
Caused by: javax.net.ssl.SSLHandshakeException: Remote host terminated the handshake
```

The SSL error occurs because when the wrong driver class is specified, the connection properties (like `useSSL=false`) aren't properly applied.

### Solution

#### For Existing Deployments

If you have an existing FEI installation, update your database configuration files:

1. **Find your database configuration file** - typically named `KomodoDB_Pool.properties` or similar

2. **Update the driver class**:
   ```properties
   # OLD (Java 8):
   driverClass = com.mysql.jdbc.Driver

   # NEW (Java 17):
   driverClass = com.mysql.cj.jdbc.Driver
   ```

3. **Update JDBC URL** (if needed):
   ```properties
   # Recommended format for MySQL 8:
   jdbcUrl = jdbc:mysql://<host>:<port>/<database>?useSSL=false&allowPublicKeyRetrieval=true
   ```

#### For New Deployments

The template configuration files have been updated:

- `fei-main/etc/config/KomodoDB_CPDS.properties` ✓
- `fei-main/etc/config/KomodoDB_Pool.properties` ✓
- `fei-containerized-testing/server/config/KomodoDB_Pool.properties` ✓

These now include the correct driver class for Java 17 compatibility.

### Important Notes

- **useSSL=false** is appropriate for development/testing environments
- **Production environments** should use proper SSL certificates instead of `useSSL=false`
- The `allowPublicKeyRetrieval=true` parameter allows password-based authentication when no public key is available

---

## Verification Steps

After making these changes, verify your setup:

1. **Check policy file**:
   ```bash
   grep "getStackWalkerWithClassReference" config/komodo.policy
   ```
   Should output: `permission java.lang.RuntimePermission "getStackWalkerWithClassReference";`

2. **Check database config**:
   ```bash
   grep "driverClass" config/KomodoDB_Pool.properties
   ```
   Should output: `driverClass = com.mysql.cj.jdbc.Driver`

3. **Start server with Java 17**:
   ```bash
   java -version  # Verify Java 17
   java -Djava.security.policy=config/komodo.policy ...
   ```

4. **Test database connection**:
   - Server should connect to MySQL without SSL handshake errors
   - Check logs for any `SecurityException` related to StackWalker

---

## MySQL 8 Compatibility

FEI has been tested with:
- **Java**: 17
- **MySQL**: 8.x
- **MySQL Connector/J**: 8.0.33

The JDBC parameters are configured to work with MySQL 8's default settings.

---

## Troubleshooting

### Still Getting SSL Handshake Errors

1. Verify the driver class is `com.mysql.cj.jdbc.Driver` (not `com.mysql.jdbc.Driver`)
2. Check JDBC URL has `useSSL=false&allowPublicKeyRetrieval=true`
3. Verify MySQL server is accessible on the specified host:port

### Still Getting StackWalker Permission Errors

1. Verify `komodo.policy` contains: `permission java.lang.RuntimePermission "getStackWalkerWithClassReference";`
2. Verify you're running with the policy file: `java -Djava.security.policy=...`
3. Check that the permission is inside the `grant { ... }` block (not commented out)

### Connection Pool Issues

If using c3p0 connection pool (as configured in `fei-main/etc/config/`), ensure both files are updated:
- `KomodoDB_Pool.properties` (pool wrapper)
- `KomodoDB_CPDS.properties` (pool datasource)

---

## References

- [MySQL Connector/J Migration Guide](https://dev.mysql.com/doc/connector-j/8.0/en/)
- [Java 17 Security Manager Changes](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StackWalker.html)
