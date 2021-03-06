/*
 * Created on 07-Nov-2004
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

package org.gudy.azureus2.core3.torrent.impl;

/**
 * @author parg
 *
 */

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;

import org.gudy.azureus2.core3.torrent.*;
import org.gudy.azureus2.core3.util.AETemporaryFileHandler;
import org.gudy.azureus2.core3.util.BDecoder;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.FileUtil;

public class
TOTorrentCreatorImpl
	implements TOTorrentCreator
{
	private File					torrent_base;
	private URL						announce_url;
	private boolean					add_other_hashes;
	private long					piece_length;
	private long 					piece_min_size;
	private long 					piece_max_size;
	private long 					piece_num_lower;
	private long 					piece_num_upper;
	
	private boolean					is_desc;
	
	private Map<String,File>		linkage_map		= new HashMap<String, File>();
	private File					descriptor_dir;
	
	private TOTorrentCreateImpl		torrent;

	private List<TOTorrentProgressListener>	listeners = new ArrayList<TOTorrentProgressListener>();
	
	public 
	TOTorrentCreatorImpl(
		File						_torrent_base )
	{
		torrent_base 		= _torrent_base;
	}
		
	public 
	TOTorrentCreatorImpl(
		File						_torrent_base,
		URL							_announce_url,
		boolean						_add_other_hashes,
		long						_piece_length )
		
		throws TOTorrentException
	{
		torrent_base 		= _torrent_base;
		announce_url		= _announce_url;
		add_other_hashes	= _add_other_hashes;
		piece_length		= _piece_length;	
	}
	
	public 
	TOTorrentCreatorImpl(	
		File						_torrent_base,
		URL							_announce_url,
		boolean						_add_other_hashes,
		long						_piece_min_size,
		long						_piece_max_size,
		long						_piece_num_lower,
		long						_piece_num_upper )
	
		throws TOTorrentException
	{
		torrent_base 		= _torrent_base;
		announce_url		= _announce_url;
		add_other_hashes	= _add_other_hashes;
		piece_min_size		= _piece_min_size;	
		piece_max_size		= _piece_max_size;	
		piece_num_lower		= _piece_num_lower;	
		piece_num_upper		= _piece_num_upper;	
	}
	
	public void
	setFileIsLayoutDescriptor(
		boolean		b )
	{
		is_desc	= b;
	}
	
	public TOTorrent
	create()
	
		throws TOTorrentException
	{
		try{
			if ( announce_url == null ){
				
				throw( new TOTorrentException( "Skeleton creator", TOTorrentException.RT_WRITE_FAILS ));
			}
			
			File	base_to_use;
			
			if ( is_desc ){
				
				base_to_use = createLayoutMap();
				
			}else{
				
				base_to_use = torrent_base;
			}
			
			if ( piece_length > 0 ){
				
				torrent = 
					new TOTorrentCreateImpl(
							linkage_map,
							base_to_use,
							announce_url,
							add_other_hashes,
							piece_length );
			}else{
				
				torrent = 
					new TOTorrentCreateImpl(
							linkage_map,
							base_to_use,
							announce_url,
							add_other_hashes,
							piece_min_size,
							piece_max_size,
							piece_num_lower,
							piece_num_upper );
			}
			
			for ( TOTorrentProgressListener l: listeners ){
				
				torrent.addListener( l );
			}
			
			torrent.create();
			
			return( torrent );
			
		}finally{
			
			if ( is_desc ){
				
				destroyLayoutMap();
			}
		}
	}
	
	private List<DescEntry>
	readDescriptor()
	
		throws TOTorrentException
	{
		try{
			int		top_files		= 0;
			int		top_entries		= 0;
			
			String 	top_component 	= null;
			
			Map	map = BDecoder.decode( FileUtil.readFileAsByteArray( torrent_base ));
			
			List<Map>	file_map = (List<Map>)map.get( "file_map" );
			
			if ( file_map == null ){
				
				throw( new TOTorrentException( "Invalid descriptor file", TOTorrentException.RT_READ_FAILS ));
			}
			
			List<DescEntry>	desc_entries = new ArrayList<DescEntry>();
			
			BDecoder.decodeStrings( file_map );
			
			for ( Map m: file_map ){
				
				List<String>	logical_path 	= (List<String>)m.get( "logical_path" );				
				String			target			= (String)m.get( "target" );
				
				if ( logical_path == null || target == null ){
					
					throw( new TOTorrentException( "Invalid descriptor file: entry=" + m, TOTorrentException.RT_READ_FAILS ));
				}
				
				if ( logical_path.size() == 0 ){
					
					throw( new TOTorrentException( "Logical path must have at least one entry: " + m, TOTorrentException.RT_READ_FAILS ));
				}

				for ( int i=0;i<logical_path.size();i++ ){
					
					logical_path.set( i, FileUtil.convertOSSpecificChars( logical_path.get(i), i < logical_path.size()-1));
				}
				
				File	tf = new File( target );
				
				if ( !tf.exists()){
					
					throw( new TOTorrentException( "Invalid descriptor file: file '" + tf + "' not found" + m, TOTorrentException.RT_READ_FAILS ));
					
				}else{
					
					String str = logical_path.get(0);
					
					if ( logical_path.size() == 1 ){
						
						top_entries++;
					}
					
					if ( top_component != null && !top_component.equals( str )){
					
						throw( new TOTorrentException( "Invalid descriptor file: multiple top level elements specified", TOTorrentException.RT_READ_FAILS ));
					}
					
					top_component = str;
				}
				
				desc_entries.add( new DescEntry( logical_path, tf ));
			}
			
			if ( top_entries > 1 ){
				
				throw( new TOTorrentException( "Invalid descriptor file: exactly one top level entry required", TOTorrentException.RT_READ_FAILS ));
			}
			
			if ( desc_entries.isEmpty()){
				
				throw( new TOTorrentException( "Invalid descriptor file: no mapping entries found", TOTorrentException.RT_READ_FAILS ));
			}
			
			return( desc_entries );
			
		}catch( IOException e ){
			
			throw( new TOTorrentException( "Invalid descriptor file: " + Debug.getNestedExceptionMessage( e ), TOTorrentException.RT_READ_FAILS ));

		}
	}
	
	private void
	mapDirectory(
		int			prefix_length,
		File		target,
		File		temp )
	
		throws IOException
	{
		File[]	files = target.listFiles();
		
		for ( File f: files ){

			String	file_name = f.getName();
			
			if ( file_name.equals( "." ) || file_name.equals( ".." )){
				
				continue;
			}
			
			File t = new File( temp, file_name);

			if ( f.isDirectory()){

				if ( !t.isDirectory()){
				
					t.mkdirs();
				}
				
				mapDirectory( prefix_length, f, t );
				
			}else{
				
				if ( !t.exists()){
					
					t.createNewFile();
					
				}else{
					
					throw( new IOException( "Duplicate file: " + t ));
				}
				
				linkage_map.put( t.getAbsolutePath().substring( prefix_length ), f );
			}
		}
	}
	
	private File
	createLayoutMap()
	
		throws TOTorrentException
	{
			// create a directory/file hierarchy that mirrors that prescribed by the descriptor
			// along with a linkage map to be applied during construction
		
		if ( descriptor_dir != null ){
			
			return( descriptor_dir );
		}
		
		try{
			descriptor_dir = AETemporaryFileHandler.createTempDir();
			
			File	top_level_file = null;
			
			List<DescEntry>	desc_entries	= readDescriptor();
			
			for ( DescEntry entry: desc_entries ){
				
				List<String>	logical_path	= entry.getLogicalPath();
				File			target			= entry.getTarget();
				
				File temp = descriptor_dir;
				
				int	prefix_length = descriptor_dir.getAbsolutePath().length() + 1;
				
				for ( int i=0;i<logical_path.size();i++ ){
					
					temp = new File( temp, logical_path.get( i ));
					
					if ( top_level_file == null ){
						
						top_level_file = temp;
					}
				}
				
				if ( target.isDirectory()){
				
					if ( !temp.isDirectory()){
						
						if ( !temp.mkdirs()){
							
							throw( new TOTorrentException( "Failed to create logical directory: " + temp, TOTorrentException.RT_WRITE_FAILS ));
						}
					}
					
					mapDirectory( prefix_length, target, temp );
					
				}else{
					
					File p = temp.getParentFile();
					
					if ( !p.isDirectory()){
						
						if ( !p.mkdirs()){
							
							throw( new TOTorrentException( "Failed to create logical directory: " + p, TOTorrentException.RT_WRITE_FAILS ));
						}
					}
					
					if ( temp.exists()){
					
						throw( new TOTorrentException( "Duplicate file: " + temp, TOTorrentException.RT_WRITE_FAILS ));
						
					}else{
						
						temp.createNewFile();
						
						linkage_map.put( temp.getAbsolutePath().substring( prefix_length ), target );
					}
				}
			}
					
			return( top_level_file );
			
		}catch( TOTorrentException e ){
			
			throw( e );
			
		}catch( Throwable e ){
			
			throw( new TOTorrentException( Debug.getNestedExceptionMessage( e ), TOTorrentException.RT_WRITE_FAILS ));
		}
	}
	
	private void
	destroyLayoutMap()
	{
		if ( descriptor_dir != null && descriptor_dir.exists()){
			
			if ( !FileUtil.recursiveDelete( descriptor_dir )){
				
				Debug.out( "Failed to delete descriptor directory '" + descriptor_dir + "'" );
			}
		}
	}
	
	public long
	getTorrentDataSizeFromFileOrDir()
	
		throws TOTorrentException
	{
		if ( is_desc ){
			
			List<DescEntry>	desc_entries	= readDescriptor();
			
			long	result = 0;
			
			for ( DescEntry entry: desc_entries ){
				
				result += getTorrentDataSizeFromFileOrDir( entry.getTarget());
			}
			
			return( result );
			
		}else{
		
			return( getTorrentDataSizeFromFileOrDir( torrent_base ));
		}
	}
	
	private long
	getTorrentDataSizeFromFileOrDir(
		File				file )
	{
		String	name = file.getName();
		
		if ( name.equals( "." ) || name.equals( ".." )){
			
			return( 0 );
		}
		
		if ( !file.exists()){
		
			return(0);
		}
		
		if ( file.isFile()){
					
			return( file.length());
				
		}else{
			
			File[]	dir_files = file.listFiles();
			
			long	length = 0;
			
			for (int i=0;i<dir_files.length;i++){
				
				length += getTorrentDataSizeFromFileOrDir( dir_files[i] );
			}
			
			return( length );
		}
	}
	
	public void
	cancel()
	{
		if ( torrent != null ){
		
			torrent.cancel();
		}
	}
	
	public void
	addListener(
		TOTorrentProgressListener	listener )
	{
		if ( torrent == null ){
			
			listeners.add( listener );
			
		}else{
		
			torrent.addListener( listener );
		}
	}
	
	public void
	removeListener(
		TOTorrentProgressListener	listener )
	{
		if ( torrent == null ){
			
			listeners.remove( listener );
			
		}else{
			
			torrent.removeListener( listener );
		}
	}
	
	private static class
	DescEntry
	{
		private List<String>	logical_path;
		private File			target;
		
		private
		DescEntry(
			List<String>		_l,
			File				_t )
		{
			logical_path	= _l;
			target			= _t;
		}
		
		private List<String>
		getLogicalPath()
		{
			return( logical_path );
		}
		
		private File
		getTarget()
		{
			return( target );
		}
	}
}
