/*****************************************************************************
 * Copyright (C) 1999 California Institute of Technology. All rights reserved
 * US Government Sponsorship under NASA contract NAS7-918 is acknowledged
 ****************************************************************************/
package jpl.mipl.mdms.FileService.net;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import jpl.mipl.mdms.FileService.io.BufferedStreamIO;
import jpl.mipl.mdms.FileService.komodo.util.ConfigFileURLResolver;
import jpl.mipl.mdms.utils.logging.Logger;

/**
 * This class hides all the complexity of creating a secure socket (be it
 * server or client)
 * <B>Modification History :</B>
 * ----------------------
 *
 * <B>Date              Who              What</B>
 * ----------------------------------------------------------------------------
 * 2018-02-01        William             originally client secure sockets only have same cipher suites as server sockets.
 *                                          The inconvenience arose when cipher suites are updated.
 *                                          All clients need to update their config file to match server suites.
 *                                          Disabling this will ensure that created socket has all available suites from Java.
 */
public class SecureSocketsUtil {
   // using SSL version 3.
   //private String _algorithm = "SSLv3";
   private String _algorithm = "TLSv1.3";
   private SSLContext _context;
   private final Object _contextLock = new Object();
   private TrustManager[] _tms;

   private static final Logger _logger = Logger.getLogger(SecureSocketsUtil.class.getName());

   //private SSLSocketFactory _clientFactory;


   private static final String CIPHER_KEY = "komodo.net.ciphers";
   private static final String PROTOCOL_KEY = "komodo.net.protocol";
   private static final String FIPS_MODE_KEY = "komodo.fips.enabled";
   private static final String NAMED_GROUPS_KEY = "jdk.tls.namedGroups";

   // BC-FIPS provider class name - loaded dynamically to avoid hard dependency
   private static final String BCFIPS_PROVIDER_CLASS = "org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider";

   // FIPS 140-2/140-3 approved named groups (elliptic curves) for TLS key exchange
   // x25519 is NOT FIPS-approved - only P-256, P-384, P-521 are approved
   // See NIST SP 800-56A Rev. 3 for approved curves
   private static final String FIPS_APPROVED_NAMED_GROUPS = "secp256r1,secp384r1,secp521r1";

   // Bouncy Castle FIPS KeyStore type (available but not default)
   private static final String BCFKS_KEYSTORE_TYPE = "BCFKS";
   // PKCS12 keystore type - works in both FIPS and non-FIPS mode.
   // In FIPS mode, BC-FIPS at provider priority 1 handles all PKCS12
   // crypto operations (PBE encryption, MAC) using FIPS-approved algorithms.
   // FIPS compliance comes from the cryptographic MODULE (BC-FIPS),
   // not the keystore file FORMAT.
   private static final String PKCS12_KEYSTORE_TYPE = "PKCS12";

   // Track if FIPS provider has been initialized
   private static boolean _fipsInitialized = false;
   // Track if FIPS mode is active (provider loaded successfully)
   private static boolean _fipsModeActive = false;

   // FIPS 140-2/140-3 compliant cipher suites using AES-GCM and SHA-2
   // These work with both RSA and ECDSA certificates
   // Can be overridden via -Dkomodo.net.ciphers system property
   private static String[] _STRONGSUITES =
         {
             // GCM mode ciphers (preferred - authenticated encryption)
             "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
             "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
             "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
             "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
             "TLS_RSA_WITH_AES_256_GCM_SHA384",
             "TLS_RSA_WITH_AES_128_GCM_SHA256",
             // CBC mode fallbacks with SHA-2
             "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384",
             "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
             "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384",
             "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
             "TLS_RSA_WITH_AES_256_CBC_SHA256",
             "TLS_RSA_WITH_AES_128_CBC_SHA256"
         };




