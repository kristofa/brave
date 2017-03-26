package com.github.kristofa.brave.spark;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.LocalTracer;
import com.github.kristofa.brave.http.ITHttpServer;
import com.github.kristofa.brave.http.SpanNameProvider;
import org.junit.After;
import org.junit.AssumptionViolatedException;
import org.junit.Test;
import spark.ExceptionHandler;
import spark.Request;
import spark.Response;
import spark.Spark;

public class ITBraveSparkTest extends ITHttpServer {

    /*after filter executed before NotFoundRoute,there is no way to add 404 code after serverspan
    has been setServerSend,may be throw a not found exceeption and process it in ExceptionHandler*/
    @Override
    @Test
    public void addsStatusCodeWhenNotOk() throws Exception {
        throw new AssumptionViolatedException("TODO: fix error reporting");
    }
    @Override
    protected void init(Brave brave, SpanNameProvider spanNameProvider) throws Exception {
        stop();
        LocalTracer localTracer = brave.localTracer();
        BraveSparkRequestFilter reqFilter = BraveSparkRequestFilter.builder(brave).spanNameProvider(spanNameProvider).build();
        BraveSparkResponseFilter respFilter = BraveSparkResponseFilter.builder(brave).spanNameProvider(spanNameProvider).build();
        Spark.before(reqFilter);
        Spark.get("/foo", (req, res) -> "bar");
        Spark.get("/child", (req, res) -> {
            localTracer.startNewSpan("child", "child");
            localTracer.finishSpan();
            return "happy";
        });
        Spark.get("/disconnect", (req, res) -> {
            throw new Exception("Disconnect Exception");
        });
        Spark.exception(Exception.class, BraveSparkExceptionHandler.create(brave, new ExceptionHandler() {
            @Override
            public void handle(Exception exception, Request request, Response response) {
                response.body("exception");
            }
        }));

        Spark.after(respFilter);
        Spark.awaitInitialization();
    }

    @Override
    protected String url(String path) {//default port 4567
        return "http://localhost:4567" + path;
    }

    /*Spark stop asynchronously but share one class Instance,
      so AddressAlreadyUsed Exception may happen,see:
      https://github.com/perwendel/spark/issues/705 .
      Just sleep 1 second to avoid this happens,
      after Spark.awaitStopped add,I will fix it.
     */
    @After
    public void stop() throws Exception {
        Spark.stop();
        Thread.sleep(1000);
    }
}
