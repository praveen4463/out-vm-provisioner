package com.zylitics.wzgp.test.util;

import java.io.Serializable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Clock that allows addition of an offset at any time based on Clock.OffsetClock
 * @author Praveen Tiwari
 *
 */
public class FlexibleOffsetClock extends Clock implements Serializable {
  
  private static final long serialVersionUID = 886831347029769678L;

  private final Clock baseClock;
  private Duration offset;
  
  public FlexibleOffsetClock() {
    baseClock = Clock.systemDefaultZone();
  }
  
  public FlexibleOffsetClock(Clock baseClock) {
    this.baseClock = baseClock;
  }
  
  public FlexibleOffsetClock(Clock baseClock, Duration offset) {
    this.baseClock = baseClock;
    this.offset = offset;
  }
  
  public void setOffset(Duration offset) {
    this.offset = offset;
  }
  
  @Override
  public ZoneId getZone() {
    return baseClock.getZone();
  }
  
  @Override
  public Clock withZone(ZoneId zone) {
    if (zone.equals(baseClock.getZone())) {
      return this;
    }
    return new FlexibleOffsetClock(baseClock.withZone(zone));
  }
  
  @Override
  public long millis() {
    return Math.addExact(baseClock.millis(), offset.toMillis());
  }
  
  @Override
  public Instant instant() {
    return baseClock.instant().plus(offset);
  }
  
  @Override
  public boolean equals(Object obj) {
    if (obj instanceof FlexibleOffsetClock) {
      FlexibleOffsetClock other = (FlexibleOffsetClock) obj;
        return baseClock.equals(other.baseClock) && offset.equals(other.offset);
    }
    return false;
  }
  
  @Override
  public int hashCode() {
    return baseClock.hashCode() ^ offset.hashCode();
  }
  
  @Override
  public String toString() {
    return "FlexibleOffsetClock[" + baseClock + "," + offset + "]";
  }
}
