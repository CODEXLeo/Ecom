package com.microservice.user.security.jwt;


// Provides the UTF-8 character encoding constant.
//
// We use UTF_8 when reading the PEM file as text so that Java
// knows how to convert the bytes from the file into characters.
import java.nio.charset.StandardCharsets;


// Provides methods for reading files.
//
// We use Files.readString(...) to read the entire PEM file
// into a Java String.
import java.nio.file.Files;


// Represents a filesystem path.
//
// Path.of(...) converts the String path from JwtProperties
// into a Java Path object that Files can use.
import java.nio.file.Path;


// Provides KeyFactory.
//
// KeyFactory is responsible for converting encoded key data
// into Java Key objects such as RSAPrivateKey and RSAPublicKey.
import java.security.KeyFactory;


// Java interface representing an RSA private key.
//
// We will create one of these from private_key.pem.
import java.security.interfaces.RSAPrivateKey;


// Java interface representing an RSA public key.
//
// We will create one of these from public_key.pem.
import java.security.interfaces.RSAPublicKey;


// Represents the standard encoding format used by our
// PKCS#8 private key.
//
// Our private key PEM looks like:
//
// -----BEGIN PRIVATE KEY-----
// ...
// -----END PRIVATE KEY-----
//
// The Base64 content inside it is PKCS#8 encoded.
import java.security.spec.PKCS8EncodedKeySpec;


// Represents the standard encoding format used by our
// X.509 SubjectPublicKeyInfo public key.
//
// Our public key PEM looks like:
//
// -----BEGIN PUBLIC KEY-----
// ...
// -----END PUBLIC KEY-----
import java.security.spec.X509EncodedKeySpec;


// Provides Base64 decoding.
//
// PEM files contain Base64-encoded key data, so we need to
// decode that Base64 content into raw binary key bytes.
import java.util.Base64;


// Spring's @Bean annotation.
//
// It tells Spring that the return value of a method should
// become a Spring-managed bean.
import org.springframework.context.annotation.Bean;


// Spring's @Configuration annotation.
//
// It tells Spring that this class contains configuration
// and bean definitions.
import org.springframework.context.annotation.Configuration;


// Marks this class as a Spring configuration class.
//
// Spring will discover this class during component scanning
// and process the @Bean methods inside it.
@Configuration
public class JwtKeyConfig {


    // Holds the JWT configuration loaded from application.properties.
    //
    // JwtProperties contains things such as:
    //
    // security.jwt.private-key-path
    // security.jwt.public-key-path
    //
    // It does NOT contain the actual private/public key.
    private final JwtProperties jwtProperties;


    // Constructor injection.
    //
    // Spring sees that JwtKeyConfig requires JwtProperties
    // and supplies the JwtProperties object automatically.
    //
    // Therefore we do not manually create JwtProperties here.
    public JwtKeyConfig(JwtProperties jwtProperties) {
        // Store the injected JwtProperties object in our
        // instance variable so the bean methods can use it.
        this.jwtProperties = jwtProperties;

    }


