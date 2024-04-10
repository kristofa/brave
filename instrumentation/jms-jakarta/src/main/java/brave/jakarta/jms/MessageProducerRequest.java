/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jakarta.jms;

import brave.Span.Kind;
import brave.internal.Nullable;
import brave.messaging.ProducerRequest;
import brave.propagation.Propagation.RemoteGetter;
import brave.propagation.Propagation.RemoteSetter;
import jakarta.jms.Destination;
import jakarta.jms.Message;

import static brave.jakarta.jms.MessageProperties.getPropertyIfString;
import static brave.jakarta.jms.MessageProperties.setStringProperty;

// intentionally not yet public until we add tag parsing functionality
final class MessageProducerRequest extends ProducerRequest {
  static final RemoteGetter<MessageProducerRequest> GETTER =
      new RemoteGetter<MessageProducerRequest>() {
        @Override public Kind spanKind() {
          return Kind.PRODUCER;
        }

        @Override public String get(MessageProducerRequest request, String name) {
          return getPropertyIfString(request.delegate, name);
        }

        @Override public String toString() {
          return "Message::getStringProperty";
        }
      };

  static final RemoteSetter<MessageProducerRequest> SETTER =
      new RemoteSetter<MessageProducerRequest>() {
        @Override public Kind spanKind() {
          return Kind.PRODUCER;
        }

        @Override public void put(MessageProducerRequest request, String name, String value) {
          setStringProperty(request.delegate, name, value);
        }

        @Override public String toString() {
          return "Message::setStringProperty";
        }
      };

  final Message delegate;
  @Nullable final Destination destination;

  MessageProducerRequest(Message delegate, @Nullable Destination destination) {
    if (delegate == null) throw new NullPointerException("delegate == null");
    this.delegate = delegate;
    this.destination = destination;
  }

  @Override public Kind spanKind() {
    return Kind.PRODUCER;
  }

  @Override public Object unwrap() {
    return delegate;
  }

  @Override public String operation() {
    return "send";
  }

  @Override public String channelKind() {
    return MessageParser.channelKind(destination);
  }

  @Override public String channelName() {
    return MessageParser.channelName(destination);
  }

  @Override public String messageId() {
    return MessageParser.messageId(delegate);
  }
}
