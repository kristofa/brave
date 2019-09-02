/*
 * Copyright 2013-2019 The OpenZipkin Authors
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
package brave.sparkjava;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.http.HttpServerHandler;
import brave.http.HttpServerRequest;
import brave.http.HttpServerResponse;
import brave.http.HttpTracing;
import brave.propagation.TraceContext;
import brave.servlet.WrappedHttpServletRequest;
import brave.servlet.WrappedHttpServletResponse;
import javax.servlet.http.HttpServletRequest;
import spark.ExceptionHandler;
import spark.Filter;

public final class SparkTracing {
  // TODO: when https://github.com/perwendel/spark/issues/959 is resolved, add "http.route"
  public static SparkTracing create(Tracing tracing) {
    return new SparkTracing(HttpTracing.create(tracing));
  }

  public static SparkTracing create(HttpTracing httpTracing) {
    return new SparkTracing(httpTracing);
  }

  final Tracer tracer;
  final HttpServerHandler<HttpServerRequest, HttpServerResponse> handler;
  final TraceContext.Extractor<HttpServletRequest> extractor;

  SparkTracing(HttpTracing httpTracing) { // intentionally hidden constructor
    tracer = httpTracing.tracing().tracer();
    handler = HttpServerHandler.create(httpTracing);
    extractor = httpTracing.tracing().propagation().extractor(HttpServletRequest::getHeader);
  }

  public Filter before() {
    return (request, response) -> {
      Span span = handler.handleReceive(new WrappedHttpServletRequest(request.raw()));
      request.attribute(Tracer.SpanInScope.class.getName(), tracer.withSpanInScope(span));
    };
  }

  public Filter afterAfter() {
    return (request, response) -> {
      Span span = tracer.currentSpan();
      if (span == null) return;
      ((Tracer.SpanInScope) request.attribute(Tracer.SpanInScope.class.getName())).close();
      handler.handleSend(new WrappedHttpServletResponse(request.raw(), response.raw()), null, span);
    };
  }

  public ExceptionHandler exception(ExceptionHandler delegate) {
    return (exception, request, response) -> {
      Span span = tracer.currentSpan();
      if (span != null) {
        ((Tracer.SpanInScope) request.attribute(Tracer.SpanInScope.class.getName())).close();
        handler.handleSend(new WrappedHttpServletResponse(request.raw(), response.raw()), exception,
          span);
      }
      delegate.handle(exception, request, response);
    };
  }
}
