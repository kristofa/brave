package brave.propagation;

import org.junit.Test;

import static brave.propagation.B3SingleFormat.parseB3SingleFormat;
import static org.assertj.core.api.Assertions.assertThat;

public class B3SingleFormatTest {
  String traceId = "0000000000000001";
  String parentId = "0000000000000002";
  String spanId = "0000000000000003";

  @Test public void parseB3SingleFormat_idsNotYetSampled() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId).context())
        .isEqualToComparingFieldByField(
            TraceContext.newBuilder().traceId(1).spanId(3).build()
        );
  }

  @Test public void parseB3SingleFormat_idsNotYetSampled128() {
    assertThat(parseB3SingleFormat(traceId + traceId + "-" + spanId).context())
        .isEqualToComparingFieldByField(
            TraceContext.newBuilder().traceIdHigh(1).traceId(1).spanId(3).build()
        );
  }

  @Test public void parseB3SingleFormat_idsUnsampled() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-0").context())
        .isEqualToComparingFieldByField(
            TraceContext.newBuilder().traceId(1).spanId(3).sampled(false).build()
        );
  }

  @Test public void parseB3SingleFormat_parent_unsampled() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-0-" + parentId).context())
        .isEqualToComparingFieldByField(
            TraceContext.newBuilder().traceId(1).parentId(2).spanId(3).sampled(false).build()
        );
  }

  @Test public void parseB3SingleFormat_parent_debug() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-1-" + parentId + "-1").context())
        .isEqualToComparingFieldByField(
            TraceContext.newBuilder().traceId(1).parentId(2).spanId(3).debug(true).build()
        );
  }

  @Test public void parseB3SingleFormat_idsWithDebug() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-1-1").context())
        .isEqualToComparingFieldByField(
            TraceContext.newBuilder().traceId(1).spanId(3).debug(true).build()
        );
  }

  @Test public void parseB3SingleFormat_idsUnsampled_with_redundant_debug() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-0-0").context())
        .isEqualToComparingFieldByField(
            TraceContext.newBuilder().traceId(1).spanId(3).sampled(false).build()
        );
  }

  @Test public void parseB3SingleFormat_sampledFalse() {
    assertThat(parseB3SingleFormat("0"))
        .isEqualTo(TraceContextOrSamplingFlags.NOT_SAMPLED);
  }

  @Test public void parseB3SingleFormat_sampled() {
    assertThat(parseB3SingleFormat("1"))
        .isEqualTo(TraceContextOrSamplingFlags.SAMPLED);
  }

  @Test public void parseB3SingleFormat_sampled_redundant() {
    assertThat(parseB3SingleFormat("1-0"))
        .isEqualTo(TraceContextOrSamplingFlags.SAMPLED);
  }

  @Test public void parseB3SingleFormat_debug() {
    assertThat(parseB3SingleFormat("1-1"))
        .isEqualTo(TraceContextOrSamplingFlags.DEBUG);
  }

  @Test public void parseB3SingleFormat_malformed() {
    assertThat(parseB3SingleFormat("not-a-tumor"))
        .isNull(); // instead of raising exception
  }

  @Test public void parseB3SingleFormat_malformed_uuid() {
    assertThat(parseB3SingleFormat("b970dafd-0d95-40aa-95d8-1d8725aebe40"))
        .isNull(); // instead of raising exception
  }

  @Test public void parseB3SingleFormat_truncated() {
    assertThat(parseB3SingleFormat("-"))
        .isNull();
    assertThat(parseB3SingleFormat("-1"))
        .isNull();
    assertThat(parseB3SingleFormat("1-"))
        .isNull();
    assertThat(parseB3SingleFormat(traceId))
        .isNull();
    assertThat(parseB3SingleFormat(traceId + "-"))
        .isNull();
    assertThat(parseB3SingleFormat(traceId.substring(0, 15) + "-" + spanId))
        .isNull();
    assertThat(parseB3SingleFormat(traceId + "-" + spanId.substring(0, 15)))
        .isNull();
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-"))
        .isNull();
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-1-"))
        .isNull();
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-" + parentId.substring(0, 15)))
        .isNull();
  }

  @Test public void parseB3SingleFormat_tooBig() {
    assertThat(parseB3SingleFormat(traceId + "-" + spanId + "-" + traceId + traceId))
        .isNull();
  }
}
