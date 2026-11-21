package com.example.zalo.client.net;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import java.security.cert.CertificateExpiredException;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServerCertificateValidatorTest {

    @Test
    void rejectsExpiredCertificate() throws Exception {
        SSLSocket socket = mock(SSLSocket.class);
        SSLSession session = mock(SSLSession.class);
        X509Certificate certificate = mock(X509Certificate.class);
        when(socket.getSession()).thenReturn(session);
        when(session.getPeerCertificates()).thenReturn(new java.security.cert.Certificate[]{certificate});
        doThrow(new CertificateExpiredException()).when(certificate).checkValidity();

        SSLPeerUnverifiedException exception = assertThrows(
                SSLPeerUnverifiedException.class,
                () -> ServerCertificateValidator.validate(socket, "localhost"));

        assertEquals("Server certificate is not currently valid", exception.getMessage());
    }
}
