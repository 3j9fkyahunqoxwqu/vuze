/*
 * Created on 12-Mar-2005
 * Created by Paul Gardner
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

package com.aelitis.azureus.plugins.dht;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.gudy.azureus2.core3.util.AEMonitor;
import org.gudy.azureus2.core3.util.BDecoder;
import org.gudy.azureus2.core3.util.BEncoder;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.HashWrapper;
import org.gudy.azureus2.core3.util.SHA1Hasher;
import org.gudy.azureus2.core3.util.SystemTime;

import com.aelitis.azureus.core.dht.DHT;
import com.aelitis.azureus.core.dht.DHTStorageAdapter;
import com.aelitis.azureus.core.dht.DHTStorageKey;
import com.aelitis.azureus.core.dht.impl.DHTLog;
import com.aelitis.azureus.core.dht.transport.DHTTransportContact;
import com.aelitis.azureus.core.dht.transport.DHTTransportValue;

/**
 * @author parg
 *
 */

public class 
DHTPluginStorageManager 
	implements DHTStorageAdapter
{
	private static final long		ADDRESS_EXPIRY			= 7*24*60*60*1000; 
	private static final int		DIV_WIDTH				= 10;
	private static final int		DIV_FRAG_GET_SIZE		= 2;
	private static final long		DIV_EXPIRY_MIN			= 2*24*60*60*1000;
	private static final long		DIV_EXPIRY_RAND			= 1*24*60*60*1000;
		
	
	public static final int			LOCAL_DIVERSIFICATION_SIZE_LIMIT			= 2048;
	public static final int			LOCAL_DIVERSIFICATION_ENTRIES_LIMIT			= 256;
	public static final int			LOCAL_DIVERSIFICATION_READS_PER_MIN_SAMPLES	= 3;
	public static final int			LOCAL_DIVERSIFICATION_READS_PER_MIN			= 30;
	
	private File	data_dir;
	
	private AEMonitor	this_mon	= new AEMonitor( "DHTPluginStorageManager" );
	
	private Map					recent_addresses	= new HashMap();
	
	private Map					remote_diversifications	= new HashMap();
	private Map					local_storage_keys		= new HashMap();
	

	public
	DHTPluginStorageManager(
		File				_data_dir )
	{
		data_dir	= _data_dir;
		
		data_dir.mkdirs();
		
		readRecentAddresses();
		
		readDiversifications();
	}
	
	protected void
	importContacts(
		DHT		dht )
	{
		try{
			this_mon.enter();
						
			File	target = new File( data_dir, "contacts.dat" );

			if ( !target.exists()){
				
				target	= new File( data_dir, "contacts.saving" );
			}

			if ( target.exists()){
				
				DataInputStream	dis =  new DataInputStream( new FileInputStream( target ));
				
				try{
					
					dht.importState( dis );
					
				}finally{
											
					dis.close();
				}
			}
		}catch( Throwable e ){
			
			Debug.printStackTrace( e );
			
		}finally{
			
			this_mon.exit();
		}
	}
	
	protected void
	exportContacts(
		DHT		dht )
	{
		try{
			this_mon.enter();
						
			File	saving = new File( data_dir, "contacts.saving" );
			File	target = new File( data_dir, "contacts.dat" );

			saving.delete();
			
			DataOutputStream	dos	= null;
			
			boolean	ok = false;
			
			try{
				dos = new DataOutputStream( new FileOutputStream( saving ));
					
				dht.exportState( dos, 32 );
			
				ok	= true;
				
			}finally{
				
				if ( dos != null ){
					
					dos.close();
					
					if ( ok ){
						
						target.delete();
						
						saving.renameTo( target );
					}
				}
			}
		}catch( Throwable e ){
			
			Debug.printStackTrace( e );
			
		}finally{
			
			this_mon.exit();
		}
	}
	
	protected void
	readRecentAddresses()
	{
		try{
			this_mon.enter();
			
			recent_addresses = readMapFromFile( "addresses" );
	
		}finally{
			
			this_mon.exit();
		}
	}
	
	protected void
	writeRecentAddresses()
	{
		try{
			this_mon.enter();
				// remove any old crud
			
			Iterator	it = recent_addresses.keySet().iterator();
			
			while( it.hasNext()){
				
				String	key = (String)it.next();
				
				Long	time = (Long)recent_addresses.get(key);
				
				if ( SystemTime.getCurrentTime() - time.longValue() > ADDRESS_EXPIRY ){
					
					it.remove();
				}
			}
			
			writeMapToFile( recent_addresses, "addresses" );
			
		}catch( Throwable e ){
			
			Debug.printStackTrace(e);
	
		}finally{
			
			this_mon.exit();
		}
	}
	
	protected void
	recordCurrentAddress(
		String		address )
	{
		try{
			this_mon.enter();

			recent_addresses.put( address, new Long( SystemTime.getCurrentTime()));
		
			writeRecentAddresses();
			
		}finally{
			
			this_mon.exit();
		}
	}
	
	protected boolean
	isRecentAddress(
		String		address )
	{
		try{
			this_mon.enter();

			return( recent_addresses.containsKey( address ));
					
		}finally{
			
			this_mon.exit();
		}
	}
	
	
	protected Map
	readMapFromFile(
		String		file_prefix )
	{
		try{
			this_mon.enter();
			
			File target = new File( data_dir, file_prefix + ".dat" );
			
			if ( !target.exists()){
				
				target	= new File( data_dir, file_prefix + ".saving" );
			}
			
			if ( target.exists()){
				
				BufferedInputStream	is = new BufferedInputStream( new FileInputStream( target ));
				
				try{
					return( BDecoder.decode( is ));
					
				}finally{
					
					is.close();
				}
			}
		}catch( Throwable e ){
			
			Debug.printStackTrace( e );
			
		}finally{
			
			this_mon.exit();
		}
		
		return( new HashMap());
	}
	
	protected void
	writeMapToFile(
		Map			map,
		String		file_prefix )
	{
		try{
			this_mon.enter();
		
			File	saving = new File( data_dir, file_prefix + ".saving" );
			File	target = new File( data_dir, file_prefix + ".dat" );

			saving.delete();
			
			FileOutputStream os = null;
			
			boolean	ok = false;
			
			try{
				byte[]	data = BEncoder.encode( map );
				
				os = new FileOutputStream( saving );
					
				os.write( data );
			
				os.close();
			
				ok	= true;
				
			}finally{
				
				if ( os != null ){
					
					os.close();
					
					if ( ok ){
						
						target.delete();
						
						saving.renameTo( target );
					}
				}
			}
			
		}catch( Throwable e ){
			
			Debug.printStackTrace(e);
	
		}finally{
			
			this_mon.exit();
		}
	}
	
		// key storage
	
	public DHTStorageKey
	keyCreated(
		HashWrapper		key,
		boolean			local )
	{
		//System.out.println( "DHT key created");
		
		try{
			this_mon.enter();
		
			return(	getStorageKey( key ));
			
		}finally{
			
			this_mon.exit();
		}
	}
	
	public void
	keyDeleted(
		DHTStorageKey		key )
	{
		//System.out.println( "DHT key deleted" );
		
		try{
			this_mon.enter();
		
			deleteStorageKey((storageKey)key );
			
		}finally{
			
			this_mon.exit();
		}
	}
	
	public void
	keyRead(
		DHTStorageKey			key,
		DHTTransportContact		contact )
	{
		//System.out.println( "DHT value read" );
		
		try{
			this_mon.enter();
		
			((storageKey)key).read();
			
		}finally{
			
			this_mon.exit();
		}
	}
	
	public void
	valueAdded(
		DHTStorageKey		key,
		DHTTransportValue	value )
	{
		//System.out.println( "DHT value added" );
		
		try{
			this_mon.enter();
		
			((storageKey)key).valueChanged( 1, value.getValue().length);
			
		}finally{
			
			this_mon.exit();
		}
	}
	
	public void
	valueUpdated(
		DHTStorageKey		key,
		DHTTransportValue	old_value,
		DHTTransportValue	new_value )
	{
		//System.out.println( "DHT value updated" );
		
		try{
			this_mon.enter();
			
			((storageKey)key).valueChanged( 0, new_value.getValue().length - old_value.getValue().length);
			
		}finally{
			
			this_mon.exit();
		}
	}
	
	public void
	valueDeleted(
		DHTStorageKey		key,
		DHTTransportValue	value )
	{
		//System.out.println( "DHT value deleted" );
		
		try{
			this_mon.enter();
		
			((storageKey)key).valueChanged( -1, -value.getValue().length);
			
		}finally{
			
			this_mon.exit();
		}
	}
	
		// get diversifications for put operations must deterministically return the same end points
		// but gets for gets should be randomised to load balance
	
	public byte[][]
	getExistingDiversification(
		byte[]			key,
		boolean			put_operation )
	{
		//System.out.println( "DHT get existing diversification: put = " + put_operation  );
		
		HashWrapper	wrapper = new HashWrapper( key );
		
			// must always return a value - original if no diversification exists
		
		try{
			this_mon.enter();
		
			byte[][]	res = followDivChain( wrapper, put_operation );
				
			/*
			String	trace = "";
			for (int i=0;i<res.length;i++){
				trace += (i==0?"":",") + DHTLog.getString2( res[i] );
			}
			System.out.println( "DHT get div: " + DHTLog.getString2(key) + ", put = " + put_operation + " -> " + trace );
			*/
			
			return( res );
			
		}finally{
			
			this_mon.exit();
		}
	}
	
	public byte[][]
	createNewDiversification(
		byte[]			key,
		boolean			put_operation,
		byte			diversification_type )
	{
		//System.out.println( "DHT create new diversification: put = " + put_operation +", type = " + diversification_type );
		
		HashWrapper	wrapper = new HashWrapper( key );
		
		try{
			this_mon.enter();
		
			diversification	div = lookupDiversification( wrapper );
		
			boolean	created = false;
			
			if ( div == null ){
				
				div = createDiversification( wrapper, diversification_type );
				
				created	= true;
				
			}
		
			byte[][] res = followDivChain( wrapper, put_operation );
			
			/*
			String	trace = "";
			for (int i=0;i<res.length;i++){
				trace += (i==0?"":",") + DHTLog.getString2( res[i] );
			}
			System.out.println( "DHT create div: " + DHTLog.getString2(key) + ", new = " + created + ", put = " + put_operation + ", type = " + diversification_type + " -> " + trace );
			*/

			return( res );
			
		}finally{
			
			this_mon.exit();
		}
	} 
	
	protected byte[][]
	followDivChain(
		HashWrapper	wrapper,
		boolean		put_operation )
	{
		List	list = new ArrayList();
		
		list.add( wrapper );
		
		list	= followDivChain( list, put_operation, 0 );
		
		byte[][]	res = new byte[list.size()][];
		
		for (int i=0;i<list.size();i++){
			
			res[i] = ((HashWrapper)list.get(i)).getBytes();
		}
		
		return( res );
	}
	
	protected List
	followDivChain(
		List		list_in,
		boolean		put_operation,
		int			depth )
	{
		List	list_out = new ArrayList();
	
		/*
		String	indent = "";
		for(int i=0;i<depth;i++){
			indent+= "  ";
		}
		System.out.println( indent + "->" );
		*/
		
		for (int i=0;i<list_in.size();i++){
			
			HashWrapper	wrapper = (HashWrapper)list_in.get(i);
		
			diversification	div = lookupDiversification( wrapper );

			if ( div == null ){
				
				list_out.add( wrapper );
				
			}else{
				
					// replace this entry with the diversified keys 
				
				List	new_list = followDivChain( div.getKeys( put_operation ), put_operation, depth+1 );
				
				for (int j=0;j<new_list.size();j++){
					
					list_out.add( new_list.get(j));
				}
			}
		}
		// System.out.println( indent + "<-" );

		return( list_out );
	}
	
	protected storageKey
	getStorageKey(
		HashWrapper		key )
	{
		storageKey	res = (storageKey)local_storage_keys.get( key );
		
		if ( res == null ){
			
			res = new storageKey( this, DHT.DT_NONE, key ); 
			
			local_storage_keys.put( key, res );
		}
		
		return( res );
	}
	
	protected void
	deleteStorageKey(
		storageKey		key )
	{
		local_storage_keys.remove( key );
		
		writeDiversifications();
	}
	
	protected void
	readDiversifications()
	{
		try{
			this_mon.enter();
			
			Map	map = readMapFromFile( "diverse" );
	
			List	keys = (List)map.get("local");
			
			if ( keys != null ){
				
				long	now = SystemTime.getCurrentTime();
				
				for (int i=0;i<keys.size();i++){
					
					storageKey d = storageKey.deserialise(this, (Map)keys.get(i));
										
					if ( d.getExpiry() > now ){
					
						local_storage_keys.put( d.getKey(), d );
					}
				}
			}
			List	divs = (List)map.get("remote");
			
			if ( divs != null ){
				
				long	now = SystemTime.getCurrentTime();
				
				for (int i=0;i<divs.size();i++){
					
					diversification d = diversification.deserialise((Map)divs.get(i));
										
					if ( d.getExpiry() > now ){
					
						remote_diversifications.put( d.getKey(), d );
					}
				}
			}
			
		}finally{
			
			this_mon.exit();
		}
	}
	
	protected void
	writeDiversifications()
	{
		try{
			this_mon.enter();
	
			Map	map = new HashMap();
			
			List	keys = new ArrayList();
			
			map.put( "local", keys );
			
			Iterator	it = local_storage_keys.values().iterator();
			
			while( it.hasNext()){
			
				keys.add(((storageKey)it.next()).serialise());
			}
			
			List	divs = new ArrayList();
			
			map.put( "remote", divs );
			
			it = remote_diversifications.values().iterator();
			
			while( it.hasNext()){
			
				divs.add(((diversification)it.next()).serialise());
			}
			
			writeMapToFile( map, "diverse" );
			
		}catch( Throwable e ){
			
			Debug.printStackTrace(e);
	
		}finally{
			
			this_mon.exit();
		}
	}
	
	protected diversification
	lookupDiversification(
		HashWrapper	wrapper )
	{
		diversification	div = (diversification)remote_diversifications.get(wrapper);
		
		if ( div != null ){
			
			if ( div.getExpiry() < SystemTime.getCurrentTime()){
				
				remote_diversifications.remove( wrapper );
				
				div = null;
			}
		}
		
		return( div );
	}
	
	protected diversification
	createDiversification(
		HashWrapper		wrapper,
		byte			type )
	{
		diversification	div = new diversification( wrapper, type );
			
		remote_diversifications.put( wrapper, div );
		
		writeDiversifications();
		
		return( div );
	}
	
	protected static class
	diversification
	{
		protected HashWrapper		key;
		protected byte				type;
		
		protected long				expiry;
		
		protected int[]				fixed_put_offsets;
		
		protected
		diversification(
			HashWrapper	_key,
			byte		_type )
		
		{
			key		= _key;
			type	= _type;
			
			expiry	= SystemTime.getCurrentTime() + DIV_EXPIRY_MIN + (long)(Math.random() * DIV_EXPIRY_RAND );
			
			fixed_put_offsets	= new int[DIV_FRAG_GET_SIZE];
			
			int	pos = 0;
			
			while( pos < DIV_FRAG_GET_SIZE ){
				
				int i = (int)(Math.random()*DIV_WIDTH);
				
				boolean	found = false;
				
				for (int j=0;j<pos;j++){
					
					if( i == fixed_put_offsets[j] ){
						
						found	= true;
						
						break;
					}
				}
				
				if ( !found ){
					
					fixed_put_offsets[pos++] = i;
				}
			}
		}
		
		protected
		diversification(
			HashWrapper	_key,
			byte		_type,
			long		_expiry,
			int[]		_fixed_put_offsets )
		{
			key					= _key;
			type				= _type;
			expiry				= _expiry;
			fixed_put_offsets	= _fixed_put_offsets;
		}
		
		protected Map
		serialise()
		{
			Map	map = new HashMap();
			
			map.put( "key", key.getBytes());
			map.put( "type", new Long(type));
			map.put( "exp", new Long(expiry));
			
			List	offsets = new ArrayList();
			
			for (int i=0;i<fixed_put_offsets.length;i++){
				
				offsets.add( new Long( fixed_put_offsets[i]));
			}
			
			map.put( "fpo", offsets );
			
			return( map );
		}
		
		protected static diversification
		deserialise(
			Map		map )
		{
			HashWrapper	key 	= new HashWrapper((byte[])map.get("key"));
			int			type 	= ((Long)map.get("type")).intValue(); 
			long		exp 	= ((Long)map.get("exp")).longValue();
			
			List	offsets = (List)map.get("fpo");
			
			int[]	fops = new int[offsets.size()];
			
			for (int i=0;i<fops.length;i++){
				
				fops[i] = ((Long)offsets.get(i)).intValue();
			}
			
			return( new diversification( key, (byte)type, exp, fops ));
		}
		
		protected HashWrapper
		getKey()
		{
			return( key );
		}
		
		protected long
		getExpiry()
		{
			return( expiry );
		}
		
		protected List
		getKeys(
			boolean		put )
		{
			List	keys = new ArrayList();
			
			if ( put ){
				
				if ( type == DHT.DT_FREQUENCY ){
					
						// put to all keys
										
					for (int i=0;i<DIV_WIDTH;i++){
						
						keys.add( diversifyKey( key, i ));
					}
										
				}else{
					
						// put to a fixed subset. has to be fixed else over time we'll put to
						// all the fragmented locations and nullify the point of this. gets are
						// randomised to we don't loose out by fixing the puts
															
					for (int i=0;i<fixed_put_offsets.length;i++){
						
						keys.add( diversifyKey( key, fixed_put_offsets[i]));
					}					
				}
			}else{
				
					// get always returns a randomised selection
				
				if ( type == DHT.DT_FREQUENCY ){
					
						// diversification has lead to caching at all 'n' places
					
					keys.add( diversifyKey( key,(int)(Math.random()*DIV_WIDTH)));
					
				}else{
					
						// diversification has fragmented across 'n' places
						// select 2 to search
					
					List	randoms = new ArrayList();
					
					while( randoms.size() < DIV_FRAG_GET_SIZE ){
						
						Integer	i = new Integer((int)(Math.random()*DIV_WIDTH));
						
						if ( !randoms.contains(i)){
							
							randoms.add( i );
						}
					}
										
					for (int i=0;i<DIV_FRAG_GET_SIZE;i++){
						
						keys.add( diversifyKey( key, ((Integer) randoms.get(i)).intValue()));
					}
				}
			}
			
			return( keys );
		}
	
		protected HashWrapper
		diversifyKey(
			HashWrapper		key_in,
			int				offset )
		{
			byte[]	old_bytes	= key_in.getBytes();
			
			byte[]	bytes = new byte[old_bytes.length+1];
			
			System.arraycopy( old_bytes, 0, bytes, 0, old_bytes.length );
			
			bytes[old_bytes.length] = (byte)offset;
			
			return( new HashWrapper( new SHA1Hasher().calculateHash( bytes )));
		}
	}
	
	protected static class
	storageKey
		implements DHTStorageKey
	{
		private DHTPluginStorageManager	manager;
		
		private HashWrapper				key;	
		private byte					type;
		
		private int				size;
		private int				entries;
		
		private long			expiry;
		
		private long			read_count_start;
		private int				read_count;
		
		protected
		storageKey(
			DHTPluginStorageManager	_manager,
			byte					_type,
			HashWrapper				_key )
		{
			manager		= _manager;
			type		= _type;
			key			= _key;
			
			expiry	= SystemTime.getCurrentTime() + DIV_EXPIRY_MIN + (long)(Math.random() * DIV_EXPIRY_RAND );
		}
		
		protected
		storageKey(
			DHTPluginStorageManager	_manager,
			byte					_type,
			HashWrapper				_key,
			long					_expiry )
		{
			manager		= _manager;
			type		= _type;
			key			= _key;
			expiry		= _expiry;
		}
		
		protected Map
		serialise()
		{
			Map	map = new HashMap();
			
			map.put( "key", key.getBytes());
			map.put( "type", new Long(type));
			map.put( "exp", new Long(expiry));
			
			return( map );
		}
		protected static storageKey
		deserialise(
			DHTPluginStorageManager	_manager,
			Map						map )
		{
			HashWrapper	key 	= new HashWrapper((byte[])map.get("key"));
			int			type 	= ((Long)map.get("type")).intValue(); 
			long		exp 	= ((Long)map.get("exp")).longValue();
			
			return( new storageKey( _manager, (byte)type, key, exp ));
		}
		
		protected HashWrapper
		getKey()
		{
			return( key );
		}
		
		protected long
		getExpiry()
		{
			return( expiry );
		}
		
		public byte
		getDiversificationType()
		{
			if ( type != DHT.DT_NONE ){
				
					// trigger timeouts here
				
				if ( expiry < SystemTime.getCurrentTime()){

					type	= DHT.DT_NONE;
					
					manager.writeDiversifications();
				}
			}
			
			return( type );
		}
		
		protected void
		read()
		{
			// System.out.println( "read: " + DHTLog.getString2( key.getBytes()));
			
			if ( type == DHT.DT_NONE ){
				
				read_count++;
				
				long	now = SystemTime.getCurrentTime();
				
				long	diff = now - read_count_start;
				
				if ( diff > LOCAL_DIVERSIFICATION_READS_PER_MIN_SAMPLES*60*1000 ){
					
					// System.out.println( "read rate = " + read_count );
					
					if ( read_count > LOCAL_DIVERSIFICATION_READS_PER_MIN * LOCAL_DIVERSIFICATION_READS_PER_MIN_SAMPLES ){
					
						type = DHT.DT_FREQUENCY;
						
						manager.writeDiversifications();
					}
					
					read_count_start	= now;
					read_count			= 0;
				}
			}
		}
		
		protected void
		valueChanged(
			int		entries_diff,
			int		size_diff )
		{
			entries += entries_diff;
			size	+= size_diff;
			
			if ( entries < 0 ){
				Debug.out( "entries negative" );
				entries	= 0;
			}
			
			if ( size < 0 ){
				Debug.out( "size negative" );
				size	= 0;
			}
			
			if ( type == DHT.DT_NONE ){
				
				if ( size > LOCAL_DIVERSIFICATION_SIZE_LIMIT ){
				
					type	= DHT.DT_SIZE;
					
					manager.writeDiversifications();
					
				}else if ( entries > LOCAL_DIVERSIFICATION_ENTRIES_LIMIT ){
					
					type 	= DHT.DT_SIZE;
					
					manager.writeDiversifications();
				}
			}
			
			// System.out.println( "value changed: entries = " + entries + "(" + entries_diff + "), size = " +	size +  "(" + size_diff + ")");
		}
	}
}