   /**
    * Constructor to initialize security providers.
    */
   public SecureSocketsUtil() {
      // Initialize BC-FIPS provider for FIPS 140-2/140-3 compliance (enabled by default)
      // Set -Dkomodo.fips.enabled=false to disable if needed
      initializeFipsProvider();

      //Dynamic loading of security providers. Should be in
      //$JAVA_HOME/jre/lib/security/java.security, but never know
      // Commenting these out as they become problematic if using
      // a non Sun/Oracle JVM (e.g. IBM jvm).  Will rely on
      // the java.security file to define which providers are loaded
      //  - awt 11/19/14
      //Security.addProvider(new sun.security.provider.Sun());
      //Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider());

      // Have to manually load the trustStore in case it is bundled
      // in a jar file (as in the case for webstart).
      try {
        //get the domain file
          ConfigFileURLResolver resolver = new ConfigFileURLResolver();
          URL trustStoreURL = resolver.getKeyStoreFile();
          //String trustStore = System.getProperty("javax.net.ssl.trustStore");
          if (trustStoreURL != null) {
              // Determine keystore type — defaults to PKCS12 in all modes.
              // System property override still honored for environments using BCFKS.
              String trustStoreType = getKeystoreType("javax.net.ssl.trustStoreType");

              // Get the appropriate KeyStore instance.
              // For BCFKS, uses BC-FIPS provider explicitly.
              // For PKCS12, uses default provider resolution (BC-FIPS at priority 1).
              KeyStore ks = loadTrustKeyStore(trustStoreType);

              TrustManagerFactory tmFactory = TrustManagerFactory.getInstance(
                  TrustManagerFactory.getDefaultAlgorithm());

              InputStream ksStream = trustStoreURL.openStream();

              // Read truststore password from standard Java system property
              // Password is required for both PKCS12 and BCFKS formats
              String trustStorePwd = System.getProperty("javax.net.ssl.trustStorePassword");
              char[] tsPassword = (trustStorePwd != null) ? trustStorePwd.toCharArray() : null;
              ks.load(ksStream, tsPassword);
              ksStream.close();

              tmFactory.init(ks);
              this._tms = tmFactory.getTrustManagers();
              this._context = SSLContext.getInstance(this._algorithm);
              this._context.init(null, this._tms, this._getSecureRandom());
          } else {
        	  this._context = SSLContext.getInstance(this._algorithm);
	          this._context.init(null, null, this._getSecureRandom());
          }


          // Get cipher suites override from properties
          // If specified, expecting a csv list of ciphers
          String cipherOverride = System.getProperty(SecureSocketsUtil.CIPHER_KEY);
          String[] cipherList = null;
          if (cipherOverride != null) {
              cipherList = cipherOverride.split(",");
          }

          if (cipherList != null && cipherList.length > 0) {
              SecureSocketsUtil._STRONGSUITES = cipherList;
          }

          // Get protocol override from properties
          String protocolOverride = System.getProperty(SecureSocketsUtil.PROTOCOL_KEY);
          if (protocolOverride != null) {
              this._algorithm = protocolOverride;
          }
      } catch (Exception e) {
         SecureSocketsUtil._logger.error("ERROR: Failed to initialize SSL truststore: " + e.getMessage());
          e.printStackTrace();
          // Attempt fallback to default SSL context so connections can still be attempted
          if (this._context == null) {
              try {
                  this._context = SSLContext.getInstance(this._algorithm);
                  this._context.init(null, null, this._getSecureRandom());
              } catch (Exception fallbackEx) {
                  SecureSocketsUtil._logger.error("ERROR: Fallback SSL context also failed: " + fallbackEx.getMessage());
              }
          }
      }


   }

   /**
    * Constructor to initialize security providers and initialize SSL context.
    *
    * @param passphrase password to keystore
    * @param keys absolute path to keys file
    * @throws IOException       when keystore access fail
    * @throws SecurityException when context initialization fail
    */
   public SecureSocketsUtil(String keys, String passphrase)
         throws IOException, SecurityException {
      this();
      this._context = getSSLContext(passphrase, keys);
   }

   /**
    * Constructor to initialize providers, context, and use the input
    * algorithm.
    *
    * @param key absolute path to keys file
    * @param passphrase password to the keystore
    * @param algorithm the algorithm to be used (defaults to SSLv3)
    * @throws IOException       when keystore access fail
    * @throws SecurityException when context initialization fail
    */
   public SecureSocketsUtil(String key, String passphrase, String algorithm)
         throws IOException, SecurityException {
      this(key, passphrase);
      this._algorithm = algorithm;
   }

