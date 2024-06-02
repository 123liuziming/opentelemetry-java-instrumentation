/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v12_0;

import static java.util.logging.Level.FINE;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class AbstractJettyClient12Test extends AbstractHttpClientTest<Request> {

  private static final Logger logger = Logger.getLogger(AbstractJettyClient12Test.class.getName());

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  protected abstract HttpClient createStandardClient();

  protected abstract HttpClient createHttpsClient(SslContextFactory.Client sslContextFactory);

  protected HttpClient client = createStandardClient();

  protected HttpClient httpsClient;

  Request jettyRequest = null;

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    try {
      // disable redirect tests
      optionsBuilder.disableTestRedirects();
      // jetty 12 does not support to reuse request
      // use request.send() twice will block the program infinitely
      optionsBuilder.disableTestReusedRequest();
      // start the main Jetty HttpClient and a https client
      client.setConnectTimeout(CONNECTION_TIMEOUT.toMillis());
      client.start();

      SslContextFactory.Client tlsCtx = new SslContextFactory.Client();
      httpsClient = createHttpsClient(tlsCtx);
      httpsClient.setFollowRedirects(false);
      httpsClient.start();
    } catch (Throwable t) {
      logger.log(FINE, t.getMessage(), t);
    }
  }

  @Override
  public Request buildRequest(String method, URI uri, Map<String, String> headers)
      throws Exception {
    HttpClient theClient = Objects.equals(uri.getScheme(), "https") ? httpsClient : client;

    Request request = theClient.newRequest(uri);
    request.agent("Jetty");

    request.method(method);
    request.timeout(READ_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

    jettyRequest = request;

    return request;
  }

  @Override
  public int sendRequest(Request request, String method, URI uri, Map<String, String> headers)
      throws ExecutionException, InterruptedException, TimeoutException {
    headers.forEach((k, v) -> request.headers(httpFields -> httpFields.put(new HttpField(k, v))));

    ContentResponse response = request.send();

    return response.getStatus();
  }

  @Override
  public void sendRequestWithCallback(
      Request request,
      String method,
      URI uri,
      Map<String, String> headers,
      HttpClientResult requestResult)
      throws Exception {
    JettyClientListener jcl = new JettyClientListener();

    request.onRequestFailure(jcl);
    request.onResponseFailure(jcl);
    headers.forEach((k, v) -> request.headers(httpFields -> httpFields.put(new HttpField(k, v))));

    request.send(
        result -> {
          if (jcl.failure != null) {
            requestResult.complete(jcl.failure);
            return;
          }

          requestResult.complete(result.getResponse().getStatus());
        });
  }

  private static class JettyClientListener
      implements Request.FailureListener, Response.FailureListener {
    volatile Throwable failure;

    @Override
    public void onFailure(Request requestF, Throwable failure) {
      this.failure = failure;
    }

    @Override
    public void onFailure(Response responseF, Throwable failure) {
      this.failure = failure;
    }
  }
}