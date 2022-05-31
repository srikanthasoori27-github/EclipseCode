/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.messaging.sms;

import com.twilio.http.HttpClient;
import com.twilio.http.NetworkHttpClient;
import com.twilio.http.TwilioRestClient;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;


/**
 * This is copied from an example provided by Twilio to use an http proxy when
 * talking to their API.
 *
 * See https://www.twilio.com/docs/libraries/java/custom-http-clients-java?code-sample=code-proxiedtwilioclientcreator&code-language=Java&code-sdk-version=default
 *
 * Created by keith.yarbrough on 4/25/2019
 *
 */
public class ProxiedTwilioClientCreator {
    private String username;
    private String password;
    private String proxyHost;
    private int proxyPort;
    private HttpClient httpClient;

    /**
     * Constructor for ProxiedTwilioClientCreator
     * @param username the twilio username
     * @param password the twilio authtoken
     * @param proxyHost the proxy host
     * @param proxyPort the proxy port
     */
    public ProxiedTwilioClientCreator(String username, String password, String proxyHost, int proxyPort) {
        this.username = username;
        this.password = password;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
    }

    /**
     * Creates a custom HttpClient based on default config as seen on:
     * {@link com.twilio.http.NetworkHttpClient#NetworkHttpClient() constructor}
     */
    private void createHttpClient() {

        // The constants used in here are copied from com.twilio.http.NetworkHttpClient.
        // Unfortunately, they are declared as private statics in NetworkHttpClient.
        // Since we do not currently support configuring these network settings in IdentityIQ,
        // I am not adding that capability now.

        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(10000)
                .setSocketTimeout(30500)
                .build();

        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setDefaultMaxPerRoute(10);
        connectionManager.setMaxTotal(10*2);

        HttpHost proxy = new HttpHost(proxyHost, proxyPort);

        HttpClientBuilder clientBuilder = HttpClientBuilder.create();
        clientBuilder
                .setConnectionManager(connectionManager)
                .setProxy(proxy)
                .setDefaultRequestConfig(config);

        // Inclusion of Twilio headers and build() is executed under this constructor
        this.httpClient = new NetworkHttpClient(clientBuilder);
    }

    /**
     * Get the custom client or builds a new one
     * @return a TwilioRestClient object
     */
    public TwilioRestClient getClient() {
        if (this.httpClient == null) {
            this.createHttpClient();
        }

        TwilioRestClient.Builder builder = new TwilioRestClient.Builder(username, password);
        return builder.httpClient(this.httpClient).build();
    }
}