   /**
    * Gets a secure server socket and return it to the calling program. The
    * method throws a generic exception which contains the message of the
    * actual exception (one of 6 possible)
    *
    * @param port the port number (between 0 and 65536)
    * @return a secure server socket object
    * @throws IOException when network problem occurs
    */
   public SSLServerSocket getSecureServerSocket(int port) throws IOException {
      SSLServerSocket newSocket;
      SSLServerSocketFactory factory = this._context.getServerSocketFactory();
      newSocket = (SSLServerSocket) factory.createServerSocket(port);
      newSocket.setEnabledCipherSuites(SecureSocketsUtil._STRONGSUITES);
      return newSocket;
   }

   /**
    * Gets a secure client socket on the specified host and port number.
    *
    * @param host the remote host name
    * @param port the remote port number (between 0 and 65536)
    * @return a secure client socket object
    * @throws IOException when network problem occurs
    */
   public synchronized SSLSocket getSecureClientSocket(String host, int port)
         throws IOException {
       return this.getSecureClientSocket(host, port, 0);
   }


   /**
    * Gets a secure client socket on the specified host and port number.
    *
    * @param host the remote host name
    * @param port the remote port number (between 0 and 65536)
    * @return a secure client socket object
    * @throws IOException when network problem occurs
    */
   public synchronized SSLSocket getSecureClientSocket(String host, int port,
                                             int timeout) throws IOException {

      // Use the SSLContext created in the constructor rather than recreating it.
      // Recreating the context via SSLContext.getInstance() + init() causes
      // the JDK to re-wrap trust managers in new X509TrustManagerImpl instances,
      // which can lose trust anchors when BC-FIPS is at provider priority 1.
      // The constructor already initializes _context with proper trust managers.
      SSLSocketFactory factory;
      synchronized (_contextLock) {
          factory = (SSLSocketFactory) this._context.getSocketFactory();
      }

      // If port = 0, return null. This allows the dynamic loading of
      // socket classes before attempting a real connection.
      if (port == 0)
         return null;

      SSLSocket socket = (SSLSocket) factory.createSocket(host, port);

      // Just enable the strong cypher suites.
      /* NOTE: updated on 2018/02/01
         originally client secure sockets only have same cipher suites as server sockets.
         The inconvenience arose when cipher suites are updated.
         All clients need to update their config file to match server suites.
         Disabling this will ensure that created socket has all available suites from Java.
       */
      //socket.setEnabledCipherSuites(SecureSocketsUtil._STRONGSUITES);

      /*
       * If timeout non-zero, set SoTimeout value.  When set, a read() call
       * on the InputStream associated with socket will block for this time.
       * If timeout expires, java.net.SocketTimeoutException is thrown.
       */
      if (timeout > 0)
          socket.setSoTimeout(timeout);

      return socket;
   }

   /**
    * Gets a secure client socket on the specified host and port from the
    * specified local INET address and port.
    *
    * @param host the remote host name
    * @param port the remote port number (between 0 and 65536)
    * @param localAddr the InetAdress of local network interface
    * @param localPort the local port for full-duplex connection.
    * @param timeout If non-zero, enables the SO_TIMEOUT with the timeout
    *                value.
    * @return the secure client socket object
    * @throws IOException when network problem occurs
    */
   public SSLSocket getSecureClientSocket(String host,
                                          int port,
                                          InetAddress localAddr,
                                          int localPort,
                                          int timeout)
         throws IOException {
      SSLSocketFactory factory = this._context.getSocketFactory();
            //(SSLSocketFactory) SSLSocketFactory.getDefault();
      SSLSocket socket =
            (SSLSocket) factory.createSocket(host, port, localAddr, localPort);

      /*
         * * enable all the suites. * String[] supported =
         * socket.getSupportedCipherSuites(); *
         * socket.setEnabledCipherSuites(supported); * Just enable the strong
         * cypher suites.
         */
      /* NOTE: updated on 2018/02/01
         originally client secure sockets only have same cipher suites as server sockets.
         The inconvenience arose when cipher suites are updated.
         All clients need to update their config file to match server suites.
         Disabling this will ensure that created socket has all available suites from Java.
       */
      //socket.setEnabledCipherSuites(SecureSocketsUtil._STRONGSUITES);

      /*
       * If timeout non-zero, set SoTimeout value.  When set, a read() call
       * on the InputStream associated with socket will block for this time.
       * If timeout expires, java.net.SocketTimeoutException is thrown.
       */
      if (timeout > 0)
          socket.setSoTimeout(timeout);

      return socket;
   }

