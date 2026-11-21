package com.example.zalo.client.net;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

/** Performs explicit post-handshake certificate validity and hostname checks. */
public final class ServerCertificateValidator {

    private ServerCertificateValidator() {
    }

    public static void validate(SSLSocket socket, String expectedHost) throws SSLPeerUnverifiedException {
        try {
            Certificate[] chain = socket.getSession().getPeerCertificates();
            if (chain.length == 0 || !(chain[0] instanceof X509Certificate certificate)) {
                throw new SSLPeerUnverifiedException("Server did not provide an X.509 certificate");
            }
            certificate.checkValidity();
            if (!matchesHostname(certificate, expectedHost)) {
                throw new SSLPeerUnverifiedException("Server certificate does not match expected host: " + expectedHost);
            }
        } catch (java.security.cert.CertificateExpiredException | java.security.cert.CertificateNotYetValidException exception) {
            SSLPeerUnverifiedException invalid = new SSLPeerUnverifiedException("Server certificate is not currently valid");
            invalid.initCause(exception);
            throw invalid;
        }
    }

    private static boolean matchesHostname(X509Certificate certificate, String expectedHost) throws SSLPeerUnverifiedException {
        try {
            Collection<List<?>> subjectAlternativeNames = certificate.getSubjectAlternativeNames();
            if (subjectAlternativeNames != null) {
                for (List<?> entry : subjectAlternativeNames) {
                    if (entry.size() >= 2 && entry.get(0) instanceof Integer type && type == 2
                            && entry.get(1) instanceof String dnsName && matchesDns(expectedHost, dnsName)) {
                        return true;
                    }
                    if (entry.size() >= 2 && entry.get(0) instanceof Integer type && type == 7
                            && entry.get(1) instanceof String ipAddress && expectedHost.equalsIgnoreCase(ipAddress)) {
                        return true;
                    }
                }
                return false;
            }
            LdapName subject = new LdapName(certificate.getSubjectX500Principal().getName());
            for (Rdn rdn : subject.getRdns()) {
                if ("CN".equalsIgnoreCase(rdn.getType()) && matchesDns(expectedHost, String.valueOf(rdn.getValue()))) {
                    return true;
                }
            }
            return false;
        } catch (Exception exception) {
            SSLPeerUnverifiedException invalid = new SSLPeerUnverifiedException("Could not validate server certificate hostname");
            invalid.initCause(exception);
            throw invalid;
        }
    }

    private static boolean matchesDns(String expectedHost, String certificateName) {
        String host = expectedHost.toLowerCase(Locale.ROOT);
        String name = certificateName.toLowerCase(Locale.ROOT);
        if (!name.startsWith("*.")) {
            return host.equals(name);
        }
        String suffix = name.substring(1);
        return host.endsWith(suffix) && host.length() > suffix.length() && host.indexOf('.') == host.length() - suffix.length();
    }
}
