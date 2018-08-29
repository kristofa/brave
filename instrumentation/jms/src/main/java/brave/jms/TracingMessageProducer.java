package brave.jms;

import brave.Span;
import brave.Tracer.SpanInScope;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import javax.jms.CompletionListener;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueSender;
import javax.jms.Topic;
import javax.jms.TopicPublisher;

class TracingMessageProducer<M extends MessageProducer> extends TracingProducer<M, Message>
    implements MessageProducer {

  static MessageProducer create(MessageProducer delegate, JmsTracing jmsTracing) {
    if (delegate == null) throw new NullPointerException("messageProducer == null");
    if (delegate instanceof TracingMessageProducer) return delegate;
    if (delegate instanceof QueueSender) {
      return TracingQueueSender.create((QueueSender) delegate, jmsTracing);
    }
    if (delegate instanceof TopicPublisher) {
      return TracingTopicPublisher.create((TopicPublisher) delegate, jmsTracing);
    }
    return new TracingMessageProducer<>(delegate, jmsTracing);
  }

  TracingMessageProducer(M delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
  }

  @Override void addB3SingleHeader(TraceContext context, Message message) {
    JmsTracing.addB3SingleHeader(context, message);
  }

  @Override void clearPropagationHeaders(Message message) {
    PropertyFilter.MESSAGE.filterProperties(message, jmsTracing.propagationKeys);
  }

  @Override TraceContextOrSamplingFlags extractAndClearMessage(Message message) {
    return jmsTracing.extractAndClearMessage(message);
  }

  @Override Destination destination(Message message) {
    try {
      Destination result = message.getJMSDestination();
      if (result != null) return result;
      return delegate.getDestination();
    } catch (JMSException ignored) {
      // don't crash on wonky exceptions!
    }
    return null;
  }

  @Override public void setDisableMessageID(boolean value) throws JMSException {
    delegate.setDisableMessageID(value);
  }

  @Override public boolean getDisableMessageID() throws JMSException {
    return delegate.getDisableMessageID();
  }

  @Override public void setDisableMessageTimestamp(boolean value) throws JMSException {
    delegate.setDisableMessageTimestamp(value);
  }

  @Override public boolean getDisableMessageTimestamp() throws JMSException {
    return delegate.getDisableMessageTimestamp();
  }

  @Override public void setDeliveryMode(int deliveryMode) throws JMSException {
    delegate.setDeliveryMode(deliveryMode);
  }

  @Override public int getDeliveryMode() throws JMSException {
    return delegate.getDeliveryMode();
  }

  @Override public void setPriority(int defaultPriority) throws JMSException {
    delegate.setPriority(defaultPriority);
  }

  @Override public int getPriority() throws JMSException {
    return delegate.getPriority();
  }

  @Override public void setTimeToLive(long timeToLive) throws JMSException {
    delegate.setTimeToLive(timeToLive);
  }

  @Override public long getTimeToLive() throws JMSException {
    return delegate.getTimeToLive();
  }

  /* @Override JMS 2.0 method: Intentionally no override to ensure JMS 1.1 works! */
  @JMS2_0 public void setDeliveryDelay(long deliveryDelay) throws JMSException {
    delegate.setDeliveryDelay(deliveryDelay);
  }

  /* @Override JMS 2.0 method: Intentionally no override to ensure JMS 1.1 works! */
  @JMS2_0 public long getDeliveryDelay() throws JMSException {
    return delegate.getDeliveryDelay();
  }

  @Override public Destination getDestination() throws JMSException {
    return delegate.getDestination();
  }

  @Override public void close() throws JMSException {
    delegate.close();
  }

  @Override public void send(Message message) throws JMSException {
    Span span = createAndStartProducerSpan(null, message);
    SpanInScope ws = tracer.withSpanInScope(span); // animal-sniffer mistakes this for AutoCloseable
    try {
      delegate.send(message);
    } catch (RuntimeException | JMSException | Error e) {
      span.error(e);
      throw e;
    } finally {
      ws.close();
      span.finish();
    }
  }

  @Override public void send(Message message, int deliveryMode, int priority, long timeToLive)
      throws JMSException {
    Span span = createAndStartProducerSpan(null, message);
    SpanInScope ws = tracer.withSpanInScope(span); // animal-sniffer mistakes this for AutoCloseable
    try {
      delegate.send(message, deliveryMode, priority, timeToLive);
    } catch (RuntimeException | JMSException | Error e) {
      span.error(e);
      throw e;
    } finally {
      ws.close();
      span.finish();
    }
  }

  enum SendDestination {
    DESTINATION {
      @Override void apply(MessageProducer producer, Destination destination, Message message)
          throws JMSException {
        producer.send(destination, message);
      }
    },
    QUEUE {
      @Override void apply(MessageProducer producer, Destination destination, Message message)
          throws JMSException {
        ((QueueSender) producer).send((Queue) destination, message);
      }
    },
    TOPIC {
      @Override void apply(MessageProducer producer, Destination destination, Message message)
          throws JMSException {
        ((TopicPublisher) producer).publish((Topic) destination, message);
      }
    };

    abstract void apply(MessageProducer producer, Destination destination, Message message)
        throws JMSException;
  }

  @Override public void send(Destination destination, Message message) throws JMSException {
    send(SendDestination.DESTINATION, destination, message);
  }

  void send(SendDestination sendDestination, Destination destination, Message message)
      throws JMSException {
    Span span = createAndStartProducerSpan(destination, message);
    SpanInScope ws = tracer.withSpanInScope(span); // animal-sniffer mistakes this for AutoCloseable
    try {
      sendDestination.apply(delegate, destination, message);
    } catch (RuntimeException | JMSException | Error e) {
      span.error(e);
      throw e;
    } finally {
      ws.close();
      span.finish();
    }
  }

  @Override
  public void send(Destination destination, Message message, int deliveryMode, int priority,
      long timeToLive) throws JMSException {
    Span span = createAndStartProducerSpan(destination, message);
    SpanInScope ws = tracer.withSpanInScope(span); // animal-sniffer mistakes this for AutoCloseable
    try {
      delegate.send(destination, message, deliveryMode, priority, timeToLive);
    } catch (RuntimeException | JMSException | Error e) {
      span.error(e);
      throw e;
    } finally {
      ws.close();
      span.finish();
    }
  }

  /* @Override JMS 2.0 method: Intentionally no override to ensure JMS 1.1 works! */
  @JMS2_0
  public void send(Message message, CompletionListener completionListener) throws JMSException {
    Span span = createAndStartProducerSpan(null, message);
    SpanInScope ws = tracer.withSpanInScope(span); // animal-sniffer mistakes this for AutoCloseable
    try {
      delegate.send(message, TracingCompletionListener.create(completionListener, span, current));
    } catch (RuntimeException | JMSException | Error e) {
      span.error(e);
      span.finish();
      throw e;
    } finally {
      ws.close();
    }
  }

  /* @Override JMS 2.0 method: Intentionally no override to ensure JMS 1.1 works! */
  @JMS2_0 public void send(Message message, int deliveryMode, int priority, long timeToLive,
      CompletionListener completionListener) throws JMSException {
    Span span = createAndStartProducerSpan(null, message);
    completionListener = TracingCompletionListener.create(completionListener, span, current);
    SpanInScope ws = tracer.withSpanInScope(span); // animal-sniffer mistakes this for AutoCloseable
    try {
      delegate.send(message, deliveryMode, priority, timeToLive, completionListener);
    } catch (RuntimeException | JMSException | Error e) {
      span.error(e);
      span.finish();
      throw e;
    } finally {
      ws.close();
    }
  }

  /* @Override JMS 2.0 method: Intentionally no override to ensure JMS 1.1 works! */
  @JMS2_0 public void send(Destination destination, Message message,
      CompletionListener completionListener) throws JMSException {
    Span span = createAndStartProducerSpan(destination, message);
    completionListener = TracingCompletionListener.create(completionListener, span, current);
    SpanInScope ws = tracer.withSpanInScope(span); // animal-sniffer mistakes this for AutoCloseable
    try {
      delegate.send(destination, message, completionListener);
    } catch (RuntimeException | JMSException | Error e) {
      span.error(e);
      span.finish();
      throw e;
    } finally {
      ws.close();
    }
  }

  /* @Override JMS 2.0 method: Intentionally no override to ensure JMS 1.1 works! */
  @JMS2_0 public void send(Destination destination, Message message, int deliveryMode, int priority,
      long timeToLive, CompletionListener completionListener) throws JMSException {
    Span span = createAndStartProducerSpan(destination, message);
    completionListener = TracingCompletionListener.create(completionListener, span, current);
    SpanInScope ws = tracer.withSpanInScope(span); // animal-sniffer mistakes this for AutoCloseable
    try {
      delegate.send(destination, message, deliveryMode, priority, timeToLive, completionListener);
    } catch (RuntimeException | JMSException | Error e) {
      span.error(e);
      span.finish();
      throw e;
    } finally {
      ws.close();
    }
  }
}