   /**
    * Gets a secure client socket on the specified host and port from the
    * specified local INET address and port.
    *
    * @param host the remote host name
    * @param port the remote port number (between 0 and 65536)
    * @param localAddr the InetAdress of local network interface
    * @param localPort the local port for full-duplex connection.
    * @return the secure client socket object
    * @throws IOException when network problem occurs
    */
   public SSLSocket getSecureClientSocket(String host,
                                          int port,
                                          InetAddress localAddr,
                                          int localPort)
         throws IOException {
      return this.getSecureClientSocket(host, port, localAddr, localPort, 0);
   }

   /**
    * Gets a secure client socket with authorization. The client machine is
    * assumed to be authorized to connect to the remote server host, therefore
    * store clpher suites is not enabled for this socket.
    *
    * @param host the remote host name
    * @param port the remote port number (between 0 and 65536)
    * @return the secure client socket object
    * @throws IOException when I/O failure
    */
   public SSLSocket getSecureClientSocketWithAuth(String host, int port)
         throws IOException {
      SSLSocketFactory factory = this._context.getSocketFactory();
      return (SSLSocket) factory.createSocket(host, port);
   }

   /**
    * Sets up SSL context for server sockets and client sockets with
    * authorization
    *
    * @param passphrase password to keystore
    * @param keys absolute path to keys file
    * @return the SSL context object for the server socket.
    * @throws IOException       when keystore access fail
    * @throws SecurityException when context initialization fail
    */
   private SSLContext getSSLContext(String passphrase, String keys)
         throws SecurityException, IOException {
      SSLContext context;
      FileInputStream fis = null;
      try {

         // Determine keystore type — defaults to PKCS12 in all modes.
         // System property override still honored for environments using BCFKS.
         String keyStoreType = getKeystoreType("javax.net.ssl.keyStoreType");

         // Get the appropriate KeyStore instance.
         // For BCFKS, use BC-FIPS provider explicitly.
         // For PKCS12, use default provider resolution — see truststore
         // loading comment above for full rationale.
         KeyStore ks;
         if (_fipsModeActive && BCFKS_KEYSTORE_TYPE.equals(keyStoreType)) {
             ks = KeyStore.getInstance(keyStoreType, "BCFIPS");
         } else {
             ks = KeyStore.getInstance(keyStoreType);
         }

         KeyManagerFactory kmf = KeyManagerFactory.getInstance(
             KeyManagerFactory.getDefaultAlgorithm());

         // Load keystore with password
         char[] passwd = passphrase.toCharArray();
         fis = new FileInputStream(keys);
         ks.load(fis, passwd);
         kmf.init(ks, passwd);

         // Initialize SSL context with BOTH key managers AND trust managers
         // Key managers: loaded from the server's keystore (for server cert/private key)
         // Trust managers: loaded in constructor from truststore (for validating client certs)
         // NOTE: Previously this was calling init() twice which wiped out trust managers!
         context = SSLContext.getInstance(this._algorithm);
         context.init(kmf.getKeyManagers(), this._tms, this._getSecureRandom());
      } catch (KeyManagementException e) {
         throw new SecurityException(e.getMessage());
      } catch (KeyStoreException e) {
         throw new SecurityException(e.getMessage());
      } catch (NoSuchAlgorithmException e) {
         throw new SecurityException(e.getMessage());
      } catch (UnrecoverableKeyException e) {
         throw new SecurityException(e.getMessage());
      } catch (CertificateException e) {
         throw new SecurityException(e.getMessage());
      } catch (Exception e) {
         // Catch NoSuchProviderException and other provider-related exceptions
         throw new SecurityException(e.getMessage());
      } finally {
         if (fis != null) {
            try { fis.close(); } catch (IOException ignore) {}
         }
      }
      return context;
   }

