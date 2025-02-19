/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.httpclient5;

import brave.Tracing;
import brave.http.HttpTracing;
import brave.propagation.CurrentTraceContext;
import org.apache.hc.client5.http.impl.ChainElement;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.H2AsyncClientBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;

import static org.apache.hc.client5.http.impl.ChainElement.PROTOCOL;

public class HttpClient5Tracing {

  final HttpTracing httpTracing;

  final CurrentTraceContext currentTraceContext;

  final TraceContextOpenScopeInterceptor openInterceptor;

  final TraceContextCloseScopeInterceptor closeInterceptor;

  HttpClient5Tracing(HttpTracing httpTracing) {
    if (httpTracing == null) throw new NullPointerException("HttpTracing == null");
    this.httpTracing = httpTracing;
    this.openInterceptor =
      new TraceContextOpenScopeInterceptor(httpTracing.tracing().currentTraceContext());
    this.closeInterceptor = new TraceContextCloseScopeInterceptor();
    this.currentTraceContext = httpTracing.tracing().currentTraceContext();
  }

  public static HttpClient5Tracing newBuilder(Tracing tracing) {
    return new HttpClient5Tracing(HttpTracing.create(tracing));
  }

  public static HttpClient5Tracing newBuilder(HttpTracing httpTracing) {
    return new HttpClient5Tracing(httpTracing);
  }

  public HttpClientBuilder addTracingToBuilder(HttpClientBuilder builder) {
      if (builder == null) throw new NullPointerException("HttpClientBuilder == null");
      builder.addExecInterceptorBefore(ChainElement.MAIN_TRANSPORT.name(),
          HandleSendHandler.class.getName(),
          new HandleSendHandler(httpTracing));
      builder.addExecInterceptorBefore(ChainElement.PROTOCOL.name(),
          HandleReceiveHandler.class.getName(),
          new HandleReceiveHandler(httpTracing));
      return builder;
  }

  public CloseableHttpClient build(HttpClientBuilder builder) {
    return addTracingToBuilder(builder).build();
  }

  public CloseableHttpAsyncClient build(HttpAsyncClientBuilder builder) {
    if (builder == null) throw new NullPointerException("HttpAsyncClientBuilder == null");
    builder.addExecInterceptorBefore(PROTOCOL.name(), AsyncHandleSendHandler.class.getName(),
      new AsyncHandleSendHandler(httpTracing));
    builder.addRequestInterceptorFirst(openInterceptor);
    builder.addRequestInterceptorLast(closeInterceptor);
    builder.addResponseInterceptorFirst(openInterceptor);
    builder.addResponseInterceptorLast(closeInterceptor);
    return new TracingHttpAsyncClient(builder.build(), currentTraceContext);
  }

  public CloseableHttpAsyncClient build(H2AsyncClientBuilder builder) {
    if (builder == null) throw new NullPointerException("H2AsyncClientBuilder == null");
    builder.addExecInterceptorBefore(PROTOCOL.name(), AsyncHandleSendHandler.class.getName(),
      new AsyncHandleSendHandler(httpTracing));
    builder.addRequestInterceptorFirst(openInterceptor);
    builder.addRequestInterceptorLast(closeInterceptor);
    builder.addResponseInterceptorFirst(openInterceptor);
    builder.addResponseInterceptorLast(closeInterceptor);
    return new TracingHttpAsyncClient(builder.build(), currentTraceContext);
  }
}
