package com.github.axiomc.license.common.crypto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Set;

public record ServerKeys(KeyPair exchange, KeyPair signing) {

    public static final String EXCHANGE_ALGORITHM = "X25519";
    public static final String SIGNING_ALGORITHM = "Ed25519";

    private static final Set<PosixFilePermission> OWNER_ONLY = PosixFilePermissions.fromString("rw-------");

    public static ServerKeys generate() throws GeneralSecurityException {
        KeyPair exchange = KeyPairGenerator.getInstance(EXCHANGE_ALGORITHM).generateKeyPair();
        KeyPair signing = KeyPairGenerator.getInstance(SIGNING_ALGORITHM).generateKeyPair();
        return new ServerKeys(exchange, signing);
    }

    public static ServerKeys loadOrCreate(Path directory) throws IOException, GeneralSecurityException {
        if (Files.isDirectory(directory) && Files.exists(directory.resolve("exchange.key"))) {
            return new ServerKeys(read(directory, "exchange", EXCHANGE_ALGORITHM), read(directory, "signing", SIGNING_ALGORITHM));
        }
        Files.createDirectories(directory);
        ServerKeys keys = generate();
        write(directory, "exchange", keys.exchange);
        write(directory, "signing", keys.signing);
        return keys;
    }

    public String exchangePublicBase64() {
        return Base64.getEncoder().encodeToString(exchange.getPublic().getEncoded());
    }

    public String signingPublicBase64() {
        return Base64.getEncoder().encodeToString(signing.getPublic().getEncoded());
    }

    public static PublicKey decodePublic(String base64, String algorithm) throws GeneralSecurityException {
        byte[] encoded = Base64.getDecoder().decode(base64);
        return KeyFactory.getInstance(algorithm).generatePublic(new X509EncodedKeySpec(encoded));
    }

    private static KeyPair read(Path directory, String name, String algorithm) throws IOException, GeneralSecurityException {
        KeyFactory factory = KeyFactory.getInstance(algorithm);
        PrivateKey privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(directory.resolve(name + ".key"))));
        PublicKey publicKey = factory.generatePublic(new X509EncodedKeySpec(Files.readAllBytes(directory.resolve(name + ".pub"))));
        return new KeyPair(publicKey, privateKey);
    }

    private static void write(Path directory, String name, KeyPair pair) throws IOException {
        Path privateFile = Files.createFile(directory.resolve(name + ".key"), PosixFilePermissions.asFileAttribute(OWNER_ONLY));
        Files.write(privateFile, pair.getPrivate().getEncoded());
        Files.write(directory.resolve(name + ".pub"), pair.getPublic().getEncoded());
    }
}