   /**
    * Methdo to create our own SecrueRandom seed object.  By default, the JVM looks
    * for local random generator method which will impact the performance.  For example,
    * on Sun and Linux, it requires to read from device /dev/random which could be blocked
    * if not enough entropy have been collected
    *
    * @return a SecureRandom object
    */
   private SecureRandom _getSecureRandom() {
      // Use a non-BC-FIPS provider explicitly. After BC-FIPS is inserted at
      // provider priority 1, new SecureRandom() routes through BC-FIPS DEFAULT
      // which may not function correctly on non-FIPS platforms (e.g. macOS)
      // when used to seed an SSLContext, causing TLS handshake failures.
      // obtainNonBcSecureRandom() tries NativePRNGNonBlocking/SUN first (fast,
      // non-blocking) and falls through a list of SUN algorithms before
      // falling back to new SecureRandom() as a last resort.
      try {
         return obtainNonBcSecureRandom();
      } catch (Exception e) {
         // Last resort — accept whatever the default provider gives us
         long baseSeed = (new java.util.Date()).getTime();
         SecureRandom sr = new SecureRandom();
         sr.setSeed(baseSeed);
         return sr;
      }
   }

   /**
    * Get a KeyStore instance for truststore loading.
    * Uses default provider resolution (BC-FIPS at priority 1 when FIPS active).
    * For BCFKS format, the BC-FIPS provider is used explicitly.
    */
   private static KeyStore loadTrustKeyStore(String trustStoreType) throws KeyStoreException {
      if (_fipsModeActive && BCFKS_KEYSTORE_TYPE.equals(trustStoreType)) {
         try {
            return KeyStore.getInstance(trustStoreType, "BCFIPS");
         } catch (Exception e) {
            throw new KeyStoreException("Failed to get BCFKS KeyStore from BCFIPS provider", e);
         }
      }

      return KeyStore.getInstance(trustStoreType);
   }

   /**
    * Determine the keystore type based on FIPS mode and system properties.
    *
    * @param systemProperty The system property to check for override (e.g., "javax.net.ssl.keyStoreType")
    * @return The keystore type to use (BCFKS in FIPS mode, otherwise from property or PKCS12)
    */
   private static String getKeystoreType(String systemProperty) {
      // First check for explicit system property override
      String override = System.getProperty(systemProperty);
      if (override != null && !override.isEmpty()) {
         return override;
      }

      // PKCS12 is the standard keystore format for all modes.
      // In FIPS mode, BC-FIPS at provider priority 1 handles all PKCS12
      // crypto operations using FIPS-approved algorithms.
      // FIPS compliance comes from the cryptographic MODULE (BC-FIPS),
      // not the keystore file FORMAT.
      // Note: BCFKS was previously the default here but has a known
      // DER re-encoding bug (bc-java#2007) that breaks cert chain verification.
      // Environments can still use BCFKS via -Djavax.net.ssl.keyStoreType=BCFKS.
      return PKCS12_KEYSTORE_TYPE;
   }

   /**
    * Build a safe single-token replacement for the {@code securerandom.strongAlgorithms}
    * security property. This value is set permanently (not restored after BC-FIPS init)
    * because BC-FIPS calls {@code getCoreSecureRandom()} → {@code getInstanceStrong()}
    * on every {@code new SecureRandom()} invocation, not just at startup.
    *
    * <p>The multi-token comma-separated default value causes catastrophic regex
    * backtracking inside {@code SecureRandom.getInstanceStrong()} → StackOverflowError.
    * A single token eliminates the backtracking.
    *
    * <p>Strategy (in order):
    * <ol>
    *   <li>Probe registered JCA providers for a {@code SecureRandom.PKCS11} service
    *       from a SunPKCS11 provider. On FIPS Linux this is the only working algorithm;
    *       the property does NOT list it, so we cannot rely on the property string.
    *   <li>Fall back to the first token of the original property (works on macOS /
    *       non-FIPS Linux where SUN NativePRNG algorithms are available).
    *   <li>Last resort: {@code NativePRNGBlocking:SUN}.
    * </ol>
    *
    * @param original the current value of {@code securerandom.strongAlgorithms}
    * @return a safe single-token replacement value
    */
   private static String buildSafeStrongAlgorithms(String original) {
      // Pass 1: probe registered providers for a PKCS11 SecureRandom.
      // On FIPS Linux, SunPKCS11-NSS-FIPS is security.provider.1 and exposes
      // SecureRandom.PKCS11, but the securerandom.strongAlgorithms property does
      // NOT list it. We must discover it by inspecting the provider list directly.
      for (Provider p : Security.getProviders()) {
         if (p.getName().toUpperCase().contains("PKCS11")
               && p.getService("SecureRandom", "PKCS11") != null) {
            String token = "PKCS11:" + p.getName();
            SecureSocketsUtil._logger.debug("INFO: SecureSocketsUtil: found PKCS11 SecureRandom in provider: "
                + p.getName() + " → using token: " + token);
            return token;
         }
      }

      // Pass 2: no PKCS11 provider present (macOS, non-FIPS Linux) — use the first
      // token from the original property. On macOS this is "NativePRNGBlocking:SUN",
      // which is a real working algorithm. Do NOT hardcode PKCS11 as a fallback here:
      // if no SunPKCS11 provider exists, getInstanceStrong("PKCS11:...") throws, BC-FIPS
      // catches Exception and calls new SecureRandom() → BC-FIPS SPI recursion → SOE.
      if (original != null) {
         String first = original.split(",")[0].trim();
         if (!first.isEmpty()) {
            return first;
         }
      }
      return "NativePRNGBlocking:SUN";
   }