    // Tells Spring that the object returned by this method
    // should be registered as a Spring bean.
    //
    // The resulting bean will have the type:
    //
    // RSAPrivateKey
    //
    // Later JwtConfig will be able to request this bean
    // through dependency injection.
    @Bean
    RSAPrivateKey rsaPrivateKey() {

        // Key loading involves filesystem access and cryptographic
        // parsing, both of which can throw checked exceptions.
        //
        // Instead of forcing every caller to handle those exceptions,
        // we handle them here and convert them into a clear
        // application startup failure.
        try {
            // Get the configured private-key path from JwtProperties.
            //
            // For example, the environment variable:
            //
            // JWT_PRIVATE_KEY_PATH
            //
            // may contain:
            //
            // C:/Users/swata/.config/
            // microservice-user-service/jwt/private_key.pem
            //
            // jwtProperties.getPrivateKeyPath()
            // returns that String.
            //
            // Path.of(...)
            // converts the String into a Java Path object.
            //
            // Files.readString(...)
            // reads the entire PEM file into a String.
            //
            // StandardCharsets.UTF_8
            // tells Java to interpret the file using UTF-8.
            String pem = Files.readString(Path.of(jwtProperties.getPrivateKeyPath()), StandardCharsets.UTF_8);


            // At this point 'pem' contains something similar to:
            //
            // -----BEGIN PRIVATE KEY-----
            // MIIEv...
            // ...
            // -----END PRIVATE KEY-----
            //
            // We cannot directly give this entire String to
            // Base64.getDecoder().
            //
            // First we need to remove the PEM header,
            // PEM footer, and whitespace.
            String privateKeyContent = pem


                    // Remove the PEM header.
                    //
                    // Before:
                    //
                    // -----BEGIN PRIVATE KEY-----
                    // ABCDEFG...
                    //
                    // After:
                    //
                    // ABCDEFG...
                    //
                    // The second argument "" means:
                    // replace the matched text with nothing.
                    .replace("-----BEGIN PRIVATE KEY-----", "")


                    // Remove the PEM footer.
                    //
                    // Before:
                    //
                    // ABCDEFG...
                    // -----END PRIVATE KEY-----
                    //
                    // After:
                    //
                    // ABCDEFG...
                    .replace("-----END PRIVATE KEY-----", "")


                    // Remove whitespace.
                    //
                    // \\s is the regular-expression pattern
                    // representing whitespace.
                    //
                    // This removes:
                    //
                    // spaces
                    // tabs
                    // newlines
                    // carriage returns
                    //
                    // We want one continuous Base64 string.
                    .replaceAll("\\s", "");


            // Decode the Base64 text into its original binary
            // representation.
            //
            // PEM stores the key as Base64 text because binary
            // data cannot conveniently be represented directly
            // as normal text.
            //
            // keyBytes therefore contains the actual encoded
            // PKCS#8 private-key bytes.
            byte[] keyBytes = Base64.getDecoder().decode(privateKeyContent);


            // Tell Java what format these binary bytes represent.
            //
            // Our private key uses PKCS#8 encoding.
            //
            // keyBytes:
            //
            // Base64-decoded binary data
            //
            // becomes:
            //
            // PKCS8EncodedKeySpec
            //
            // which KeyFactory can understand.
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);


            // Ask Java's security framework for a KeyFactory
            // capable of creating RSA keys.
            //
            // "RSA" tells Java which cryptographic algorithm
            // the key belongs to.
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");


            // Convert the PKCS#8 encoded key specification
            // into an actual Java RSA private-key object.
            //
            // generatePrivate(...) creates a PrivateKey.
            //
            // We know that the key is RSA, so we cast it to:
            //
            // RSAPrivateKey
            //
            // This object will later be supplied to JwtEncoder
            // so JWTs can be digitally signed.
            return (RSAPrivateKey)keyFactory.generatePrivate(keySpec);
        } catch (Exception exception) {

            // If anything goes wrong while loading or parsing
            // the private key, stop application startup with
            // a meaningful application-level exception.
            //
            // Examples of possible failures:
            //
            // - configured path does not exist
            // - file cannot be read
            // - invalid Base64
            // - invalid PKCS#8 data
            // - key is not a valid RSA private key
            //
            // The original exception is passed as the cause,
            // preserving the underlying technical error for logs.
            throw new IllegalStateException("Failed to load RSA private key from configured path", exception);

        }

    }


    // Creates the RSA public-key Spring bean.
    //
    // This is conceptually the same process as the private key,
    // but the public key uses X.509 encoding instead of PKCS#8.
    //
    // JwtConfig will later use this public key to configure
    // JwtDecoder.
    @Bean
    RSAPublicKey rsaPublicKey() {

        // Handle errors while reading and parsing the public key.
        try {

            // Get the public-key path from JwtProperties.
            //
            // For example:
            //
            // C:/Users/swata/.config/
            // microservice-user-service/jwt/public_key.pem
            //
            // Path.of(...) converts the String into a Path.
            //
            // Files.readString(...) reads the complete PEM file.
            //
            // UTF_8 specifies the character encoding.
            String pem = Files.readString(Path.of(jwtProperties.getPublicKeyPath()), StandardCharsets.UTF_8);

            // Remove the PEM header, footer, and whitespace.
            //
            // The public-key PEM has this structure:
            //
            // -----BEGIN PUBLIC KEY-----
            // Base64 data...
            // -----END PUBLIC KEY-----
            //
            // After these replacements we are left with
            // only the Base64 data.
            String publicKeyContent = pem


                    // Remove public-key PEM header.
                    .replace("-----BEGIN PUBLIC KEY-----", "")


                    // Remove public-key PEM footer.
                    .replace("-----END PUBLIC KEY-----", "")


                    // Remove newlines, spaces, tabs, etc.
                    //
                    // This produces one continuous Base64 string.
                    .replaceAll("\\s", "");


            // Decode the Base64 public-key data into binary bytes.
            //
            // These bytes contain the X.509 encoded public key.
            byte[] keyBytes = Base64.getDecoder().decode(publicKeyContent);


            // Tell Java that these bytes represent an X.509
            // encoded public key.
            //
            // This is the format produced by:
            //
            // openssl rsa -pubout
            //
            // for our generated public key.
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);


            // Obtain Java's RSA KeyFactory.
            //
            // This factory knows how to construct RSA key objects
            // from encoded key specifications.
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");


            // Convert the X.509 encoded public-key specification
            // into an actual Java RSAPublicKey object.
            //
            // JwtDecoder will later use this public key to verify
            // signatures generated using the corresponding private key.
            return (RSAPublicKey)keyFactory.generatePublic(keySpec);


        } catch (Exception exception) {

            // If loading or parsing the public key fails,
            // fail application startup with a meaningful message.
            //
            // The original exception is retained as the cause.
            throw new IllegalStateException("Failed to load RSA public key from configured path", exception);

        }

    }

}