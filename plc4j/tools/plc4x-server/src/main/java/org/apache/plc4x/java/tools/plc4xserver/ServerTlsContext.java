/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.plc4x.java.tools.plc4xserver;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HexFormat;

/**
 * Holds the {@link SSLContext} the server listens with, plus the SHA-256 fingerprint of the
 * presented certificate. Either loads an operator-provided keystore or, when none is configured,
 * generates an ephemeral self-signed certificate so TLS works with zero configuration. The
 * fingerprint lets a client pin/trust the auto-generated identity.
 */
public final class ServerTlsContext {

    private final SSLContext sslContext;
    private final String certificateFingerprint;
    private final boolean selfSigned;

    private ServerTlsContext(SSLContext sslContext, String certificateFingerprint, boolean selfSigned) {
        this.sslContext = sslContext;
        this.certificateFingerprint = certificateFingerprint;
        this.selfSigned = selfSigned;
    }

    public SSLContext getSslContext() {
        return sslContext;
    }

    public String getCertificateFingerprint() {
        return certificateFingerprint;
    }

    public boolean isSelfSigned() {
        return selfSigned;
    }

    /**
     * Builds a TLS context from an existing PKCS12/JKS keystore.
     */
    public static ServerTlsContext fromKeystore(String keystorePath, String keystorePassword,
                                                String keystoreType) throws Exception {
        char[] password = keystorePassword == null ? new char[0] : keystorePassword.toCharArray();
        KeyStore keyStore = KeyStore.getInstance(keystoreType == null ? "PKCS12" : keystoreType);
        try (var in = new java.io.FileInputStream(keystorePath)) {
            keyStore.load(in, password);
        }
        SSLContext sslContext = buildContext(keyStore, password);
        return new ServerTlsContext(sslContext, fingerprintOfFirst(keyStore), false);
    }

    /**
     * Generates an ephemeral self-signed certificate and wraps it in a fresh TLS context.
     */
    public static ServerTlsContext selfSigned() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048, new SecureRandom());
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        X500Name subject = new X500Name("CN=PLC4X-Server");
        Instant now = Instant.now();
        Date notBefore = Date.from(now.minus(1, ChronoUnit.HOURS));
        Date notAfter = Date.from(now.plus(3650, ChronoUnit.DAYS));
        BigInteger serial = BigInteger.valueOf(System.nanoTime());

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
            subject, serial, notBefore, notAfter, subject, keyPair.getPublic());
        // Add localhost / loopback SANs so common-name verification can succeed for local use.
        GeneralNames sans = new GeneralNames(new GeneralName[]{
            new GeneralName(GeneralName.dNSName, "localhost"),
            new GeneralName(GeneralName.iPAddress, "127.0.0.1")
        });
        certBuilder.addExtension(Extension.subjectAlternativeName, false, sans);

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        X509Certificate certificate = new JcaX509CertificateConverter()
            .getCertificate(certBuilder.build(signer));

        char[] password = new char[0];
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, password);
        keyStore.setKeyEntry("plc4x-server", keyPair.getPrivate(), password,
            new X509Certificate[]{certificate});

        SSLContext sslContext = buildContext(keyStore, password);
        return new ServerTlsContext(sslContext, fingerprint(certificate), true);
    }

    private static SSLContext buildContext(KeyStore keyStore, char[] password) throws Exception {
        KeyManagerFactory keyManagerFactory =
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, password);
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), null, new SecureRandom());
        return sslContext;
    }

    private static String fingerprintOfFirst(KeyStore keyStore) throws Exception {
        var aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            var cert = keyStore.getCertificate(alias);
            if (cert instanceof X509Certificate x509) {
                return fingerprint(x509);
            }
        }
        return "unknown";
    }

    private static String fingerprint(X509Certificate certificate) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(certificate.getEncoded());
        return HexFormat.ofDelimiter(":").withUpperCase().formatHex(hash);
    }

}
