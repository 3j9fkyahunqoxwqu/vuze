/*
 * File    : DownloadManagerImpl.java
 * Created : 19-Oct-2003
 * By      : parg
 * 
 * Azureus - a Java Bittorrent client
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.gudy.azureus2.core3.download.impl;
/*
 * Created on 30 juin 2003
 *
 */
 
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.net.*;


import org.gudy.azureus2.core3.config.*;
import org.gudy.azureus2.core3.config.impl.TransferSpeedValidator;
import org.gudy.azureus2.core3.disk.*;
import org.gudy.azureus2.core3.disk.impl.DiskManagerImpl;
import org.gudy.azureus2.core3.global.GlobalManager;
import org.gudy.azureus2.core3.global.GlobalManagerStats;
import org.gudy.azureus2.core3.internat.*;
import org.gudy.azureus2.core3.logging.*;
import org.gudy.azureus2.core3.peer.*;
import org.gudy.azureus2.core3.tracker.client.*;
import org.gudy.azureus2.core3.torrent.*;
import org.gudy.azureus2.core3.util.*;
import org.gudy.azureus2.core3.download.*;

import org.gudy.azureus2.plugins.download.DownloadAnnounceResult;
import org.gudy.azureus2.plugins.download.DownloadScrapeResult;
import org.gudy.azureus2.plugins.network.ConnectionManager;

import com.aelitis.azureus.core.util.CaseSensitiveFileMap;

/**
 * @author Olivier
 * 
 */