   /**
    * Obtain a SecureRandom instance from a non-BC provider to use as the entropy
    * source when constructing BouncyCastleFipsProvider. On OS-FIPS-enabled Linux
    * several algorithms may be blocked; this method tries in order of preference
    * and returns the first that succeeds.
    *
    * @return a SecureRandom backed by a non-BC provider
    * @throws Exception if no suitable algorithm is available
    */
   private static SecureRandom obtainNonBcSecureRandom() throws Exception {
      // Algorithms to try, in preference order.
      // NativePRNGNonBlocking is available on Linux/SUN even in FIPS mode
      // and never blocks waiting for entropy.
      String[][] candidates = {
         {"NativePRNGNonBlocking", "SUN"},
         {"NativePRNG",            "SUN"},
         {"NativePRNGBlocking",    "SUN"},
         {"SHA1PRNG",              "SUN"},
      };
      Exception last = null;
      for (String[] candidate : candidates) {
         try {
            SecureRandom sr = SecureRandom.getInstance(candidate[0], candidate[1]);
            sr.nextBytes(new byte[32]); // force full init / entropy collection
            return sr;
         } catch (Exception e) {
            SecureSocketsUtil._logger.debug("INFO: SecureSocketsUtil: " + candidate[0] + "/"
                + candidate[1] + " not available: " + e.getMessage());
            last = e;
         }
      }
      // Last resort: platform default (may re-enter BC-FIPS on FIPS Linux, but
      // at least we've tried every non-blocking option first)
      try {
         SecureRandom sr = new SecureRandom();
         sr.nextBytes(new byte[32]);
         return sr;
      } catch (Exception e) {
         last = e;
      }
      throw new Exception("No SecureRandom available to seed BC-FIPS provider", last);
   }

   /**
    * Initialize the Bouncy Castle FIPS provider for FIPS 140-2/140-3 compliance.
    * FIPS mode is ENABLED by default. Set -Dkomodo.fips.enabled=false to disable.
    *
    * The BC-FIPS provider is loaded dynamically to avoid a hard dependency -
    * if the BC-FIPS jars are not on the classpath, a warning will be printed
    * but the application will still function with the default JVM providers.
    */
   /**
    * Initialize the Bouncy Castle FIPS provider. Safe to call multiple times (idempotent).
    * Must be called before any CipherUtil or SSL/TLS operations on FIPS-enabled systems.
    * Calling this explicitly at application startup avoids provider ordering issues.
    */
   public static void ensureFipsProviderInitialized() {
      initializeFipsProvider();
   }

