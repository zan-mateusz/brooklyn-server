/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.util.core.logbook;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.text.Strings;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.ssl.SSLContextBuilder;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.brooklyn.util.core.logbook.LogbookConfig.BASE_NAME_LOGBOOK;

public class OpenSearchLogStore implements LogStore {

    /*
     example config for local default implementation
     brooklyn.logbook.logStore = org.apache.brooklyn.util.core.logbook.OpenSearchLogStore
     brooklyn.logbook.openSearchLogStore.host = https://localhost:9200
     brooklyn.logbook.openSearchLogStore.index = brooklyn8
     brooklyn.logbook.openSearchLogStore.user = admin
     brooklyn.logbook.openSearchLogStore.password = admin
     # brooklyn.logbook.openSearchLogStore.apikey = <replace> #not used by default
     brooklyn.logbook.openSearchLogStore.verifySSl = false
     */
    public final static String BASE_NAME_OPEN_SEARCH_LOG_STORE = BASE_NAME_LOGBOOK + ".openSearchLogStore";

    public final static ConfigKey<String> LOGBOOK_LOG_STORE_HOST = ConfigKeys.newStringConfigKey(
            BASE_NAME_OPEN_SEARCH_LOG_STORE + ".host", "Log store host");

    public final static ConfigKey<String> LOGBOOK_LOG_STORE_INDEX = ConfigKeys.newStringConfigKey(
            BASE_NAME_OPEN_SEARCH_LOG_STORE + ".index", "Log store index");

    public final static ConfigKey<String> LOGBOOK_LOG_STORE_USER = ConfigKeys.newStringConfigKey(
            BASE_NAME_OPEN_SEARCH_LOG_STORE + ".user", "User name");

    public final static ConfigKey<String> LOGBOOK_LOG_STORE_PASS = ConfigKeys.newStringConfigKey(
            BASE_NAME_OPEN_SEARCH_LOG_STORE + ".password", "User password");

    public final static ConfigKey<String> LOGBOOK_LOG_STORE_APIKEY = ConfigKeys.newStringConfigKey(
            BASE_NAME_OPEN_SEARCH_LOG_STORE + ".apikey", "API key");

    public final static ConfigKey<Boolean> LOGBOOK_LOG_STORE_VERIFY_SSL = ConfigKeys.newBooleanConfigKey(
            BASE_NAME_OPEN_SEARCH_LOG_STORE + ".verifySSl", "Verify SSL", true);

    private final ManagementContext mgmt;
    CloseableHttpClient httpClient;
    private String host;
    private String user;
    private String password;
    private String apiKey;
    private Boolean verifySsl;
    private String indexName;

    public OpenSearchLogStore(ManagementContext mgmt) {
        this.mgmt = mgmt;
        initialize();

        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
        if (!verifySsl) {

            final SSLContext sslContext;
            try {
                sslContext = SSLContextBuilder
                        .create()
                        .loadTrustMaterial(new TrustSelfSignedStrategy())
                        .build();
                HostnameVerifier allowAllHosts = new NoopHostnameVerifier();
                SSLConnectionSocketFactory connectionFactory = new SSLConnectionSocketFactory(sslContext, allowAllHosts);
                httpClientBuilder.setSSLSocketFactory(connectionFactory);
            } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
                Exceptions.propagate(e);
            }
        }
        if (Strings.isNonBlank(apiKey)) {
            httpClientBuilder.setDefaultHeaders(ImmutableList.of(new BasicHeader(HttpHeaders.AUTHORIZATION, "ApiKey " + apiKey)));
        } else {
            httpClientBuilder.setDefaultCredentialsProvider(buildBasicCredentialsProvider());
        }

        httpClient = httpClientBuilder.build();
    }

    private CredentialsProvider buildBasicCredentialsProvider() {
        URL url = null;
        try {
            url = new URL(host);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("The provided host OpenSeatch host ulr is not valid: " + host);
        }
        HttpHost httpHost = new HttpHost(url.getHost(), url.getPort());
        CredentialsProvider provider = new BasicCredentialsProvider();
        provider.setCredentials(
                new AuthScope(httpHost),
                new UsernamePasswordCredentials(user, password)
        );
        return provider;
    }

    private void initialize() {
        this.host = mgmt.getConfig().getConfig(LOGBOOK_LOG_STORE_HOST);
        Preconditions.checkNotNull(host, "OpenSearch host must be set: " + LOGBOOK_LOG_STORE_HOST.getName());

        this.user = mgmt.getConfig().getConfig(LOGBOOK_LOG_STORE_USER);
        this.indexName = mgmt.getConfig().getConfig(LOGBOOK_LOG_STORE_INDEX);
        this.password = mgmt.getConfig().getConfig(LOGBOOK_LOG_STORE_PASS); //todojd this is not secure
        this.apiKey = mgmt.getConfig().getConfig(LOGBOOK_LOG_STORE_APIKEY);
        this.verifySsl = mgmt.getConfig().getConfig(LOGBOOK_LOG_STORE_VERIFY_SSL);
    }

    @Override
    public List<String> query(LogBookQueryParams params) {
        return null;
    }

    @Override
    public List<String> getEntries(Integer from, Integer numberOfItems) throws IOException {
        HttpPost request = new HttpPost(host + "/" + indexName + "/_search");
        request.addHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        request.setEntity(new StringEntity(getJSONQuery(from, numberOfItems)));
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            BrooklynOpenSearchModel jsonResponse = new ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .readValue(response.getEntity().getContent(), BrooklynOpenSearchModel.class);
            return jsonResponse.hits.hits.stream()
                    .map(BrooklynOpenSearchModel.OpenSearchHit::getSource)
                    .map(BrooklynOpenSearchModel.BrooklynHit::toJsonString)
                    .collect(Collectors.toList());
        }
    }

    private String getJSONQuery(Integer from, Integer numberOfItems) {
        int initialEntry = from >= 0 ? from : -from;
        String order = from > 0 ? "asc" : "desc";

        return new StringBuilder()
                .append("{")
                .append("\"from\":").append(initialEntry).append(",")
                .append("\"size\":").append(numberOfItems).append(",")
                .append("\"sort\": { \"timestamp\": \"").append(order).append("\"},")
                .append("  \"query\": {\"match_all\": {}}")
                .append("}").toString();
    }


}
