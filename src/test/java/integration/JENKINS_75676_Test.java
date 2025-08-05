package integration;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.testcontainers.containers.NginxContainer;
import org.testcontainers.utility.MountableFile;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class JENKINS_75676_Test {
    static MockWebServer bitbucketMock;
    static NginxContainer<?> nginx;
    static File tmpConf;
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @BeforeClass
    public static void setup() throws IOException, InterruptedException {
        runScript("src/test/resources/JENKINS-75676/generate-mtls-certs.sh");
        bitbucketMock = new MockWebServer();
        bitbucketMock.enqueue(new MockResponse()
                .setBody("Bitbucket Mock Server Up")
                .setResponseCode(200));
        bitbucketMock.start();

        // 2. Dynamically write nginx.conf, using the mockwebserver port and host.docker.internal
        int bbPort = bitbucketMock.getPort();
        String nginxConf = renderNginxConfTemplate(bbPort);

        tmpConf = File.createTempFile("nginx-ssl", ".conf");
        Files.write(tmpConf.toPath(), nginxConf.getBytes(StandardCharsets.UTF_8));

        // 3. Start Nginx container, using the temp conf
        nginx = new NginxContainer<>("nginx:1.28")
                .withExposedPorts(8443)
                .withCopyFileToContainer(MountableFile.forHostPath(tmpConf.getAbsolutePath()), "/etc/nginx/nginx.conf")
                .withCopyFileToContainer(MountableFile.forClasspathResource("JENKINS-75676/certs/nginx/server.crt"), "/etc/nginx/certs/server.crt")
                .withCopyFileToContainer(MountableFile.forClasspathResource("JENKINS-75676/certs/nginx/server.key"), "/etc/nginx/certs/server.key")
                .withCopyFileToContainer(MountableFile.forClasspathResource("JENKINS-75676/certs/nginx/rootCA.crt"), "/etc/nginx/certs/rootCA.crt");
        nginx.start();
    }
    @AfterClass
    public static void tearDown() throws IOException {
        // 5. Cleanup
        if (nginx != null)
            nginx.stop();
        if(bitbucketMock != null)
            bitbucketMock.shutdown();
        if (tmpConf != null && tmpConf.exists())
            tmpConf.delete();
    }
    @Ignore
    @Test
    public void canAccessViaNginxProxy() throws Exception {
        String proxyUrl = "http://" + nginx.getHost() + ":" + nginx.getMappedPort(8443) + "/";
        HttpURLConnection conn = (HttpURLConnection) new URL(proxyUrl).openConnection();
        int code = conn.getResponseCode();
        String body = new java.util.Scanner(conn.getInputStream()).useDelimiter("\\A").next();
        assertThat(code).isEqualTo(200);
        assertThat(body).contains("Bitbucket Mock Server Up");


    }

    @Ignore
    @Test
    public void proxyReturnsMasterBranch() throws Exception {
        // Enqueue response for the branches endpoint
        bitbucketMock.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{ \"values\": [ { \"displayId\": \"master\", \"id\": \"refs/heads/master\", \"isDefault\": true } ], \"isLastPage\": true }"));

        // Create the proxy URL
        String branchesUrl = "http://" + nginx.getHost() + ":" + nginx.getMappedPort(8080) +
                "/rest/api/1.0/projects/myorg/repos/testrepo/branches";

        HttpURLConnection conn = (HttpURLConnection) new URL(branchesUrl).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        int responseCode = conn.getResponseCode();
        assertThat(responseCode).isEqualTo(200);

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder body = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            body.append(line);
        }
        reader.close();

        String bodyString = body.toString().replaceAll("\\s+", "");
        assertThat(bodyString).contains("\"displayId\":\"master\"");
        assertThat(bodyString).contains("\"isDefault\":true");
    }


    @Test
    public void canAccessViaNginxProxyWithClientCert() throws Exception {
        SSLContext sslContext = setupSSLContext();
        String proxyUrl = "https://" + nginx.getHost() + ":" + nginx.getMappedPort(8443) + "/";
        HttpsURLConnection conn = (HttpsURLConnection) new URL(proxyUrl).openConnection();
        conn.setSSLSocketFactory(sslContext.getSocketFactory());
        int code = conn.getResponseCode();
        String body = new java.util.Scanner(conn.getInputStream()).useDelimiter("\\A").next();
        assertThat(code).isEqualTo(200);
        assertThat(body).contains("Bitbucket Mock Server Up");
    }

    public static SSLContext setupSSLContext() throws Exception {
        // Load PKCS12 keystore (from cert generation script)
        char[] password = "changeit".toCharArray();
        KeyStore clientStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = JENKINS_75676_Test.class.getResourceAsStream("/JENKINS-75676/certs/client_keystore.p12")) {
            clientStore.load(in, password);
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(clientStore, password);

        // Trust store with CA root
        KeyStore trustStore = KeyStore.getInstance("JKS");
        trustStore.load(null, null);
        try (InputStream in = JENKINS_75676_Test.class.getResourceAsStream("/JENKINS-75676/certs/rootCA.crt")) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            trustStore.setCertificateEntry("ca", cf.generateCertificate(in));
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
        return sslContext;
    }

    private static String renderNginxConfTemplate(int bitbucketPort) throws IOException {
        try (InputStream is = JENKINS_75676_Test.class.getResourceAsStream("/JENKINS-75676/nginx.conf.tmpl")) {
            if (is == null)
                throw new IllegalStateException("Missing nginx.conf.tmpl file in test resources");
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return content.replace("${BITBUCKET_PORT}", String.valueOf(bitbucketPort));
        }
    }

    private static void runScript(String scriptPath) throws IOException, InterruptedException {
        File script = new File(scriptPath);
        if (!script.exists()) {
            throw new IllegalArgumentException("Cert generation script does not exist: " + scriptPath);
        }
        // Make script executable if not already
        script.setExecutable(true);

        // Run the script
        ProcessBuilder pb = new ProcessBuilder("bash", script.getAbsolutePath());
        pb.redirectErrorStream(true); // merge stderr with stdout
        Process process = pb.start();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) {
                System.out.println("[certgen] " + line);
            }
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("Cert generation script failed with exit code " + exitCode);
        }
    }

}
