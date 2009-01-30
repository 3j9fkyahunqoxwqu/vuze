/*
 * Created on 14-Jun-2004
 * Created by Paul Gardner
 * Copyright (C) 2004, 2005, 2006 Aelitis, All Rights Reserved.
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
 * AELITIS, SAS au capital de 46,603.30 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 *
 */

package com.aelitis.net.upnp;

/**
 * @author parg
 *
 */

public interface 
UPnP 
{
	public UPnPRootDevice[]
	getRootDevices();
	
		/**
		 * resets by removing all root devices and then rediscovering them
		 *
		 */
	
	public void
	reset();
	
		/**
		 * scan for new
		 */
	
	public void
	search();
	
		/**
		 * Logs a message to all registered log listeners
		 * @param str
		 */
	
	public void
	log(
		String	str );
	
	public void
	addRootDeviceListener(
		UPnPListener	l );
	
	public void
	removeRootDeviceListener(
		UPnPListener	l );
	
	public void
	addLogListener(
		UPnPLogListener	l );
		
	public void
	removeLogListener(
		UPnPLogListener	l );
}
