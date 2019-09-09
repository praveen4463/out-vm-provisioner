package com.zylitics.wzgp.resource;

/**
 * All implementations must be strictly immutable for all accesses except spring container's.
 * @author Praveen Tiwari
 *
 */
public interface BuildProperty {

  String getBuildId();
}
