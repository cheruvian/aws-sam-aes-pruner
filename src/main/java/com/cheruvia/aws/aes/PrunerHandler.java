package com.cheruvia.aws.aes;

import com.amazonaws.auth.AWS4Signer;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.http.AWSRequestSigningApacheInterceptor;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexAction;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class PrunerHandler implements RequestStreamHandler {
    /**
     * Endpoint of your Elasticsearch cluster.
     */
    private static final String CLUSTER_ENDPOINT = System.getenv("CLUSTER_ENDPOINT");
    /**
     * Signing Service Name for Amazon Elasticsearch Service (es).
     */
    private static final String SERVICE_NAME = "es";
    /**
     * AWS Region which gets injected by Lambda (ex: us-east-1).
     */
    private static final String REGION = System.getenv("AWS_DEFAULT_REGION");
    /**
     * Number of days of indexes to keep in elasticsearch.
     */
    private static final int DAYS_TO_KEEP = 7;
    /**
     * Number of days of indexes to keep in elasticsearch.
     */
    private static final String CWL_PREFIX = "cwl-";
    /**
     * Date format for parsing index date strings.
     */
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy.MM.dd");

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
        final AWS4Signer signer = new AWS4Signer();
        signer.setServiceName(SERVICE_NAME);
        signer.setRegionName(REGION);

        HttpRequestInterceptor interceptor =
                new AWSRequestSigningApacheInterceptor(SERVICE_NAME, signer, new DefaultAWSCredentialsProviderChain());

        RestClient lowLevelRestClient = RestClient
                .builder(HttpHost.create(CLUSTER_ENDPOINT))
                .setHttpClientConfigCallback(hacb -> hacb.addInterceptorLast(interceptor))
                .build();

        // You can use the high level REST client like so
//        RestHighLevelClient client =
//                new RestHighLevelClient(lowLevelRestClient);
//
//        client.index(
//                new IndexRequest()
//                        .index("cwl-2016.10.10")
//                        .type("type")
//                        .id("index")
//                        .source(new HashMap())
//        );

        Response response = lowLevelRestClient.performRequest("GET", "/_cat/indices");
        String responseBody = EntityUtils.toString(response.getEntity());
        List<String> responseIndexes = Arrays.asList(responseBody.split("\n"));

        final Date dateBoundary = new Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(DAYS_TO_KEEP));
        context.getLogger().log("Deleting indexes before " + dateBoundary);
        responseIndexes.stream()
                .map(this::getIndexNameFromCatLine)
                .forEach(indexName -> {
                    // Assumes you are using the standard CloudWatch Logs format for daily indices
                    // cwl-YYYY.MM.DD
                    try {
                        Date indexDate = DATE_FORMAT.parse(indexName.replace(CWL_PREFIX, ""));
                        context.getLogger().log(indexName + " was parsed to " + indexDate);
                        if (indexDate.before(dateBoundary)) {
                            context.getLogger().log(indexName + " Deleting");
                            try {
                                lowLevelRestClient.performRequest("DELETE", "/" + indexName);
                                context.getLogger().log(indexName + " Deleted");
                            } catch (IOException e) {
                                context.getLogger().log(indexName + " Failed to delete: " + e.getMessage());
                            }
                        } else {
                            context.getLogger().log(indexName + " Kept");
                        }
                    } catch (ParseException e) {
                        context.getLogger().log(indexName + " Skipped becaused date could not be parsed: " + e.getMessage());
                    }
                });
        lowLevelRestClient.close();
    }

    private String getIndexNameFromCatLine(final String catString) {
        return Arrays.stream(catString.split(("\\s+")))
                .map(String::trim)
                .collect(Collectors.toList())
                .get(2);
    }
}
