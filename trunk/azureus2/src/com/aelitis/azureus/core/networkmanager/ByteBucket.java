/*
 * Created on Jul 26, 2004
 * Created by Alon Rohter
 * Copyright (C) 2004 Aelitis, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * AELITIS, SARL au capital de 30,000 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 *
 */

package com.aelitis.azureus.core.networkmanager;

import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.SystemTime;

/**
 * Byte-bucket implementation based on the token bucket algorithm.
 * Buckets can be configured with a guaranteed normal rate, along with
 * a burst rate.
 */
public class ByteBucket {
  
  private int rate;
  private int burst_rate;
  private int avail_bytes;
  private long prev_update_time;
  
  
  /**
   * Create a new byte-bucket with the given byte fill (guaranteed) rate.
   * Burst rate is set to default 2X of given fill rate.
   * @param rate_bytes_per_sec
   */
  protected ByteBucket( int rate_bytes_per_sec ) {
    this.rate = rate_bytes_per_sec;
    burst_rate = rate_bytes_per_sec * 2; //allow for twice normal rate
    avail_bytes = 0; //start bucket empty
    prev_update_time = SystemTime.getCurrentTime();
  }
  
  
  /**
   * Get the number of bytes currently available for use.
   * @return number of free bytes
   */
  protected int getAvailableByteCount() {
    update_avail_byte_count();
    return avail_bytes;
  }
  
  
  /**
   * Update the bucket with the number of bytes just used.
   * @param bytes_used
   */
  protected void setBytesUsed( int bytes_used ) {
    avail_bytes -= bytes_used;
  }
  
  
  /**
   * Get the configured fill rate.
   * @return guaranteed rate in bytes per sec
   */
  protected int getRate() {  return rate;  }
  
  
  /**
   * Get the configured burst rate.
   * @return burst rate in bytes per sec
   */
  protected int getBurstRate() {  return burst_rate;  }
  
  
  /**
   * Set the current fill/guaranteed rate, with a burst rate of 2X the given rate.
   * @param rate_bytes_per_sec
   */
  protected void setRate( int rate_bytes_per_sec ) {
    setRate( rate_bytes_per_sec, rate_bytes_per_sec * 2 );
  }
  
  
  /**
   * Set the current fill/guaranteed rate, along with the burst rate.
   * @param rate_bytes_per_sec
   * @param burst_rate
   */
  protected void setRate( int rate_bytes_per_sec, int burst_rate ) {
    this.rate = rate_bytes_per_sec;
    this.burst_rate = burst_rate;
  }
  
  
  private void update_avail_byte_count() { //TODO: SystemTime.getCurrentTime() good enough ?
    long current_time = SystemTime.getCurrentTime();
    long time_diff = current_time - prev_update_time;
    int num_new_bytes = new Float((time_diff * rate) / 1000).intValue();    
    prev_update_time = current_time;
    avail_bytes += num_new_bytes;
    if( avail_bytes > burst_rate ) avail_bytes = burst_rate;
  }

}