   private static synchronized void initializeFipsProvider() {
      if (_fipsInitialized) {
         return;
      }

      // FIPS mode is enabled by default for FIPS 140-2/140-3 compliance
      // Set -Dkomodo.fips.enabled=false to disable if needed
      String fipsEnabled = System.getProperty(FIPS_MODE_KEY, "true");
      if ("false".equalsIgnoreCase(fipsEnabled)) {
         _fipsInitialized = true;
         return;
      }

      // Configure FIPS-compliant TLS settings BEFORE any SSL operations
      // These settings apply regardless of whether BC-FIPS provider loads successfully

       // Explicitly disable BC-FIPS "approved only" mode.
       // BC-FIPS 2.0+ auto-detects when the JVM is running on an OS-FIPS system
       // and may automatically enter approved-only mode, which blocks direct RSA
       // encrypt/decrypt operations (CipherUtil uses RSA/OAEP for auth tokens).
       // Setting this to "false" BEFORE provider instantiation prevents that.
       // FIPS compliance is preserved: BC-FIPS at provider priority 1 still uses
       // only FIPS-approved algorithm implementations for TLS/SSL; the approved_only
       // flag only additionally restricts non-TLS crypto like direct RSA ciphers.
       System.setProperty("org.bouncycastle.fips.approved_only", "false");

      // Restrict TLS named groups to FIPS-approved curves only
      // x25519 is NOT FIPS-approved - only P-256, P-384, P-521 are approved
      // See NIST SP 800-56A Rev. 3 for approved curves
      String existingGroups = System.getProperty(NAMED_GROUPS_KEY);
      if (existingGroups == null || existingGroups.isEmpty()) {
         System.setProperty(NAMED_GROUPS_KEY, FIPS_APPROVED_NAMED_GROUPS);
      }

      try {
         // Check if BC-FIPS provider is already registered
         Provider existingProvider = Security.getProvider("BCFIPS");
         if (existingProvider != null) {
            _fipsModeActive = true;
            _fipsInitialized = true;
            return;
         }

         // On OS-FIPS-enabled Linux, BouncyCastleFipsProvider's constructor causes a
         // StackOverflowError via two intertwined problems:
         //
         // Problem 1 (regex catastrophe): getCoreSecureRandom() calls
         //   SecureRandom.getInstanceStrong(), which matches each token in the
         //   "securerandom.strongAlgorithms" security property against a regex.
         //   On FIPS Linux the property is a comma-separated multi-token string
         //   ("NativePRNGBlocking:SUN,DRBG:SUN,...") that causes catastrophic
         //   backtracking → StackOverflowError before any SecureRandom is returned.
         //
         // Problem 2 (BC-FIPS SPI recursion): After insertProviderAt(), any
         //   new SecureRandom() call goes through getDefaultPRNG → BC-FIPS SPI →
         //   getCoreSecureRandom() → getInstanceStrong() → regex catastrophe again,
         //   or falls back to new SecureRandom() → infinite recursion.
         //
         // Root cause: securerandom.strongAlgorithms on this JVM is
         //   "NativePRNGBlocking:SUN,DRBG:SUN" — but the ONLY working SecureRandom
         //   on FIPS Linux is PKCS11 from SunPKCS11-NSS-FIPS, which is NOT listed
         //   in that property. The property cannot be trusted.
         //
         // Fix: probe registered providers at runtime for a working PKCS11
         //   SecureRandom. If found, permanently override securerandom.strongAlgorithms
         //   to the single token "PKCS11:<providerName>". This eliminates both the
         //   regex catastrophe (single token) and the recursion (getInstanceStrong()
         //   resolves immediately to the PKCS11 SecureRandom, not BC-FIPS's SPI).
         //   The property is NOT restored after init: BC-FIPS calls getCoreSecureRandom()
         //   on every new SecureRandom() invocation, so restoring the multi-token value
         //   would cause the same StackOverflowError on subsequent calls.
         //   On macOS / non-FIPS Linux where no PKCS11 SecureRandom exists, fall
         //   back to the first token of the original property (a SUN algorithm that
         //   actually works on that platform) — also left in place permanently.
         Class<?> providerClass = Class.forName(BCFIPS_PROVIDER_CLASS);
         Provider bcFipsProvider = null;

         String origStrongAlgs = Security.getProperty("securerandom.strongAlgorithms");
         SecureSocketsUtil._logger.debug("INFO: SecureSocketsUtil: securerandom.strongAlgorithms=" + origStrongAlgs);
         String safeStrongAlgs = buildSafeStrongAlgorithms(origStrongAlgs);
         SecureSocketsUtil._logger.debug("INFO: SecureSocketsUtil: overriding strongAlgorithms to: " + safeStrongAlgs);
         Security.setProperty("securerandom.strongAlgorithms", safeStrongAlgs);

         try {
            SecureRandom seedRandom = obtainNonBcSecureRandom();
            SecureSocketsUtil._logger.debug("INFO: SecureSocketsUtil: seed SecureRandom: "
                + seedRandom.getProvider().getName() + "/" + seedRandom.getAlgorithm());

            java.lang.reflect.Constructor<?> ctor = null;
            try {
               ctor = providerClass.getConstructor(String.class, SecureRandom.class);
            } catch (NoSuchMethodException ignored) {
               // fall through to no-arg
            }

            try {
               if (ctor != null) {
                  SecureSocketsUtil._logger.debug("INFO: SecureSocketsUtil: calling BouncyCastleFipsProvider(null, SecureRandom)");
                  bcFipsProvider = (Provider) ctor.newInstance(null, seedRandom);
               } else {
                  SecureSocketsUtil._logger.debug("INFO: SecureSocketsUtil: calling BouncyCastleFipsProvider() [no-arg]");
                  bcFipsProvider = (Provider) providerClass.getDeclaredConstructor().newInstance();
               }
            } catch (java.lang.reflect.InvocationTargetException ite) {
               Throwable cause = ite.getCause();
               SecureSocketsUtil._logger.error("ERROR: SecureSocketsUtil: BC-FIPS constructor threw: "
                   + (cause != null ? cause : ite));
               if (cause instanceof Exception) {
                  throw (Exception) cause;
               }
               throw new RuntimeException("BC-FIPS constructor failed", cause);
            }
            SecureSocketsUtil._logger.debug("INFO: SecureSocketsUtil: BouncyCastleFipsProvider instantiated OK");

            // Insert while safe value is still active. insertProviderAt() triggers
            // JCA provider probing which may call new SecureRandom() → BC-FIPS SPI
            // → getCoreSecureRandom() → getInstanceStrong(). With a single-token
            // value that actually resolves, this returns immediately.
            Security.insertProviderAt(bcFipsProvider, 1);
            SecureSocketsUtil._logger.debug("INFO: SecureSocketsUtil: BCFIPS inserted into provider list");

            // Force BC-FIPS lazy init NOW while the safe value is still active.
            // getCoreSecureRandom() must complete with the single-token value.
            // Do NOT restore the original multi-token property afterwards:
            // BC-FIPS's PooledSecureRandomProvider calls getCoreSecureRandom() on
            // every new SecureRandom() invocation (not just once at startup), so
            // restoring the multi-token property would cause catastrophic regex
            // backtracking → StackOverflowError on any subsequent SecureRandom use.
            // The safe single-token value is correct for this platform and should
            // remain set for the lifetime of the JVM.
            try {
               SecureRandom.getInstance("PKCS11", seedRandom.getProvider());
               SecureSocketsUtil._logger.debug("INFO: SecureSocketsUtil: BC-FIPS SecureRandom warmed up via explicit provider");
            } catch (Exception warmupEx) {
               // Not a PKCS11 env (e.g. macOS) — try generic warmup via BC-FIPS directly
               try {
                  SecureRandom.getInstance("DEFAULT", bcFipsProvider);
                  SecureSocketsUtil._logger.debug("INFO: SecureSocketsUtil: BC-FIPS SecureRandom warmed up via BCFIPS provider");
               } catch (Exception warmup2Ex) {
                  SecureSocketsUtil._logger.debug("INFO: SecureSocketsUtil: warmup skipped: " + warmup2Ex.getMessage());
               }
            }
         } catch (Exception e) {
            // BC-FIPS init failed — restore original property since BC-FIPS was not inserted
            if (origStrongAlgs != null) {
               Security.setProperty("securerandom.strongAlgorithms", origStrongAlgs);
               SecureSocketsUtil._logger.debug("INFO: SecureSocketsUtil: restored securerandom.strongAlgorithms (BC-FIPS init failed)");
            }
            throw e;
         }

         _fipsModeActive = true;
         _fipsInitialized = true;
      } catch (ClassNotFoundException e) {
         SecureSocketsUtil._logger.error("WARNING: BC-FIPS provider not found on classpath. FIPS mode not available.");
         SecureSocketsUtil._logger.error("Add bc-fips, bcpkix-fips, and bcutil-fips jars to enable FIPS mode.");
         _fipsInitialized = true;
      } catch (Exception e) {
         SecureSocketsUtil._logger.error("ERROR: Failed to initialize BC-FIPS provider: " + e.getMessage());
         e.printStackTrace();
         _fipsInitialized = true;
      }
   }
}
