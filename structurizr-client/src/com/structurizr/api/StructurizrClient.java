package com.structurizr.api;

import com.structurizr.Workspace;
import com.structurizr.io.json.JsonReader;
import com.structurizr.io.json.JsonWriter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Base64;
import java.util.Properties;

public class StructurizrClient {

    private static final Log log = LogFactory.getLog(StructurizrClient.class);

    public static final String STRUCTURIZR_API_URL = "structurizr.api.url";
    public static final String STRUCTURIZR_API_KEY = "structurizr.api.key";
    public static final String STRUCTURIZR_API_SECRET = "structurizr.api.secret";

    private static final String WORKSPACE_PATH = "/workspace/";

    private String url;
    private String apiKey;
    private String apiSecret;

    /**
     * Creates a new Structurizr client based upon configuration in a structurizr.properties file
     * on the classpath with the following name-value pairs:
     * - structurizr.api.url
     * - structurizr.api.key
     * - structurizr.api.secret
     */
    public StructurizrClient() {
        try {
            Properties properties = new Properties();
            InputStream in = StructurizrClient.class.getClassLoader().getResourceAsStream("structurizr.properties");
            if (in != null) {
                properties.load(in);
                setUrl(properties.getProperty(STRUCTURIZR_API_URL));
                this.apiKey = properties.getProperty(STRUCTURIZR_API_KEY);
                this.apiSecret = properties.getProperty(STRUCTURIZR_API_SECRET);
                in.close();
            }
        } catch (Exception e) {
            log.error(e);
        }
    }

    /**
     * Creates a new Structurizr client with the specified API URL, key and secret.
     */
    public StructurizrClient(String url, String apiKey, String apiSecret) {
        setUrl(url);
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        if (url != null) {
            if (url.endsWith("/")) {
                this.url = url.substring(0, url.length() - 1);
            } else {
                this.url = url;
            }
        }
    }

    /**
     * Gets the workspace with the given ID.
     *
     * @param workspaceId   the ID of your workspace
     * @return a Workspace instance
     * @throws Exception    if there are problems related to the network, authorization, JSON deserialization, etc
     */
    public Workspace getWorkspace(long workspaceId) throws Exception {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(url + WORKSPACE_PATH + workspaceId);
        addHeaders(httpGet, "", "");
        debugRequest(httpGet);

        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
            debugResponse(response);

            String json = EntityUtils.toString(response.getEntity());
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                return new JsonReader().read(new StringReader(json));
            } else {
                ApiError apiError = ApiError.parse(json);
                throw new StructurizrClientException(apiError.getMessage());
            }
        }
    }

    /**
     * Updates the given workspace.
     *
     * @param workspace     the workspace instance to update
     * @throws Exception    if there are problems related to the network, authorization, JSON serialization, etc
     */
    public void putWorkspace(Workspace workspace) throws Exception {
        if (workspace == null) {
            throw new IllegalArgumentException("A workspace must be supplied");
        } else if (workspace.getId() <= 0) {
            throw new IllegalArgumentException("The workspace ID must be set");
        }

        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPut httpPut = new HttpPut(url + WORKSPACE_PATH + workspace.getId());

        JsonWriter jsonWriter = new JsonWriter(true);
        StringWriter stringWriter = new StringWriter();
        jsonWriter.write(workspace, stringWriter);

        StringEntity stringEntity = new StringEntity(stringWriter.toString(), ContentType.APPLICATION_JSON);
        httpPut.setEntity(stringEntity);
        addHeaders(httpPut, EntityUtils.toString(stringEntity), ContentType.APPLICATION_JSON.toString());
        debugRequest(httpPut);

        try (CloseableHttpResponse response = httpClient.execute(httpPut)) {
            debugResponse(response);
            log.info(EntityUtils.toString(response.getEntity()));
        }
    }

    /**
     * Fetches the workspace with the given workspaceId from the server and merges its layout information with
     * the given workspace. All models from the the new workspace are taken, only the old layout information is preserved.
     *
     * @param workspaceId   the ID of your workspace
     * @param workspace     the new workspace
     * @throws Exception    if you are not allowed to update the workspace with the given ID or there are any network troubles
     */
    public void mergeWorkspace(long workspaceId, Workspace workspace) throws Exception {
        Workspace currentWorkspace = getWorkspace(workspaceId);
        if (currentWorkspace != null) {
            workspace.getViews().copyLayoutInformationFrom(currentWorkspace.getViews());
        }
        workspace.setId(workspaceId);
        putWorkspace(workspace);
    }

    private void debugRequest(HttpRequestBase httpRequest) {
        log.debug(httpRequest.getMethod() + " " + httpRequest.getURI().getPath());
        Header[] headers = httpRequest.getAllHeaders();
        for (Header header : headers) {
            log.debug(header.getName() + ": " + header.getValue());
        }
    }

    private void debugResponse(CloseableHttpResponse response) {
        log.info(response.getStatusLine());
    }

    private void addHeaders(HttpRequestBase httpRequest, String content, String contentType) throws Exception {
        String httpMethod = httpRequest.getMethod();
        String path = httpRequest.getURI().getPath();
        String contentMd5 = new Md5Digest().generate(content);
        String nonce = "" + System.currentTimeMillis();

        HashBasedMessageAuthenticationCode hmac = new HashBasedMessageAuthenticationCode(apiSecret);
        HmacContent hmacContent = new HmacContent(httpMethod, path, contentMd5, contentType, nonce);
        httpRequest.addHeader(HttpHeaders.AUTHORIZATION, new HmacAuthorizationHeader(apiKey, hmac.generate(hmacContent.toString())).format());
        httpRequest.addHeader(HttpHeaders.NONCE, nonce);
        httpRequest.addHeader(HttpHeaders.CONTENT_MD5, Base64.getEncoder().encodeToString(contentMd5.getBytes()));
        httpRequest.addHeader(HttpHeaders.CONTENT_TYPE, contentType);
    }

}