/*
 * Copyright 2013-2024 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.httpclient5;

import brave.Span;
import brave.Tracer;
import brave.Tracer.SpanInScope;
import brave.http.HttpClientHandler;
import brave.http.HttpClientRequest;
import brave.http.HttpClientResponse;
import brave.http.HttpRequest;
import brave.http.HttpTracing;
import brave.sampler.SamplerFunction;
import java.io.IOException;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecChain.Scope;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;

import static brave.internal.Throwables.propagateIfFatal;

class HandleReceiveHandler implements ExecChainHandler {
  final Tracer tracer;
  final SamplerFunction<HttpRequest> httpSampler;
  final HttpClientHandler<HttpClientRequest, HttpClientResponse> handler;

  HandleReceiveHandler(HttpTracing httpTracing) {
    this.tracer = httpTracing.tracing().tracer();
    this.httpSampler = httpTracing.clientRequestSampler();
    this.handler = HttpClientHandler.create(httpTracing);
  }

  @Override
  public ClassicHttpResponse execute(
    ClassicHttpRequest classicHttpRequest,
    ExecChain.Scope execChainScope,
    ExecChain execChain) throws IOException, HttpException {
    HttpHost targetHost = execChainScope.route.getTargetHost();
    HttpRequestWrapper request = new HttpRequestWrapper(classicHttpRequest, targetHost);
    Span span = tracer.nextSpan(httpSampler, request);

    HttpClientContext clientContext = execChainScope.clientContext;
    clientContext.setAttribute(Span.class.getName(), span);

    ClassicHttpResponse response = null;
    Throwable error = null;
    try (SpanInScope scope = tracer.withSpanInScope(span)) {
      return response = execChain.proceed(classicHttpRequest, execChainScope);
    } catch (Throwable e) {
      propagateIfFatal(e);
      error = e;
      throw e;
    } finally {
      if (HttpClientUtils.isLocalCached(clientContext, span)) {
        handler.handleSend(request, span);
        span.kind(null);
        clientContext.removeAttribute(Span.class.getName());
      }
      handler.handleReceive(new HttpResponseWrapper(response, request, error), span);
    }
  }
}