public class 
DownloadManagerImpl 
	extends LogRelation
	implements DownloadManager
{
	
	private static int	upload_when_busy_min_secs;
	
	static{
		COConfigurationManager.addAndFireParameterListener(
			"max.uploads.when.busy.inc.min.secs",
			new ParameterListener()
			{
				public void 
				parameterChanged(
					String name )
				{
					upload_when_busy_min_secs = COConfigurationManager.getIntParameter( name );
				}
			});
	}
	
	private static final String CFG_MOVE_COMPLETED_TOP = "Newly Seeding Torrents Get First Priority";
		// DownloadManager listeners
	
	private static final int LDT_STATECHANGED			= 1;
	private static final int LDT_DOWNLOADCOMPLETE		= 2;
	private static final int LDT_COMPLETIONCHANGED 		= 3;
	private static final int LDT_POSITIONCHANGED 		= 4;
	private static final int LDT_FILEPRIORITYCHANGED 	= 5;
	
	
	private AEMonitor	listeners_mon	= new AEMonitor( "DM:DownloadManager:L" );

	private static ListenerManager	listeners_aggregator 	= ListenerManager.createAsyncManager(
			"DM:ListenAggregatorDispatcher",
			new ListenerManagerDispatcher()
			{
				public void
				dispatch(
					Object		_listener,
					int			type,
					Object		_value )
				{
					DownloadManagerListener	listener = (DownloadManagerListener)_listener;
					
					Object[]	value = (Object[])_value;
					
					DownloadManagerImpl	dm = (DownloadManagerImpl)value[0];
					
					if ( type == LDT_STATECHANGED ){
						
						listener.stateChanged(dm, ((Integer)value[1]).intValue());
						
					}else if ( type == LDT_DOWNLOADCOMPLETE ){
						
						listener.downloadComplete(dm);

					}else if ( type == LDT_COMPLETIONCHANGED ){
						
						listener.completionChanged(dm, ((Boolean)value[1]).booleanValue());

					}else if ( type == LDT_FILEPRIORITYCHANGED ){
						
						listener.filePriorityChanged(dm, (DiskManagerFileInfo)value[1]);

					}else if ( type == LDT_POSITIONCHANGED ){
												
						listener.positionChanged( dm, ((Integer)value[1]).intValue(), ((Integer)value[2]).intValue());
						                         
					}
				}
			});		
	
	private ListenerManager	listeners 	= ListenerManager.createManager(
			"DM:ListenDispatcher",
			new ListenerManagerDispatcher()
			{
				public void
				dispatch(
					Object		listener,
					int			type,
					Object		value )
				{
					listeners_aggregator.dispatch( listener, type, value );
				}
			});	
	
		// TrackerListeners
	
	private static final int LDT_TL_ANNOUNCERESULT		= 1;
	private static final int LDT_TL_SCRAPERESULT		= 2;
	
	private ListenerManager	tracker_listeners 	= ListenerManager.createManager(
			"DM:TrackerListenDispatcher",
			new ListenerManagerDispatcher()
			{
				public void
				dispatch(
					Object		_listener,
					int			type,
					Object		value )
				{
					DownloadManagerTrackerListener	listener = (DownloadManagerTrackerListener)_listener;
					
					if ( type == LDT_TL_ANNOUNCERESULT ){
						
						listener.announceResult((TRTrackerAnnouncerResponse)value);
						
					}else if ( type == LDT_TL_SCRAPERESULT ){
						
						listener.scrapeResult((TRTrackerScraperResponse)value);
					}
				}
			});	

	// PeerListeners
	
	private static final int LDT_PE_PEER_ADDED		= 1;
	private static final int LDT_PE_PEER_REMOVED	= 2;
	private static final int LDT_PE_PIECE_ADDED		= 3;
	private static final int LDT_PE_PIECE_REMOVED	= 4;
	private static final int LDT_PE_PM_ADDED		= 5;
	private static final int LDT_PE_PM_REMOVED		= 6;
	
		// one static async manager for them all
	
	private static ListenerManager	peer_listeners_aggregator 	= ListenerManager.createAsyncManager(
			"DM:PeerListenAggregatorDispatcher",
			new ListenerManagerDispatcher()
			{
				public void
				dispatch(
					Object		_listener,
					int			type,
					Object		value )
				{
					DownloadManagerPeerListener	listener = (DownloadManagerPeerListener)_listener;
					
					if ( type == LDT_PE_PEER_ADDED ){
						
						listener.peerAdded((PEPeer)value);
						
					}else if ( type == LDT_PE_PEER_REMOVED ){
						
						listener.peerRemoved((PEPeer)value);
						
					}else if ( type == LDT_PE_PIECE_ADDED ){
						
						listener.pieceAdded((PEPiece)value);
						
					}else if ( type == LDT_PE_PIECE_REMOVED ){
						
						listener.pieceRemoved((PEPiece)value);
						
					}else if ( type == LDT_PE_PM_ADDED ){
						
						listener.peerManagerAdded((PEPeerManager)value);
						
					}else if ( type == LDT_PE_PM_REMOVED ){
						
						listener.peerManagerRemoved((PEPeerManager)value);
					}			
				}
			});

	private ListenerManager	peer_listeners 	= ListenerManager.createManager(
			"DM:PeerListenDispatcher",
			new ListenerManagerDispatcher()
			{
				public void
				dispatch(
					Object		listener,
					int			type,
					Object		value )
				{
					peer_listeners_aggregator.dispatch( listener, type, value );
				}
			});	
	
	private AEMonitor	peer_listeners_mon	= new AEMonitor( "DM:DownloadManager:PL" );
	
	private List	current_peers 	= new ArrayList();
	private List	current_pieces	= new ArrayList();
  
	private DownloadManagerController	controller;
	private DownloadManagerStatsImpl	stats;

	protected AEMonitor					this_mon = new AEMonitor( "DM:DownloadManager" );
	
	private boolean		persistent;

	/**
	 * Pretend this download is complete while not running, 
	 * even if it has no data.  When the torrent starts up, the real complete
	 * level will be checked (probably by DiskManager), and if the torrent
	 * actually does have missing data at that point, the download will be thrown
	 * into error state.
	 * <p>
	 * Only a forced-recheck should clear this flag.
	 * <p>
	 * Current Implementation:<br>
	 * - implies that the user completed the download at one point<br>
	 * - Checks if there's Data Missing when torrent is done (or torrent load)
	 */
	private boolean assumedComplete;
	
	/**
	 * forceStarted torrents can't/shouldn't be automatically stopped
	 */
	
	private int			last_informed_state	= STATE_START_OF_DAY;
	private boolean		latest_informed_force_start;

	private GlobalManager globalManager;
	private String torrentFileName;
	
	private boolean	open_for_seeding;
	
	private String	display_name	= "";
	private String	internal_name	= "";
	
		// for simple torrents this refers to the torrent file itself. For non-simple it refers to the
		// folder containing the torrent's files
	
	private File	torrent_save_location;	
  
	// Position in Queue
	private int position = -1;
	
	private Object[]					read_torrent_state;
	private	DownloadManagerState		download_manager_state;
	
	private TOTorrent		torrent;
	private String 			torrent_comment;
	private String 			torrent_created_by;
	
	private TRTrackerAnnouncer 				tracker_client;
	private TRTrackerAnnouncerListener		tracker_client_listener = 
			new TRTrackerAnnouncerListener() 
			{
				public void 
				receivedTrackerResponse(
					TRTrackerAnnouncerResponse	response) 
				{
					PEPeerManager pm = controller.getPeerManager();
      
					if ( pm != null ) {
        
						pm.processTrackerResponse( response );
					}

					tracker_listeners.dispatch( LDT_TL_ANNOUNCERESULT, response );
				}

				public void 
				urlChanged(
					String 	url, 
					boolean explicit) 
				{
					if ( explicit ){
						requestTrackerAnnounce( true );
					}
				}

				public void 
				urlRefresh() 
				{
					requestTrackerAnnounce( true );
				}
			};
	
				// a second listener used to catch and propagate the "stopped" event
				
	private TRTrackerAnnouncerListener		stopping_tracker_client_listener = 
		new TRTrackerAnnouncerListener() 
		{
			public void 
			receivedTrackerResponse(
				TRTrackerAnnouncerResponse	response) 
			{
				tracker_listeners.dispatch( LDT_TL_ANNOUNCERESULT, response );
			}

			public void 
			urlChanged(
				String 	url, 
				boolean explicit) 
			{
			}

			public void 
			urlRefresh() 
			{
			}
		};
		
		
	private long						scrape_random_seed	= SystemTime.getCurrentTime();

	private HashMap data;
  
	private boolean data_already_allocated = false;
  
	private long	creation_time	= SystemTime.getCurrentTime();
  
	private int iSeedingRank;

	private boolean az_messaging_enabled = true;
   
	private boolean	dl_identity_obtained;
	private byte[]	dl_identity;
    private int 	dl_identity_hashcode;

    private int		max_connections;
    private int		max_uploads_when_seeding;
    private boolean	max_uploads_when_seeding_enabled;
    
    private int		max_upload_when_busy_bps;
    private int		current_upload_when_busy_bps;
    private long	last_upload_when_busy_update;
    private long	last_upload_when_busy_dec_time;
    
	// Only call this with STATE_QUEUED, STATE_WAITING, or STATE_STOPPED unless you know what you are doing
	
	
	public 
	DownloadManagerImpl(
		GlobalManager 							_gm,
		byte[]									_torrent_hash,
		String 									_torrentFileName, 
		String 									_torrent_save_dir,
		String									_torrent_save_file,
		int   									_initialState,
		boolean									_persistent,
		boolean									_recovered,
		boolean									_open_for_seeding,
		boolean									_has_ever_been_started,
		List									_file_priorities,
		DownloadManagerInitialisationAdapter	_initialisation_adapter ) 
	{
		if ( 	_initialState != STATE_WAITING &&
				_initialState != STATE_STOPPED &&
				_initialState != STATE_QUEUED ){
			
			Debug.out( "DownloadManagerImpl: Illegal start state, " + _initialState );
		}
		
		persistent			= _persistent;
		globalManager 		= _gm;
		open_for_seeding	= _open_for_seeding;

			// TODO: move this to download state!
		
    	if ( _file_priorities != null ){
    		
    		setData( "file_priorities", _file_priorities );
    	}
    	
		stats = new DownloadManagerStatsImpl( this );
  	
		controller	= new DownloadManagerController( this );

		stats.setMaxUploads( COConfigurationManager.getIntParameter("Max Uploads") );
	 	
		torrentFileName = _torrentFileName;
		
		while( _torrent_save_dir.endsWith( File.separator )){
			
			_torrent_save_dir = _torrent_save_dir.substring(0, _torrent_save_dir.length()-1 );
		}
		
			// readTorrent adjusts the save dir and file to be sensible values
			
		readTorrent( 	_torrent_save_dir, _torrent_save_file, _torrent_hash, 
						persistent && !_recovered, _open_for_seeding, _has_ever_been_started,
						_initialState );		

		if ( torrent != null && _initialisation_adapter != null ){
			
			try{
				_initialisation_adapter.initialised( this );
				
			}catch( Throwable e ){
					
				Debug.printStackTrace(e);
			}
		}
	}


	private void 
	readTorrent(
		String		torrent_save_dir,
		String		torrent_save_file,
		byte[]		torrent_hash,		// can be null for initial torrents
		boolean		new_torrent,		// probably equivalend to (torrent_hash == null)????
		boolean		for_seeding,
		boolean		has_ever_been_started,
		int			initial_state )
	{		
		try{
			display_name				= torrentFileName;	// default if things go wrong decoding it
			internal_name				= "";
			torrent_comment				= "";
			torrent_created_by			= "";
			
			try{
	
					// this is the first thing we do and most likely to go wrong - hence its
					// existence is used below to indicate success or not
				
				 download_manager_state	= 
					 	DownloadManagerStateImpl.getDownloadState(
					 			this, torrentFileName, torrent_hash );
				 
				 readParameters();
				 
					// establish any file links
					
				 download_manager_state.addListener(
						new DownloadManagerStateListener()
						{
							public void
							stateChanged(
								DownloadManagerState			state,
								DownloadManagerStateEvent		event )
							{
								if ( event.getType() == DownloadManagerStateEvent.ET_ATTRIBUTE_WRITTEN ){
									
									String	attribute_name = (String)event.getData();
									
									if ( attribute_name.equals( DownloadManagerState.AT_FILE_LINKS )){
										
										setFileLinks();
										
									}else if ( attribute_name.equals( DownloadManagerState.AT_PARAMETERS )){
										
										readParameters();
									}
								}
							}
						});
						
				 torrent	= download_manager_state.getTorrent();
				 
				 setFileLinks();
				 
				 	// We can't have the identity of this download changing as this will screw up
				 	// anyone who tries to maintain a unique set of downloads (e.g. the GlobalManager)
				 	//
				 
				 if ( !dl_identity_obtained ){
					 
					 	// flag set true below
					 
					 dl_identity			= torrent_hash==null?torrent.getHash():torrent_hash;
	                 
	                 this.dl_identity_hashcode = new String( dl_identity ).hashCode();		 
				 }
					 
				 if ( !Arrays.equals( dl_identity, torrent.getHash())){
						 
					 torrent	= null;	// prevent this download from being used
					 
					 throw( new NoStackException( "Download identity changed - please remove and re-add the download" ));
				 }
				 
				 read_torrent_state	= null;	// no longer needed if we saved it
	
				 LocaleUtilDecoder	locale_decoder = LocaleTorrentUtil.getTorrentEncoding( torrent );
						 
				 	// if its a simple torrent and an explicit save file wasn't supplied, use
				 	// the torrent name itself
				 
				 display_name = locale_decoder.decodeString( torrent.getName());
	             
				 display_name = FileUtil.convertOSSpecificChars( display_name );
			
				 internal_name = ByteFormatter.nicePrint(torrent.getHash(),true);
	
				 	// now we know if its a simple torrent or not we can make some choices about
				 	// the save dir and file. On initial entry the save_dir will have the user-selected
				 	// save location and the save_file will be null
				 
				 File	save_dir_file	= new File( torrent_save_dir );
				 
				 // System.out.println( "before: " + torrent_save_dir + "/" + torrent_save_file );
				 
				 	// if save file is non-null then things have already been sorted out
				 
				 if ( torrent_save_file == null ){
				 		 	
				 		// make sure we're working off a canonical save dir if possible
				 	
				 	try{
				 		if ( save_dir_file.exists()){
				 			
				 			save_dir_file = save_dir_file.getCanonicalFile();
				 		}
				 	}catch( Throwable e ){
				 			
				 		Debug.printStackTrace(e);
				 	}
		
				 	if ( torrent.isSimpleTorrent()){
				 		
				 			// if target save location is a directory then we use that as the save
				 			// dir and use the torrent display name as the target. Otherwise we
				 			// use the file name
				 		
				 		if ( save_dir_file.exists()){
				 			
				 			if ( save_dir_file.isDirectory()){
				 				
				 				torrent_save_file	= display_name;
				 				
				 			}else{
				 				
				 				torrent_save_dir	= save_dir_file.getParent().toString();
				 				
				 				torrent_save_file	= save_dir_file.getName();
				 			}
				 		}else{
				 			
				 				// doesn't exist, assume it refers directly to the file
				 			
				 			if ( save_dir_file.getParent() == null ){
				 				
				 				throw( new NoStackException( "Data location '" + torrent_save_dir + "' is invalid" ));
	
				 			}
				 			
			 				torrent_save_dir	= save_dir_file.getParent().toString();
			 				
			 				torrent_save_file	= save_dir_file.getName(); 			
				 		}
				 		
				 	}else{
				 	
				 			// torrent is a folder. It is possible that the natural location
				 			// for the folder is X/Y and that in fact 'Y' already exists and
				 			// has been selected. If ths is the case the select X as the dir and Y
				 			// as the file name
				 		
				 		if ( save_dir_file.exists()){
				 			
				 			if ( !save_dir_file.isDirectory()){
				 				
				 				throw( new NoStackException( "'" + torrent_save_dir + "' is not a directory" ));
				 			}
				 			
				 			if ( save_dir_file.getName().equals( display_name )){
				 				
				 				torrent_save_dir	= save_dir_file.getParent().toString();
				 			}
				 		}
				 		
				 		torrent_save_file	= display_name;		
				 	}
				 }
	
				 torrent_save_location = new File( torrent_save_dir, torrent_save_file );
				 
				 	// final validity test must be based of potentially linked target location as file
				 	// may have been re-targetted
	
				 File	linked_target = getSaveLocation();
				 
				 if ( !linked_target.exists()){
				 	
				 		// if this isn't a new torrent then we treat the absence of the enclosing folder
				 		// as a fatal error. This is in particular to solve a problem with the use of
				 		// externally mounted torrent data on OSX, whereby a re-start with the drive unmounted
				 		// results in the creation of a local diretory in /Volumes that subsequently stuffs
				 		// up recovery when the volume is mounted
				 	
				 		// changed this to only report the error on non-windows platforms 
				 	
				 	if ( !(new_torrent || Constants.isWindows )){
				 		
							// another exception here - if the torrent has never been started then we can
							// fairly safely continue as its in a stopped state
						
						if ( has_ever_been_started ){
				 		
							throw( new NoStackException( MessageText.getString("DownloadManager.error.datamissing") + " " + linked_target.toString()));
						}
				 	}
				 }	
				 
				 	// if this is a newly introduced torrent trash the tracker cache. We do this to
				 	// prevent, say, someone publishing a torrent with a load of invalid cache entries
				 	// in it and a bad tracker URL. This could be used as a DOS attack
	
				 if ( new_torrent ){
				 	
					download_manager_state.setLongParameter( DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME, SystemTime.getCurrentTime());
					 
				 	download_manager_state.setTrackerResponseCache( new HashMap());
				 	
				 		// also remove resume data incase someone's published a torrent with resume
				 		// data in it
				 	
				 	if ( for_seeding ){
				 		
				 		DiskManagerFactory.setTorrentResumeDataNearlyComplete(download_manager_state);
	
				 	}else{
				 		
				 		download_manager_state.clearResumeData();
				 	}
				 }else{
					 
			       long	add_time = download_manager_state.getLongParameter( DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME );
			        
			       if ( add_time == 0 ){
			    	  
			        		// grab an initial value from torrent file - migration only
			    	   
			        	try{
			        		add_time = new File( torrentFileName ).lastModified();
			        		
			        	}catch( Throwable e ){
			        	}
			        	
			        	if ( add_time == 0 ){
			        		
			        		add_time = SystemTime.getCurrentTime();
			        	}
			        	
			        	download_manager_state.setLongParameter( DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME, add_time );
			        }
				 }
				 
		         
				 //trackerUrl = torrent.getAnnounceURL().toString();
	         
				torrent_comment = locale_decoder.decodeString(torrent.getComment());
	         
				if ( torrent_comment == null ){
					
				   torrent_comment	= "";
				}
				
				torrent_created_by = locale_decoder.decodeString(torrent.getCreatedBy());
	         
				if ( torrent_created_by == null ){
					
					torrent_created_by	= "";
				}
				 			 
				 	// only restore the tracker response cache for non-seeds
		   
				 if ( DiskManagerFactory.isTorrentResumeDataComplete( this )) {
				 	
					 	// actually, can't think of a good reason not to restore the
					 	// cache for seeds, after all if the tracker's down we still want
					 	// to connect to peers to upload to
					 
					  // download_manager_state.clearTrackerResponseCache();
						
					  stats.setDownloadCompleted(1000);
				  
					  setAssumedComplete(true);
				  
				 }else{
				 					 
					 setAssumedComplete(false);
				}
			}catch( TOTorrentException e ){
			
				Debug.printStackTrace( e );
				       		 			
				setFailed( TorrentUtils.exceptionToText( e ));
	 			
			}catch( UnsupportedEncodingException e ){
			
				Debug.printStackTrace( e );
				       					
				setFailed( MessageText.getString("DownloadManager.error.unsupportedencoding"));
				
			}catch( NoStackException e ){
				
				Debug.outNoStack( e.getMessage());
				
			}catch( Throwable e ){
				
				Debug.printStackTrace( e );
				   					
				setFailed( e );
				
			}finally{
				
				 dl_identity_obtained	= true;			 
			}
			
			if ( download_manager_state == null ){
			
				read_torrent_state = 
					new Object[]{ 	
						torrent_save_dir, torrent_save_file, torrent_hash,
						new Boolean(new_torrent), new Boolean( for_seeding ), new Boolean( has_ever_been_started ),
						new Integer( initial_state )
					};
	
					// torrent's stuffed - create a dummy "null object" to simplify use
					// by other code
				
				download_manager_state	= DownloadManagerStateImpl.getDownloadState( this );
				
					// make up something vaguely sensible for save location
				
				if ( torrent_save_file == null ){
					
					torrent_save_location = new File( torrent_save_dir );
					
				}else{
					
					torrent_save_location = new File( torrent_save_dir, torrent_save_file );
				}
				
			}else{
				
					// make sure we know what networks to use for this download
				
				if ( download_manager_state.getNetworks().length == 0 ){
					
					String[] networks = AENetworkClassifier.getNetworks( torrent, display_name );
					
					download_manager_state.setNetworks( networks );
				}
				
				if ( download_manager_state.getPeerSources().length == 0 ){
					
					String[] ps = PEPeerSource.getPeerSources();
					
					download_manager_state.setPeerSources( ps );
				}
			}			
		}finally{
			
			if ( torrent_save_location != null ){
				
				try{
					torrent_save_location = torrent_save_location.getCanonicalFile();
					
				}catch( Throwable e ){
					
					torrent_save_location = torrent_save_location.getAbsoluteFile();
				}
			}
			
				// must be after torrent read, so that any listeners have a TOTorrent
				// not that if things have failed above this method won't override a failed
				// state with the initial one
			
			controller.setInitialState( initial_state );
		}
	}

	protected void
	readTorrent()
	{
		if ( read_torrent_state == null ){
			
			return;
		}
		
		readTorrent(
				(String)read_torrent_state[0],
				(String)read_torrent_state[1],
				(byte[])read_torrent_state[2],
				((Boolean)read_torrent_state[3]).booleanValue(),
				((Boolean)read_torrent_state[4]).booleanValue(),
				((Boolean)read_torrent_state[5]).booleanValue(),
				((Integer)read_torrent_state[6]).intValue());

	}
	
	protected void
	readParameters()
	{
		max_connections						= getDownloadState().getIntParameter( DownloadManagerState.PARAM_MAX_PEERS );
		max_uploads_when_seeding_enabled 	= getDownloadState().getBooleanParameter( DownloadManagerState.PARAM_MAX_UPLOADS_WHEN_SEEDING_ENABLED );
		max_uploads_when_seeding 			= getDownloadState().getIntParameter( DownloadManagerState.PARAM_MAX_UPLOADS_WHEN_SEEDING );
		max_upload_when_busy_bps			= getDownloadState().getIntParameter( DownloadManagerState.PARAM_MAX_UPLOAD_WHEN_BUSY ) * 1024;

	}
	
	protected int
	getMaxConnections()
	{
		return( max_connections );
	}
	
	protected boolean
	isMaxUploadsWhenSeedingEnabled()
	{
		return( max_uploads_when_seeding_enabled );
	}
	
	protected int
	getMaxUploadsWhenSeeding()
	{
		return( max_uploads_when_seeding );
	}
	
	public int
	getEffectiveUploadRateLimitBytesPerSecond()
	{
		int	local_max_bps	= stats.getUploadRateLimitBytesPerSecond();
		int	rate			= local_max_bps;
		
		if ( max_upload_when_busy_bps != 0 ){
			
			long	now = SystemTime.getCurrentTime();
			
			if ( now < last_upload_when_busy_update || now - last_upload_when_busy_update > 5000 ){
				
				last_upload_when_busy_update	= now;
				
					// might need to impose the limit
				
				String key = TransferSpeedValidator.getActiveUploadParameter( globalManager );
				
				int	global_limit_bps = COConfigurationManager.getIntParameter( key )*1024;
								
				if ( global_limit_bps > 0 && max_upload_when_busy_bps < global_limit_bps ){
				
						// we have a global limit and a valid busy limit
				
					local_max_bps = local_max_bps==0?global_limit_bps:local_max_bps;

					GlobalManagerStats gm_stats = globalManager.getStats();
	
					int	actual = gm_stats.getDataSendRateNoLAN() + gm_stats.getProtocolSendRateNoLAN();
				
					int	move_by = ( local_max_bps - max_upload_when_busy_bps ) / 10;
					
					if ( move_by < 1024 ){
						
						move_by = 1024;
					}
					
					if ( global_limit_bps - actual <= 2*1024 ){
				
							// close enough to impose the busy limit downwards
						
						
						if ( current_upload_when_busy_bps == 0 ){
							
							current_upload_when_busy_bps = local_max_bps;
						}
						
						int	prev_upload_when_busy_bps = current_upload_when_busy_bps;
						
						current_upload_when_busy_bps -= move_by;
						
						if ( current_upload_when_busy_bps < max_upload_when_busy_bps ){
							
							current_upload_when_busy_bps = max_upload_when_busy_bps;
						}
						
						if ( current_upload_when_busy_bps < prev_upload_when_busy_bps ){
							
							last_upload_when_busy_dec_time = now;
						}
					}else{
						
							// not hitting limit, increase
						
						if ( current_upload_when_busy_bps != 0 ){
							
								// only try increment if sufficient time passed
							
							if ( 	upload_when_busy_min_secs == 0 ||
									now < last_upload_when_busy_dec_time ||
									now - last_upload_when_busy_dec_time >=  upload_when_busy_min_secs*1000 ){
									
								current_upload_when_busy_bps += move_by;
								
								if ( current_upload_when_busy_bps >= local_max_bps ){
									
									current_upload_when_busy_bps	= 0;
								}
							}
						}
					}
					
					if ( current_upload_when_busy_bps > 0 ){
						
						rate = current_upload_when_busy_bps;
					}
				}else{
					
					current_upload_when_busy_bps = 0;
				}
			}else{
				
				if ( current_upload_when_busy_bps > 0 ){
				
					rate = current_upload_when_busy_bps;
				}
			}
		}
		
		return( rate );
	}
	
	protected void
	setFileLinks()
	{
			// invalidate the cache info in case its now wrong
		
		cached_save_location	= null;
		
		DiskManagerFactory.setFileLinks( this, download_manager_state.getFileLinks());
		
		controller.fileInfoChanged();
	}
	
	protected void
	clearFileLinks()
	{
		download_manager_state.clearFileLinks();
	}
	
	protected void
	updateFileLinks(
		String		_old_dir,
		String		_new_dir,
		File		_old_save_dir )
	{
		try{
			String	old_dir 		= new File( _old_dir ).getCanonicalPath();
			String	new_dir 		= new File( _new_dir ).getCanonicalPath();
			String	old_save_dir 	= _old_save_dir.getCanonicalPath();
			
			CaseSensitiveFileMap	links = download_manager_state.getFileLinks();
			
			Iterator	it = links.keySetIterator();
			
			while( it.hasNext()){
				
				File	from 	= (File)it.next();
				File	to		= (File)links.get(from);
				
				if ( to == null ){
					
					continue;
				}
				
				String	from_str = from.getCanonicalPath();
				
				if ( from_str.startsWith( old_save_dir )){
					
					String	new_from_str;
					
					String	from_suffix = from_str.substring( old_dir.length());
					
					if ( from_suffix.startsWith( File.separator )){
						
						new_from_str = new_dir + from_suffix;
						
					}else{
						
						new_from_str = new_dir + File.separator + from_suffix;
					}
					
					String	to_str = to.getCanonicalPath();

					if ( to_str.startsWith( old_save_dir )){

						String	new_to_str;
						
						String	to_suffix = to_str.substring( old_dir.length());
						
						if ( to_suffix.startsWith( File.separator )){
							
							new_to_str = new_dir + to_suffix;
							
						}else{
							
							new_to_str = new_dir + File.separator + to_suffix;
						}
						
						to	= new File( new_to_str );
					}
					
					// System.out.println( "Updating file link:" + from + "->" + to + ":" + new_from_str );
					
					download_manager_state.setFileLink( from, null );
					download_manager_state.setFileLink( new File( new_from_str), to ); 
				}
			}
			
		}catch( Throwable e ){
			
			Debug.printStackTrace(e);
		}
	}
	
	public void
	destroy()
	{
		clearFileLinks();
	}
	
	public boolean 
	filesExist() 
	{
		return( controller.filesExist());
	}
	
	
	public boolean
	isPersistent()
	{
		return( persistent );
	}
  
	public String 
	getDisplayName() 
	{
		return( display_name );
	}	

 	public String
	getInternalName()
  	{
 		return( internal_name );
  	}
 	
	public String 
	getErrorDetails() 
	{
		return( controller.getErrorDetail());
	}

	public long 
	getSize() 
	{
		if( torrent != null){
		
			return torrent.getSize();
		}
	  
		return 0;
	}

	protected void
	setFailed()
	{
		setFailed((String)null );
	}
  
	protected void
	setFailed(
		Throwable 	e )
	{
		setFailed( Debug.getNestedExceptionMessage(e));
	}
  
	protected void
	setFailed(
		String	str )
	{
		controller.setFailed( str );
	}
  
  

	public void
	saveResumeData()
	{
		if ( getState() == STATE_DOWNLOADING) {

			try{
				getDiskManager().saveResumeData( true );
    		
			}catch( Exception e ){
    		
				setFailed( "Resume data save fails: " + Debug.getNestedExceptionMessage(e));
			}
		}
    
		// we don't want to update the torrent if we're seeding
	  
		if ( !assumedComplete  ){
	  	
			download_manager_state.save();
		}
	}
  
  	public void
  	saveDownload()
  	{
  		DiskManager disk_manager = controller.getDiskManager();
    
  		if ( disk_manager != null ){
    	
  			disk_manager.saveState();
  		}
    
  		download_manager_state.save();
  	}
  
  
	public void 
	initialize() 
	{
	  	// entry:  valid if waiting, stopped or queued
	  	// exit: error, ready or on the way to error
	  
		if ( torrent == null ) {

				// have a go at re-reading the torrent in case its been recovered
			
			readTorrent();
		}
		
		if ( torrent == null ) {

			setFailed();
      
			return;
		}
		         	
		// If a torrent that is assumed complete, verify that it actually has the
		// files, before we create the diskManager, which will try to allocate
		// disk space.
		if (assumedComplete && !filesExist()) {
			// filesExist() has set state to error for us

			// If the user wants to re-download the missing files, they must
			// do a re-check, which will reset the flag.
			return;
		}
   
		try{
			try{
				this_mon.enter();
			
				if ( tracker_client != null ){
	
					Debug.out( "DownloadManager: initialize called with tracker client still available" );
					
					tracker_client.destroy();
				}
	
				tracker_client = TRTrackerAnnouncerFactory.create( torrent, download_manager_state.getNetworks());
	    
				tracker_client.setTrackerResponseCache( download_manager_state.getTrackerResponseCache());
					
				tracker_client.addListener( tracker_client_listener );
				
			}finally{
				
				this_mon.exit();
			}
     	
      		// we need to set the state to "initialized" before kicking off the disk manager
      		// initialisation as it should only report its status while in the "initialized"
      		// state (see getState for how this works...)
      	      
			try{
				controller.initializeDiskManager( open_for_seeding );
				
			}finally{
				
					// only supply this the very first time the torrent starts so the controller can check 
					// that things are ok. Subsequent restarts are under user control
				
				open_for_seeding	= false;
			}
			
		}catch( TRTrackerAnnouncerException e ){
 		
			setFailed( e ); 
		}
	}
  
  
	public void
	setStateWaiting()
	{
		controller.setStateWaiting();
	}
  
  	public void
  	setStateFinishing()
  	{
  		controller.setStateFinishing();
  	}
  
  	public void
  	setStateQueued()
  	{
  		controller.setStateQueued();
  	}
  
  	public int
  	getState()
  	{
  		return( controller.getState());
  	}
 
  	public int
  	getSubState()
  	{
  		return( controller.getSubState());
  	}
  	
  	public boolean
  	canForceRecheck()
  	{
		if ( getTorrent() == null ){
  	  		
  				// broken torrent, can't force recheck
  	  		
			return( false );
	  	}

  		return( controller.canForceRecheck());
  	}
  
  	public void
  	forceRecheck()
  	{
  		controller.forceRecheck();
  	}
  
    public void
    resetFile(
    	DiskManagerFileInfo		file )
    {
		int	state = getState();
  		
	  	if ( 	state == DownloadManager.STATE_STOPPED ||
	  			state == DownloadManager.STATE_ERROR ){
	  			  		
	  		DiskManagerFactory.clearResumeData( this, file );
	  		
	  	}else{
	  		
	  		Debug.out( "Download not stopped" );
	  	}
    }
    
    public void
    recheckFile(
    	DiskManagerFileInfo		file )
    {
		int	state = getState();
  		
	  	if ( 	state == DownloadManager.STATE_STOPPED ||
	  			state == DownloadManager.STATE_ERROR ){

	  		DiskManagerFactory.recheckFile( this, file );

	  	}else{
	  		
	  		Debug.out( "Download not stopped" );
	  	}
	  }
    
  	public void
  	restartDownload()
  	{
  		controller.restartDownload();
  	}
  
  	public void
  	startDownload()
  	{
 		controller.startDownload( getTrackerClient() ); 
  	}
  	
  	public void
  	stopIt(
  		int		state_after_stopping,
  		boolean	remove_torrent,
  		boolean	remove_data )
  	{
  		controller.stopIt( state_after_stopping, remove_torrent, remove_data );
  	}
  	
	public boolean
	pause()
	{
		return( globalManager.pauseDownload( this ));
	}
	
	public boolean
	isPaused()
	{
		return( globalManager.isPaused( this ));
	}
	
	public void
	resume()
	{
		globalManager.resumeDownload( this );
	}
	
	public boolean getAssumedComplete() {
		return assumedComplete;
	}

	public boolean requestAssumedCompleteMode() {
		boolean bCompleteNoDND = controller.isDownloadComplete(false);

		setAssumedComplete(bCompleteNoDND);
		return bCompleteNoDND;
	}

	// Protected: Use requestAssumedCompleteMode outside of scope
	protected void setAssumedComplete(boolean _assumedComplete) {
		if (assumedComplete == _assumedComplete) {
			return;
		}

		//Logger.log(new LogEvent(this, LogIDs.CORE, "setAssumedComplete("
		//		+ _assumedComplete + ") was " + assumedComplete));

		assumedComplete = _assumedComplete;

		if (!assumedComplete) {
			controller.setStateDownloading();
		}

		// NOTE: We don't set "stats.setDownloadCompleted(1000)" anymore because
		//       we can be in seeding mode with an unfinished torrent

		if (position != -1) {
			// we are in a new list, move to the top of the list so that we continue 
			// seeding.
			// -1 position means it hasn't been added to the global list.  We
			// shouldn't touch it, since it'll get a position once it's adding is
			// complete

			DownloadManager[] dms = { DownloadManagerImpl.this };

			// pretend we are at the bottom of the new list
			// so that move top will shift everything down one

			position = globalManager.getDownloadManagers().size() + 1;

			if (COConfigurationManager.getBooleanParameter(CFG_MOVE_COMPLETED_TOP)) {

				globalManager.moveTop(dms);

			} else {

				globalManager.moveEnd(dms);
			}

			// we left a gap in incomplete list, fixup

			globalManager.fixUpDownloadManagerPositions();
		}

		listeners.dispatch(LDT_COMPLETIONCHANGED, new Object[] {
				this,
				new Boolean(_assumedComplete) });
	}
  
  
  public int 
  getNbSeeds() 
  {
	  PEPeerManager peerManager = controller.getPeerManager();
	  
	  if (peerManager != null){
		  
		  return peerManager.getNbSeeds();
	  }
	  
	  return 0;
  }

  public int
  getNbPeers() 
  {
	  PEPeerManager peerManager = controller.getPeerManager();

	  if (peerManager != null){
		
		  return peerManager.getNbPeers();
	  }
	  
	  return 0;
  }

  

  	public String 
  	getTrackerStatus() 
  	{
  		TRTrackerAnnouncer tc = getTrackerClient();
  		
  		if (tc != null){
  			
  			return tc.getStatusString();
  		}
    
  			// no tracker, return scrape
  		
  		if (torrent != null ) {
  			
  			TRTrackerScraperResponse response = getTrackerScrapeResponse();
      
  			if (response != null) {
  				return response.getStatusString();
  				
  			}
  		}

  		return "";
  	}
  
  	public TRTrackerAnnouncer 
  	getTrackerClient() 
  	{
  		return( tracker_client );
  	}
 
	public void
	setAnnounceResult(
		DownloadAnnounceResult	result )
	{
		TRTrackerAnnouncer	cl = getTrackerClient();
		
		if ( cl == null ){
			
			Debug.out( "setAnnounceResult called when download not running" );
			
			return;
		}
		
		cl.setAnnounceResult( result );
	}
	
	public void
	setScrapeResult(
		DownloadScrapeResult	result )
	{
		if ( torrent != null && result != null ){
			
			TRTrackerScraper	scraper = globalManager.getTrackerScraper();
		
			TRTrackerScraperResponse current_resp = getTrackerScrapeResponse();
			
			URL	target_url;
			
			if ( current_resp != null ){
				
				target_url = current_resp.getURL();
				
			}else{
				
				target_url = torrent.getAnnounceURL();
			}
			
			scraper.setScrape( torrent, target_url, result );
		}
	}
	
	public int 
	getNbPieces() 
	{		
		if ( torrent == null ){
			
			return(0);
		}
		
		return( torrent.getNumberOfPieces());
	}


	public int 
	getTrackerTime() 
	{
		TRTrackerAnnouncer tc = getTrackerClient();
		
		if ( tc != null){
			
			return( tc.getTimeUntilNextUpdate());
		}
		
			// no tracker, return scrape
			
		if ( torrent != null ) {
				
			TRTrackerScraperResponse response = getTrackerScrapeResponse();
				
			if (response != null) {
					
				if (response.getStatus() == TRTrackerScraperResponse.ST_SCRAPING){
          
					return( -1 );
				}
					
				return (int)((response.getNextScrapeStartTime() - SystemTime.getCurrentTime()) / 1000);
			}
		}
		
		return( TRTrackerAnnouncer.REFRESH_MINIMUM_SECS );
	}

 
  	public TOTorrent
  	getTorrent() 
  	{
  		return( torrent );
  	}

 	private File	cached_save_location;
	private File	cached_save_location_result;
 	  	
  	public File 
	getSaveLocation()
  	{	  
  			// this can be called quite often - cache results for perf reasons
  		
  		File	save_location	= torrent_save_location;
  		
  		if ( save_location == cached_save_location  ){
  			
  			return( cached_save_location_result );
  		}
  			  			 			 			
 		File	res = download_manager_state.getFileLink( save_location );
 			
 		if ( res == null ){
 				
 			res	= save_location;
 		}else{
 			
 			try{
				res = res.getCanonicalFile();
				
			}catch( Throwable e ){
				
				res = res.getAbsoluteFile();
			}
 		}
 		
 		cached_save_location		= save_location;
 		cached_save_location_result	= res;
 		
 		return( res );
 	}
	
  	public File
  	getAbsoluteSaveLocation()
  	{
  		return( torrent_save_location );
  	}
  	
	public void 
	setTorrentSaveDir(
		String 	new_dir ) 
	{
		File	old_location = torrent_save_location;
		
		String	old_dir = old_location.getParent();
		
		if ( new_dir.equals( old_dir )){
			
			return;
		}
		
  		// assumption here is that the caller really knows what they are doing. You can't
  		// just change this willy nilly, it must be synchronised with reality. For example,
  		// the disk-manager calls it after moving files on completing
  		// The UI can call it as long as the torrent is stopped.
  		// Calling it while a download is active will in general result in unpredictable behaviour!
 
		updateFileLinks( old_dir, new_dir, torrent.isSimpleTorrent()?old_location.getParentFile():old_location );

		torrent_save_location = new File( new_dir, old_location.getName());

		try{
			torrent_save_location = torrent_save_location.getCanonicalFile();
			
		}catch( Throwable e ){
			
			torrent_save_location = torrent_save_location.getAbsoluteFile();
		}
		
		controller.fileInfoChanged();
	}

	public String 
	getPieceLength()
	{
		if ( torrent != null ){
			return( DisplayFormatters.formatByteCountToKiBEtc(torrent.getPieceLength()));
		}
		
		return( "" );
	}

	public String 
	getTorrentFileName() 
	{
		return torrentFileName;
	}

	public void 
	setTorrentFileName(
		String string) 
	{
		torrentFileName = string;
	}

		// this is called asynchronously when a response is received
	  
  	public void
  	setTrackerScrapeResponse(
  		TRTrackerScraperResponse	response )
  	{
  			// this is a reasonable place to pick up the change in active url caused by this scrape
  			// response and update the torrent's url accordingly
		
		Object[] res = getActiveScrapeResponse();
  		
		URL	active_url = (URL)res[1];

		if ( active_url != null && torrent != null ){
			
			torrent.setAnnounceURL( active_url );
		}
		
			// big-time yuck here. GlobalManager used to only call this method if the response was valid
			// so errors got lost. Due to the changes to setting the announce-url here (rather than in the
			// "get" logic that was leading to recursive mayhem) I need to pick up the errors here to so 
			// I dropped the GM condition. However, I'm a little worried about screwing up existing code
			// that doesn't do sensible things with "invalid" responses, so I've added the test here
			// for the moment...
		
		if ( response.isValid()){
		
			tracker_listeners.dispatch( LDT_TL_SCRAPERESULT, response );
		}
  	}
  	
	public TRTrackerScraperResponse 
	getTrackerScrapeResponse() 
	{
		Object[] res = getActiveScrapeResponse();
		
		return((TRTrackerScraperResponse)res[0]);
	}
  
		/**
		 * Returns the "first" online scrape response found, and its active URL, otherwise one of the failing
		 * scrapes
		 * @return
		 */
	
	protected Object[]
	getActiveScrapeResponse()
	{
		TRTrackerScraperResponse 	response	= null;
       	URL							active_url	= null;
       	
		TRTrackerScraper	scraper = globalManager.getTrackerScraper();
	
		TRTrackerAnnouncer tc = getTrackerClient();
		
		if ( tc != null ){
  	
			response = scraper.scrape( tc );
		}
  
		if ( response == null && torrent != null){
      	
				// torrent not running. For multi-tracker torrents we need to behave sensibly
      			// here
      	
			TRTrackerScraperResponse	non_null_response = null;
    	
			TOTorrentAnnounceURLSet[]	sets = torrent.getAnnounceURLGroup().getAnnounceURLSets();
    	
			if ( sets.length == 0 ){
    	
				response = scraper.scrape(torrent);
   
			}else{
    			    			
					// we use a fixed seed so that subsequent scrapes will randomise
    				// in the same order, as required by the spec. Note that if the
    				// torrent's announce sets are edited this all works fine (if we
    				// cached the randomised URL set this wouldn't work)
    		
				Random	scrape_random = new Random(scrape_random_seed);
    		
				for (int i=0;response==null && i<sets.length;i++){
    			
					TOTorrentAnnounceURLSet	set = sets[i];
    			
					URL[]	urls = set.getAnnounceURLs();
    			
					List	rand_urls = new ArrayList();
    							 	
					for (int j=0;j<urls.length;j++ ){
			  		
						URL url = urls[j];
					            									
						int pos = (int)(scrape_random.nextDouble() *  (rand_urls.size()+1));
					
						rand_urls.add(pos,url);
					}
			 	
					for (int j=0;response==null && j<rand_urls.size();j++){
						
						URL url = (URL)rand_urls.get(j);
						
						response = scraper.scrape(torrent, url);
			 		
						if ( response!= null ){
							
							int status = response.getStatus();
							
								// Exit if online
							
							if (status == TRTrackerScraperResponse.ST_ONLINE) {

								active_url	= url;
								
								break;
							}

								// Scrape 1 at a time to save on outgoing connections
							
							if (	status == TRTrackerScraperResponse.ST_INITIALIZING || 
									status == TRTrackerScraperResponse.ST_SCRAPING) {
								
								break;
							}
								
								// treat bad scrapes as missing so we go on to 
			 					// the next tracker
			 			
							if ( (!response.isValid()) || status == TRTrackerScraperResponse.ST_ERROR ){
			 				
								if ( non_null_response == null ){
			 					
									non_null_response	= response;
								}
			 				
								response	= null;
							}					
						}
					}
				}
    		
				if ( response == null ){
    			
					response = non_null_response;
				}
			}
		}
		
		return( new Object[]{ response, active_url } );
	}
	
	public void
	requestTrackerAnnounce(
		boolean	force )
	{
		TRTrackerAnnouncer tc = getTrackerClient();
		
		if ( tc != null)
	
			tc.update( force );
	}

	public void
	requestTrackerScrape(
		boolean	force )
	{
		if ( torrent != null ){
	    	
			TRTrackerScraper	scraper = globalManager.getTrackerScraper();
 
			scraper.scrape( torrent, force );
		}
	}
	
	protected void
	setTrackerRefreshDelayOverrides(
		int	percent )
	{
		TRTrackerAnnouncer tc = getTrackerClient();
		
		if ( tc != null ){
			
			tc.setRefreshDelayOverrides( percent );
		}
	}
	
	public String 
	getTorrentComment() 
	{
		return torrent_comment;
	}	
  
	public String 
	getTorrentCreatedBy() 
	{
		return torrent_created_by;
	}
  
	public long 
	getTorrentCreationDate() 
	{
		if (torrent==null){
			return(0);
		}
  	
		return( torrent.getCreationDate());
	}
  

	public GlobalManager
	getGlobalManager()
	{
		return( globalManager );
	}
	
	public DiskManager
	getDiskManager()
	{
		return( controller.getDiskManager());
	}
  
	public DiskManagerFileInfo[]
   	getDiskManagerFileInfo()
	{
		return( controller.getDiskManagerFileInfo());
	}
	
	public PEPeerManager
	getPeerManager()
	{
		return( controller.getPeerManager());
	}

	public boolean isDownloadComplete(boolean bIncludeDND) {
		if (!bIncludeDND) {
			return assumedComplete;
		}
		
		return controller.isDownloadComplete(bIncludeDND);
	}
  	
	public void
	addListener(
		DownloadManagerListener	listener )
	{
		try{
			listeners_mon.enter();

			listeners.addListener(listener);
				
			listener.stateChanged( this, getState());

				// we DON'T dispatch a downloadComplete event here as this event is used to mark the
				// transition between downloading and seeding, NOT purely to inform of seeding status
			
		}finally{
			
			listeners_mon.exit();
		}
	}
	
	public void
	removeListener(
		DownloadManagerListener	listener )
	{
		try{
			listeners_mon.enter();

			listeners.removeListener(listener);
			
		}finally{
			
			listeners_mon.exit();
		}
	}
	
	protected void
	informStateChanged()
	{
			// whenever the state changes we'll get called 
		try{
			listeners_mon.enter();
			
			int		new_state 		= controller.getState();
			boolean new_force_start	= controller.isForceStart();

			if ( 	new_state != last_informed_state ||
					new_force_start != latest_informed_force_start ){
				
				last_informed_state	= new_state;
				
				latest_informed_force_start	= new_force_start;
				
				listeners.dispatch( LDT_STATECHANGED, new Object[]{ this, new Integer( new_state )});
			}
			
		}finally{
			
			listeners_mon.exit();
		}
	}
	
	protected void
	informDownloadEnded()
	{
		try{
			listeners_mon.enter();

			listeners.dispatch( LDT_DOWNLOADCOMPLETE, new Object[]{ this });
		
		}finally{
			
			listeners_mon.exit();
		}
	}
	
	protected void
	informPriorityChange(
		DiskManagerFileInfo	file )
	{
		controller.filePriorityChanged(file);
		
		try{
			listeners_mon.enter();

			listeners.dispatch( LDT_FILEPRIORITYCHANGED, new Object[]{ this, file });
		
		}finally{
			
			listeners_mon.exit();
		}
		
		requestAssumedCompleteMode();
	}
	
	protected void
	informPositionChanged(
		int new_position )
	{
		try{
			listeners_mon.enter();
			
			int	old_position = position;
			
			if ( new_position != old_position ){
				
				position = new_position;
				
				listeners.dispatch( 
					LDT_POSITIONCHANGED, 
					new Object[]{ this, new Integer( old_position ), new Integer( new_position )});
			}
		}finally{
			
			listeners_mon.exit();
		}
	}

	public void
	addPeerListener(
		DownloadManagerPeerListener	listener )
	{
		addPeerListener(listener, true);
	}

	public void
	addPeerListener(
		DownloadManagerPeerListener	listener,
		boolean bDispatchForExisting )
	{
		try{
			peer_listeners_mon.enter();
			
			peer_listeners.addListener( listener );
			
			if (!bDispatchForExisting)
				return; // finally will call
  		
			for (int i=0;i<current_peers.size();i++){
  			
				peer_listeners.dispatch( listener, LDT_PE_PEER_ADDED, current_peers.get(i));
			}
		
			for (int i=0;i<current_pieces.size();i++){
  			
				peer_listeners.dispatch( listener, LDT_PE_PIECE_ADDED, current_pieces.get(i));
			}
		
			PEPeerManager	temp = controller.getPeerManager();
		
			if ( temp != null ){
	
				peer_listeners.dispatch( listener, LDT_PE_PM_ADDED, temp );
			}
  	
		}finally{

			peer_listeners_mon.exit();
		}
	}
		
	public void
	removePeerListener(
		DownloadManagerPeerListener	listener )
	{
		peer_listeners.removeListener( listener );
	}	
 
 
	
	public void
	addPeer(
		PEPeer 		peer )
	{
		try{
			peer_listeners_mon.enter();
 	
			current_peers.add( peer );
  		
			peer_listeners.dispatch( LDT_PE_PEER_ADDED, peer );
  		
		}finally{
		
			peer_listeners_mon.exit();
		}
	}
		
	public void
	removePeer(
		PEPeer		peer )
	{
		try{
			peer_listeners_mon.enter();
    	
			current_peers.remove( peer );
    	
			peer_listeners.dispatch( LDT_PE_PEER_REMOVED, peer );
    	
		}finally{
    	
			peer_listeners_mon.exit();
		}
	}
		
	public PEPeer[] 
	getCurrentPeers() 
	{
		try{
			peer_listeners_mon.enter();

			return (PEPeer[])current_peers.toArray(new PEPeer[current_peers.size()]);
			
		}finally{
			
			peer_listeners_mon.exit();

		}
	}

	public void
	addPiece(
		PEPiece 	piece )
	{
		try{
			peer_listeners_mon.enter();
  		
			current_pieces.add( piece );
  		
			peer_listeners.dispatch( LDT_PE_PIECE_ADDED, piece );
  		
		}finally{
  		
			peer_listeners_mon.exit();
		}
	}
		
	public void
	removePiece(
		PEPiece		piece )
	{
		try{
			peer_listeners_mon.enter();
  		
			current_pieces.remove( piece );
  		
			peer_listeners.dispatch( LDT_PE_PIECE_REMOVED, piece );
  		
		}finally{
  		
			peer_listeners_mon.exit();
		}
	}

	public PEPiece[] 
	getCurrentPieces() 
	{
		try{
			peer_listeners_mon.enter();

			return (PEPiece[])current_pieces.toArray(new PEPiece[current_pieces.size()]);
			
		}finally{
			
			peer_listeners_mon.exit();

		}	
	}


  	protected void
  	informStarted(
		PEPeerManager	pm )
  	{
		try{
			peer_listeners_mon.enter();
			
			peer_listeners.dispatch( LDT_PE_PM_ADDED, pm );
		}finally{
		
			peer_listeners_mon.exit();
		}
	
		TRTrackerAnnouncer tc = getTrackerClient();
		
		if ( tc != null ){
			
			tc.update( true );
		}
  	}
  
  	protected void
  	informStopped(
		PEPeerManager	pm )	// can be null if controller was already stopped....
  	{
  		if ( pm != null ){
		  
  			try{
  				peer_listeners_mon.enter();
			  
  				peer_listeners.dispatch( LDT_PE_PM_REMOVED, pm );
			  	
  			}finally{
			  	
  				peer_listeners_mon.exit();
  			}
  		}
				
  		try{
  			this_mon.enter();
	  
  			if ( tracker_client != null ){
			
				tracker_client.addListener( stopping_tracker_client_listener );

  				tracker_client.removeListener( tracker_client_listener );
		
 				download_manager_state.setTrackerResponseCache(	tracker_client.getTrackerResponseCache());
			
 				tracker_client.stop();
 				
  				tracker_client.destroy();
				
  				tracker_client = null;
  			}
		}finally{
			
			this_mon.exit();
		}
  	}
  
	public DownloadManagerStats
	getStats()
	{
		return( stats );
	}

	public boolean 
	isForceStart() 
	{
		return( controller.isForceStart());
	}	

	public void 
	setForceStart(
			boolean forceStart) 
	{
		controller.setForceStart( forceStart );
	}

	  /**
	   * Is called when a download is finished.
	   * Activates alerts for the user.
	   *
	   * @author Rene Leonhardt
	   */
	
	protected void 
	downloadEnded(
		boolean	never_downloaded )
	{
	    if ( !never_downloaded ){
		
	    	if (isForceStart()){
    	
	    		setForceStart(false);
	    	}

	    	setAssumedComplete(true);
	
	    	informDownloadEnded();
	    }
	    
	    TRTrackerAnnouncer	tc = tracker_client;
	    
	    if ( tc != null ){
	    	
	    	DiskManager	dm = getDiskManager();
	    	
	    		// only report "complete" if we really are complete, not a dnd completion event
	    	
	    	if ( dm != null && dm.getRemaining() == 0 ){
	    		
	    		tc.complete( never_downloaded );
	    	}
	    }
	}

 
	public void
	addDiskListener(
		DownloadManagerDiskListener	listener )
	{
		controller.addDiskListener( listener );
	}
		
	public void
	removeDiskListener(
		DownloadManagerDiskListener	listener )
	{
		controller.removeDiskListener( listener );
	}
  
	public int 
	getHealthStatus() 
	{
		int	state = getState();
	  
		PEPeerManager	peerManager	 = controller.getPeerManager();
	  
		TRTrackerAnnouncer tc = getTrackerClient();
	  
		if( tc != null && peerManager != null && (state == STATE_DOWNLOADING || state == STATE_SEEDING)) {
		  
			int nbSeeds = getNbSeeds();
			int nbPeers = getNbPeers();
			int nbRemotes = peerManager.getNbRemoteConnections();
			
			TRTrackerAnnouncerResponse	announce_response = tc.getLastResponse();
			
			int trackerStatus = announce_response.getStatus();
			
			boolean isSeed = (state == STATE_SEEDING);
      
			if( (nbSeeds + nbPeers) == 0) {
    	  
				if( isSeed ){
        	
					return WEALTH_NO_TRACKER;	// not connected to any peer and seeding
				}
        
				return WEALTH_KO;        // not connected to any peer and downloading
			}
      
      			// read the spec for this!!!!
      			// no_tracker =
      			//	1) if downloading -> no tracker
      			//	2) if seeding -> no connections		(dealt with above)
      
			if ( !isSeed ){
    	  
				if( 	trackerStatus == TRTrackerAnnouncerResponse.ST_OFFLINE || 
						trackerStatus == TRTrackerAnnouncerResponse.ST_REPORTED_ERROR){
	    	  
					return WEALTH_NO_TRACKER;
				}
			}
      
			if( nbRemotes == 0 ){
       
				TRTrackerScraperResponse scrape_response = getTrackerScrapeResponse();
				
				if ( scrape_response != null && scrape_response.isValid()){
					
						// if we're connected to everyone then report OK as we can't get
						// any incoming connections!
					
					if ( 	nbSeeds == scrape_response.getSeeds() &&
							nbPeers == scrape_response.getPeers()){
						
						return WEALTH_OK;
					}
				}
				
				return WEALTH_NO_REMOTE;
			}
      
			return WEALTH_OK;
      
		} else if (state == STATE_ERROR) {
			return WEALTH_ERROR;
		}else{
    	
			return WEALTH_STOPPED;
		}
	}
  
	public int 
	getNATStatus() 
	{
		int	state = getState();
	  
		PEPeerManager	peerManager	 = controller.getPeerManager();
	  
		TRTrackerAnnouncer tc = getTrackerClient();
	  
		if ( tc != null && peerManager != null && (state == STATE_DOWNLOADING || state == STATE_SEEDING)) {
		  			
			if ( peerManager.getNbRemoteConnections() > 0 ){
				
				return( ConnectionManager.NAT_OK );
			}
			
			long	last_good_time = peerManager.getLastRemoteConnectionTime();
		
			if ( last_good_time > 0 ){
				
					// half an hour's grace
				
				if ( SystemTime.getCurrentTime() - last_good_time < 30*60*1000 ){
				
					return( ConnectionManager.NAT_OK );
					
				}else{
					
					return( ConnectionManager.NAT_PROBABLY_OK );
				}
			}
			
			TRTrackerAnnouncerResponse	announce_response = tc.getLastResponse();
			
			int trackerStatus = announce_response.getStatus();
			
			if( 	trackerStatus == TRTrackerAnnouncerResponse.ST_OFFLINE || 
					trackerStatus == TRTrackerAnnouncerResponse.ST_REPORTED_ERROR){
	    	  
				return ConnectionManager.NAT_UNKNOWN;
			}
			
				// tracker's ok but no remotes - give it some time
			
			if ( SystemTime.getCurrentTime() - peerManager.getTimeStarted() < 3*60*1000 ){
				
				return ConnectionManager.NAT_UNKNOWN;
			}
			
			TRTrackerScraperResponse scrape_response = getTrackerScrapeResponse();
				
			if ( scrape_response != null && scrape_response.isValid()){
					
					// if we're connected to everyone then report OK as we can't get
					// any incoming connections!
					
				if ( 	peerManager.getNbSeeds() == scrape_response.getSeeds() &&
						peerManager.getNbPeers() == scrape_response.getPeers()){
						
					return ConnectionManager.NAT_UNKNOWN;
				}
			}
				
			return ConnectionManager.NAT_BAD;
	
		}else{
    	
			return ConnectionManager.NAT_UNKNOWN;
		}
	}
  
	public int 
	getPosition() 
	{
		return position;
	}

	public void 
	setPosition(
		int new_position ) 
	{
		informPositionChanged( new_position );
	}

	public void
	addTrackerListener(
		DownloadManagerTrackerListener	listener )
	{  		
		tracker_listeners.addListener( listener );
	}
  
	public void
	removeTrackerListener(
		DownloadManagerTrackerListener	listener )
	{
  		tracker_listeners.removeListener( listener );
	}
  
	protected void 
	deleteDataFiles() 
	{
		DiskManagerFactory.deleteDataFiles(torrent, torrent_save_location.getParent(), torrent_save_location.getName());
	}
  
	protected void 
	deleteTorrentFile() 
	{
		if ( torrentFileName != null ){
  		
			TorrentUtils.delete( new File(torrentFileName));
		}
	}
  

	public DownloadManagerState 
	getDownloadState()
	{	
		return( download_manager_state );
	}
  
  
  /** To retreive arbitrary objects against a download. */
  public Object getData (String key) {
  	if (data == null) return null;
    return data.get(key);
  }

  /** To store arbitrary objects against a download. */
  public void setData (String key, Object value) {
  	try{
  		peer_listeners_mon.enter();
  	
	  	if (data == null) {
	  	  data = new HashMap();
	  	}
	    if (value == null) {
	      if (data.containsKey(key))
	        data.remove(key);
	    } else {
	      data.put(key, value);
	    }
  	}finally{
  		
  		peer_listeners_mon.exit();
  	}
  }
  
  
  public boolean 
  isDataAlreadyAllocated() 
  {  
  	return data_already_allocated;  
  }
  
  public void 
  setDataAlreadyAllocated( 
  	boolean already_allocated ) 
  {
    data_already_allocated = already_allocated;
  }
    
  public void setSeedingRank(int rank) {
    iSeedingRank = rank;
  }
  
  public int getSeedingRank() {
    return iSeedingRank;
  }

  public long
  getCreationTime()
  {
  	return( creation_time );
  }

  public void
  setCreationTime(
  	long		t )
  {
  	creation_time	= t;
  }
  
  
  public boolean isAZMessagingEnabled() {  return az_messaging_enabled;  }
  
  public void 
  setAZMessagingEnabled( 
	boolean enable ) 
  {
    az_messaging_enabled = enable;
  }
  
  public void
  moveDataFiles(
	File	new_parent_dir )
  
  	throws DownloadManagerException
  {
	  if ( !isPersistent()){
		  
		  throw( new DownloadManagerException( "Download is not persistent" ));
	  }
	  		  
			// old file will be a "file" for simple torrents, a dir for non-simple
		  
	  File	old_file = getSaveLocation();
		  
	  try{
		  old_file = old_file.getCanonicalFile();
			  
		  new_parent_dir = new_parent_dir.getCanonicalFile();
			  
	  }catch( Throwable e ){
			  
		  Debug.printStackTrace(e);
			  
		  throw( new DownloadManagerException( "Failed to get canonical paths", e ));
	  }
		  
	  if ( new_parent_dir.equals( old_file.getParentFile())){
			  
		  	// null operation
		  
		  return;
	  }

	  DiskManager	dm = getDiskManager();
	  
	  if ( dm == null ){

		  if ( !old_file.exists()){
				  
		  	// files not created yet
				  
			  new_parent_dir.mkdirs();
				  
			  setTorrentSaveDir( new_parent_dir.toString());
			  
			  return;
		  }
			  
		  File new_file = new File( new_parent_dir, old_file.getName());
			 
		  try{
			  new_file	= new_file.getCanonicalFile();
			  
		  }catch( Throwable e ){
			  
			  Debug.printStackTrace(e);
		  }
		  
		  if ( old_file.equals( new_file )){
		  
			  // nothing to do
			  
		  }else if ((	!torrent.isSimpleTorrent()) &&
				  		new_file.getPath().startsWith( old_file.getPath())){
		    		
	            Logger.logTextResource(new LogAlert(LogAlert.REPEATABLE,
						LogAlert.AT_ERROR, "DiskManager.alert.movefilefails"),
						new String[] {old_file.toString(), "Target is sub-directory of files" });
	            
	            throw( new DownloadManagerException( "rename operation failed" ));
	            
		  }else{
			  if ( FileUtil.renameFile( old_file, new_file )){
		  			  
				  setTorrentSaveDir( new_parent_dir.toString());
			  
			  }else{
				  
				  throw( new DownloadManagerException( "rename operation failed" ));
			  }
		  }
	  }else{
		  
		  dm.moveDataFiles( new_parent_dir );
	  }
  }
  
  public void
  moveTorrentFile(
	File	new_parent_dir )
  
	throws DownloadManagerException
  {
	  if ( !isPersistent()){
		  
		  throw( new DownloadManagerException( "Download is not persistent" ));
	  }	  
	  
	  int	state = getState();

	  if ( 	state == DownloadManager.STATE_STOPPED ||
			state == DownloadManager.STATE_ERROR ){
			  
		  File	old_file = new File( getTorrentFileName() );
		  
		  if ( !old_file.exists()){
			  
			  Debug.out( "torrent file doesn't exist!" );
			  
			  return;
		  }
		  
		  File	new_file = new File( new_parent_dir, old_file.getName());
		  
		  try{
			  old_file = old_file.getCanonicalFile();
			  
			  new_parent_dir = new_parent_dir.getCanonicalFile();
			  
		  }catch( Throwable e ){
			  
			  Debug.printStackTrace(e);
			  
			  throw( new DownloadManagerException( "Failed to get canonical paths", e ));
		  }
		  
		  if ( new_parent_dir.equals( old_file.getParentFile())){
			  
			  	// null op
			  
			  return;
		  }
		  
		  if ( TorrentUtils.move( old_file, new_file )){
		  
			  setTorrentFileName( new_file.toString());
		  			  
		  }else{

			  throw( new DownloadManagerException( "rename operation failed" ));
		  }
	  }else{
			  
		  throw( new DownloadManagerException( "download not stopped or in error state" ));
	  }  
  }
  
  private byte[]
  getIdentity()
  {
 	  return( dl_identity );
  }
   
   /** @retun true, if the other DownloadManager has the same hash 
    * @see java.lang.Object#equals(java.lang.Object)
    */
   public boolean equals(Object obj)
   {
 		// check for object equivalence first!
   		
 	if ( this == obj ){
   		
 		return( true );
 	}
   	
 	if( obj instanceof DownloadManagerImpl ) {
     	
 	  DownloadManagerImpl other = (DownloadManagerImpl) obj;
           
 	  byte[] id1 = getIdentity();
 	  byte[] id2 = other.getIdentity();
       
 	  if ( id1 == null || id2 == null ){
       	
 		return( false );	// broken torrents - treat as different so shown
 							// as broken
 	  }
       
 	  return( Arrays.equals( id1, id2 ));
 	}
     
 	return false;
   }
   
   
   public int 
   hashCode() 
   {  
	   return dl_identity_hashcode;  
   }


	/* (non-Javadoc)
	 * @see org.gudy.azureus2.core3.logging.LogRelation#getLogRelationText()
	 */
	public String getRelationText() {
		return "TorrentDLM: '" + getDisplayName() + "'";
	}


	/* (non-Javadoc)
	 * @see org.gudy.azureus2.core3.logging.LogRelation#queryForClass(java.lang.Class)
	 */
	public Object[] getQueryableInterfaces() {
		return new Object[] { tracker_client };
	}
	
	public String toString() {
		String hash = "<unknown>";

		try {
			hash = ByteFormatter.encodeString(torrent.getHash());

		} catch (Throwable e) {
		}

		String status = DisplayFormatters.formatDownloadStatus(this);
		if (status.length() > 10) {
			status = status.substring(0, 10);
		}
		return "DownloadManagerImpl#" + getPosition()
				+ (getAssumedComplete() ? "s" : "d") + "@"
				+ Integer.toHexString(hashCode()) + "/"
				+ status + "/"
				+ getSize() + "/" + hash;
	}
	
	protected static class
	NoStackException
		extends Exception
	{
		protected
		NoStackException(
			String	str )
		{
			super( str );
		}
	}
	
	public void
	generateEvidence(
		IndentWriter		writer )
	{
		writer.println(toString());

		PEPeerManager pm = getPeerManager();

		try {
			writer.indent();

			writer.println("Save Dir: "
					+ Debug.secretFileName(getSaveLocation().toString()));
			
			if (current_peers.size() > 0) {
				writer.println("# Peers: " + current_peers.size());
			}
			
			if (current_pieces.size() > 0) {
				writer.println("# Pieces: " + current_pieces.size());
			}
			
			writer.println("Listeners: DownloadManager=" + listeners.size() + "; Disk="
				+ controller.getDiskListenerCount() + "; Peer=" + peer_listeners.size()
				+ "; Tracker=" + tracker_listeners.size());
			
			writer.println("SR: " + iSeedingRank);
			
			
			String sFlags = "";
			if (open_for_seeding) {
				sFlags += "Opened for Seeding; ";
			}
			
			if (data_already_allocated) {
				sFlags += "Data Already Allocated; ";
			}
			
			if (assumedComplete) {
				sFlags += "onlySeeding; ";
			}
			
			if (persistent) {
				sFlags += "peristent; ";
			}
			
			if (sFlags.length() > 0) {
				writer.println("Flags: " + sFlags);
			}
			

			if (pm != null) {
				pm.generateEvidence(writer);
			}
			
			if (stats != null) {
				writer.println("Completed? Live: " + stats.getDownloadCompleted(true)
						+ "; NonLive: " + stats.getDownloadCompleted(false) + "; other: "
						+ stats.getCompleted());
			}
			
			if (controller != null) {
				controller.generateEvidence(writer);
			}

		} finally {

			writer.exdent();
		}
	}

    public void downloadRemoved() {
    	// Data files don't exist, so we just don't do anything.
    	if (!getSaveLocation().exists()) {return;}
    	
    	DiskManager dm = this.getDiskManager();
    	if (dm != null) {
    		dm.downloadRemoved();
    		return;
    	}
    	    	
    	DiskManagerImpl.MoveDownloadInfo mdi = DiskManagerImpl.getMoveDownloadInfoOnRemoval(this, this);
    	if (mdi == null) {
    		return;
    	}
    	
    	boolean moved_files = false;
    	try {
    		this.moveDataFiles(new File(mdi.location));
    		moved_files = true;
    	}
    	catch (Exception e) {
    		Logger.log(new LogAlert(true, "Problem moving files to removed download directory", e));
    	}
    	
    	// This code will silently fail if the torrent file doesn't exist.
    	if (moved_files && mdi.move_torrent) {
  		    try {
	    		this.moveTorrentFile(new File(mdi.location));
	    	}
	    	catch (Exception e) {
	    		Logger.log(new LogAlert(true, "Problem moving torrent to removed download directory", e));
	    	}
    	}
    }
}
