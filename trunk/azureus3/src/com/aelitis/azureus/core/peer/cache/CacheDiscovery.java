/*
 * Created on Feb 1, 2007
 * Created by Paul Gardner
 * Copyright (C) 2007 Aelitis, All Rights Reserved.
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
 * AELITIS, SAS au capital de 63.529,40 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 *
 */


package com.aelitis.azureus.core.peer.cache;

import java.net.InetAddress;
import java.util.*;

import org.gudy.azureus2.core3.ipfilter.BannedIp;
import org.gudy.azureus2.core3.ipfilter.IPFilterListener;
import org.gudy.azureus2.core3.ipfilter.IpFilter;
import org.gudy.azureus2.core3.ipfilter.IpFilterManagerFactory;
import org.gudy.azureus2.core3.torrent.TOTorrent;
import org.gudy.azureus2.core3.util.AEThread;
import org.gudy.azureus2.core3.util.Constants;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.HostNameToIPResolver;
import org.gudy.azureus2.core3.util.IPToHostNameResolver;
import org.gudy.azureus2.core3.util.IPToHostNameResolverListener;

import com.aelitis.azureus.core.peer.cache.cachelogic.CLCacheDiscovery;

public class 
CacheDiscovery 
{
	private static final IpFilter ip_filter = IpFilterManagerFactory.getSingleton().getIPFilter();

	private static final CacheDiscoverer[] discoverers = {
		
		new CLCacheDiscovery(),
	};
	
	private static Set	cache_ips = Collections.synchronizedSet(new HashSet());
	
	public static void
	initialise()
	{
		
		ip_filter.addListener(
			new IPFilterListener()
			{
				public boolean
				canIPBeBanned(
					String			ip )
				{
					return( canBan( ip ));
				}
				
				public void
				IPBanned(
					BannedIp		ip )
				{
				}
			});
		
		new AEThread( "CacheDiscovery:ban checker", true )
			{
				public void
				runSupport()
				{
					BannedIp[] bans = ip_filter.getBannedIps();
				
					for (int i=0;i<bans.length;i++){
						
						String	ip = bans[i].getIp();
						
						if ( !canBan( ip )){
							
							ip_filter.unban( ip );
						}
					}
				}
			}.start();
	}
	
	private static boolean
	canBan(
		final String	ip )
	{
		if ( cache_ips.contains( ip )){
			
			return( false );
		}
		
		try{
			InetAddress address = HostNameToIPResolver.syncResolve( ip );
			
			final String host_address = address.getHostAddress();
			
			if ( cache_ips.contains( host_address )){
	
				return( false );
			}
			
				// reverse lookups can be very slow
			
			IPToHostNameResolver.addResolverRequest(
				ip,
				new IPToHostNameResolverListener()
				{
					public void 
					IPResolutionComplete(
						String 		result, 
						boolean 	succeeded )
					{
						String[]	ok_domains = Constants.AZUREUS_DOMAINS;
						
						for (int i=0;i<ok_domains.length;i++){
							
							if ( result.endsWith( "." + ok_domains[i] )){
								
								cache_ips.add( host_address );
								
								ip_filter.unban( host_address, true );
							}
						}
					}
				});
		
			return( true );
			
		}catch( Throwable e ){
			
			Debug.printStackTrace( e );
			
			return( true );
		}
	}
	
	public static CachePeer[]
	lookup(
		TOTorrent	torrent )
	{
		CachePeer[]	res;
		
		if ( discoverers.length == 1 ){
			
			res = discoverers[0].lookup( torrent );
			
		}else{
		
			List	result = new ArrayList();
			
			for (int i=0;i<discoverers.length;i++){
				
				CachePeer[] peers = discoverers[i].lookup( torrent );
				
				for (int j=0;j<peers.length;j++){
					
					result.add( peers[i] );
				}
			}
			
			res = (CachePeer[])result.toArray( new CachePeer[result.size()]);
		}
		
		for (int i=0;i<res.length;i++){
			
			String	ip = res[i].getAddress().getHostAddress();
				
			cache_ips.add( ip );
			
			ip_filter.unban( ip );
		}
		
		return( res );
	}
	
	public static CachePeer
	categorisePeer(
		byte[]					peer_id,
		final InetAddress		ip,
		final int				port )
	{
		for (int i=0;i<discoverers.length;i++){
			
			CachePeer	cp = discoverers[i].lookup( peer_id, ip, port );
			
			if ( cp != null ){
				
				return( cp );
			}
		}
		
		return( new CachePeerImpl( CachePeer.PT_NONE, ip, port ));
	}
	
	public static class
	CachePeerImpl
		implements CachePeer
	{
		private int				type;
		private InetAddress		address;
		private int				port;
		private long			inject_time;
		private long			speed_change_time;
		
		public
		CachePeerImpl(
			int			_type,
			InetAddress	_address,
			int			_port )
		{
			type	= _type;
			address	= _address;
			port	= _port;
		}
		
		public int
		getType()
		{
			return( type );
		}
		
		public InetAddress
		getAddress()
		{
			return( address );
		}
		
		public int
		getPort()
		{
			return( port );
		}
		
		public long
		getInjectTime(
			long	now )
		{
			if ( inject_time > now ){
				
				inject_time	= now;
			}
			
			return( inject_time );
		}
		
		public void
		setInjectTime(
			long	time )
		{
			inject_time	= time;
		}
		
		public long
		getSpeedChangeTime(
			long	now )
		{
			if ( speed_change_time > now ){
				
				speed_change_time	= now;
			}
			
			return( speed_change_time );
		}
		
		public void
		setSpeedChangeTime(
			long	time )
		{
			speed_change_time	= time;
		}
		
		public boolean
		sameAs(
			CachePeer	other )
		{
			return( 
					getType() == other.getType() &&
					getAddress().getHostAddress().equals( other.getAddress().getHostAddress()) &&
					getPort() == other.getPort());
		}
	}
}
