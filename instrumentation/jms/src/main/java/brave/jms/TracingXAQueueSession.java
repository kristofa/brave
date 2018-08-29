package brave.jms;

import javax.jms.JMSException;
import javax.jms.QueueSession;
import javax.jms.XAQueueSession;

final class TracingXAQueueSession extends TracingXASession<XAQueueSession>
    implements XAQueueSession {
  TracingXAQueueSession(XAQueueSession delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
  }

  @Override public QueueSession getQueueSession() throws JMSException {
    return TracingQueueSession.create(delegate.getQueueSession(), jmsTracing);
  }
}
