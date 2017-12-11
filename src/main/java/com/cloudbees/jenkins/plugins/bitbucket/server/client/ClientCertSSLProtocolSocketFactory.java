package com.cloudbees.jenkins.plugins.bitbucket.server.client;

import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.commons.httpclient.params.HttpConnectionParams;

import javax.net.SocketFactory;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.security.*;

/*
 * Largely based on http://svn.apache.org/viewvc/httpcomponents/oac.hc3x/trunk/src/contrib/org/apache/commons/httpclient/contrib/ssl/AuthSSLProtocolSocketFactory.java?view=markup
 */
public class ClientCertSSLProtocolSocketFactory implements ProtocolSocketFactory {

    private SSLContext sslContext;
    private KeyStore keyStore;
    private String keyStorePassword;

    public ClientCertSSLProtocolSocketFactory(KeyStore keyStore, String keyStorePassword) {
        super();
        this.keyStore = keyStore;
        this.keyStorePassword = keyStorePassword;
    }

    private SSLContext createSSLContext() {
        try {
            keyStore.load(null, null); // "initialize" the keystore
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(this.keyStore, this.keyStorePassword != null ? this.keyStorePassword.toCharArray() : null);
            KeyManager[] keyManagers = keyManagerFactory.getKeyManagers();
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(keyManagers, null, null);
            return context;
        } catch (NoSuchAlgorithmException e) {
            throw new ClientCertSSLInitializationError("Unsupported algorithm exception: " + e.getMessage());
        } catch (KeyStoreException e) {
            throw new ClientCertSSLInitializationError("KeystoreException: " + e.getMessage());
        } catch (GeneralSecurityException e) {
            throw new ClientCertSSLInitializationError(("General security error: " + e.getMessage()));
        } catch (IOException e) {
            throw new ClientCertSSLInitializationError("IOException: " + e.getMessage());
        }
    }

    private SSLContext getSSLContext() {
        if (this.sslContext == null) {
            this.sslContext = createSSLContext();
        }
        return this.sslContext;
    }

    public Socket createSocket(
            final String host,
            final int port,
            final InetAddress localAddress,
            final int localPort,
            final HttpConnectionParams params
    )
        throws IOException
    {
        if (params == null) {
            throw new IllegalArgumentException("Parameters may not be null");
        }
        int timeout = params.getConnectionTimeout();
        SocketFactory socketFactory = getSSLContext().getSocketFactory();
        if (timeout == 0) {
            return socketFactory.createSocket(host, port, localAddress, localPort);
        } else {
            Socket socket = socketFactory.createSocket();
            SocketAddress localaddr = new InetSocketAddress(localAddress, localPort);
            SocketAddress remoteaddr = new InetSocketAddress(host, port);
            socket.bind(localaddr);
            socket.connect(remoteaddr, timeout);
            return socket;
        }
    }

    public Socket createSocket(String host, int port, InetAddress clientHost, int clientPort)
        throws IOException
    {
        return getSSLContext().getSocketFactory().createSocket(
                host, port, clientHost, clientPort
        );
    }


    public Socket createSocket(String host, int port) throws IOException
    {
        return getSSLContext().getSocketFactory().createSocket(host, port);
    }

}
