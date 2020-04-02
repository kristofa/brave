/*
 * Copyright 2013-2020 The OpenZipkin Authors
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
package brave.internal;

import brave.propagation.TraceContext;
import java.util.Map;

/**
 * We need to retain propagation state extracted from headers. However, we don't know the trace
 * identifiers, yet. In order to resolve this ordering concern, we create an object to hold extra
 * state, and defer associating it with a span ID (via {@link PropagationFieldsFactory#decorate(TraceContext)}.
 *
 * <p>Implementations of this type should use copy-on-write semantics to prevent changes in a child
 * context from affecting its parent.
 */
public abstract class PropagationFields<K, V> {
  protected interface FieldConsumer<K, V> {
    // BiConsumer is Java 1.8+
    void accept(K key, @Nullable V value);
  }

  long traceId, spanId; // guarded by this

  /** Returns the value of the field with the specified key or null if not available */
  protected abstract V get(K key);

  /**
   * Replaces the value of the field with the specified key, ignoring if not a permitted field
   */
  protected abstract boolean put(K key, @Nullable V value);

  /** Invokes the consumer for every non-null field value */
  protected abstract void forEach(FieldConsumer<K, V> consumer);

  protected abstract boolean isEmpty();

  /**
   * For each field in the input replace the value if the key doesn't already exist.
   *
   * <p>Note: this does not synchronize internally as it is acting on newly constructed fields
   * not yet returned to a caller.
   */
  protected abstract void putAllIfAbsent(PropagationFields<K, V> parent);

  /** public for testing and default toString */
  public abstract Map<String, V> toMap();

  /** Fields are extracted before a context is created. We need to lazy set the context */
  final boolean tryToClaim(long traceId, long spanId) {
    synchronized (this) {
      if (this.traceId == 0L) {
        this.traceId = traceId;
        this.spanId = spanId;
        return true;
      }
      return this.traceId == traceId
        && this.spanId == spanId;
    }
  }

  @Override public String toString() {
    return getClass().getSimpleName() + toMap();
  }

  /** Returns the value of the field with the specified key or null if not available */
  public static <K, V> V get(TraceContext context, K key,
    Class<? extends PropagationFields<K, V>> type) {
    if (context == null) throw new NullPointerException("context == null");
    if (key == null) throw new NullPointerException("key == null");
    PropagationFields<K, V> fields = context.findExtra(type);
    return fields != null ? fields.get(key) : null;
  }

  /** Replaces the value of the field with the specified key, ignoring if not a permitted field */
  public static <K, V> void put(TraceContext context, K key, @Nullable V value,
    Class<? extends PropagationFields<K, V>> type) {
    if (context == null) throw new NullPointerException("context == null");
    if (key == null) throw new NullPointerException("key == null");
    PropagationFields<K, V> fields = context.findExtra(type);
    if (fields == null) return;
    fields.put(key, value);
  }

  protected static boolean equal(@Nullable Object a, @Nullable Object b) {
    return a == null ? b == null : a.equals(b); // Java 6 can't use Objects.equals()
  }
}
