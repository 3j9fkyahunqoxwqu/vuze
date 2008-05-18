/*
 * Created on May 16, 2008
 * Created by Paul Gardner
 * 
 * Copyright 2008 Vuze, Inc.  All rights reserved.
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License only.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package com.aelitis.azureus.core.vuzefile;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.Map;

import org.gudy.azureus2.core3.util.BDecoder;
import org.gudy.azureus2.plugins.utils.StaticUtilities;
import org.gudy.azureus2.plugins.utils.resourcedownloader.ResourceDownloader;


public class 
VuzeFileHandler 
{
	private static VuzeFileHandler singleton = new VuzeFileHandler();
	
	public static VuzeFileHandler
	getSingleton()
	{
		return( singleton );
	}
	
	protected
	VuzeFileHandler()
	{
		
	}
	
	public VuzeFile
	loadVuzeFile(
		String	target  )
	{
		try{
			File test_file = new File( target );
	
			if ( test_file.isFile()){
					
				return( getVuzeFile( new FileInputStream( test_file )));
				
			}else{
				
				URL	url = new URI( target ).toURL();
				
				ResourceDownloader rd = StaticUtilities.getResourceDownloaderFactory().create( url );
				
				return( getVuzeFile(rd.download()));
			}
	
		}catch( Throwable e ){
		}
		
		return( null );
	}
	
	protected VuzeFile
	getVuzeFile(
		InputStream		is )
	{
		try{
			BufferedInputStream bis = new BufferedInputStream( is );
			
			try{
				Map	map = BDecoder.decode(bis);
				
				if ( map.containsKey( "vuze" ) && !map.containsKey( "info" )){
					
					return( new VuzeFileImpl( map ));
				}
				
			}finally{
				
				is.close();
			}
		}catch( Throwable e ){
		}
		
		return( null );
	}
	
	public boolean
	loadAndHandleVuzeFIle(
		String	target )
	{
		VuzeFile vf = loadVuzeFile( target );
		
		if ( vf == null ){
			
			return( false );
		}
		
		handleFiles( new VuzeFile[]{ vf });
		
		return( true );
	}
	
	public void
	handleFiles(
		VuzeFile[]		files )
	{
		
	}
}
