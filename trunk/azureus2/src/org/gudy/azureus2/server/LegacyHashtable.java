/*
 * LegacyHashtable.java
 *
 * Created on 31. August 2003, 18:16
 */

package org.gudy.azureus2.server;

import java.util.Hashtable;

/**
 *
 * @author  tobi
 */
public class LegacyHashtable extends Hashtable {
  
  /** Creates a new instance of LegacyHashtable */
  public LegacyHashtable() {
    super();
  }
  
  public Object get(Object key) {
    if (containsKey(key))
      return super.get(key);
    else
      return key;
  }
  
}
