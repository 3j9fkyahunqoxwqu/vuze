/*
 * Created on 2 juil. 2003
 *
 */
package org.gudy.azureus2.ui.swt.views;

import java.util.Iterator;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.config.ParameterListener;
import org.gudy.azureus2.core3.util.Constants;
import org.gudy.azureus2.core3.util.DisplayFormatters;
import org.gudy.azureus2.core3.util.ByteFormatter;
import org.gudy.azureus2.core3.util.SystemTime;
import org.gudy.azureus2.core3.util.TorrentUtils;
import org.gudy.azureus2.core3.disk.DiskManager;
import org.gudy.azureus2.core3.download.DownloadManager;
import org.gudy.azureus2.core3.download.DownloadManagerStats;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.torrent.*;
import org.gudy.azureus2.core3.tracker.client.TRTrackerClient;
import org.gudy.azureus2.core3.tracker.client.TRTrackerScraperResponse;
import org.gudy.azureus2.ui.swt.Messages;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.mainwindow.Colors;
import org.gudy.azureus2.ui.swt.mainwindow.MainWindow;
import org.gudy.azureus2.ui.swt.maketorrent.*;
import org.gudy.azureus2.ui.swt.components.*;

/**
 * @author Olivier
 * 
 */
public class GeneralView extends AbstractIView implements ParameterListener {

  private Display display;
  private DownloadManager manager;
  boolean pieces[];
  int loopFactor;

  Composite genComposite;
  Group gFile;
  Canvas piecesImage;
  Image pImage;
  BufferedLabel piecesPercent;
  Canvas availabilityImage;
  Image aImage;
  BufferedLabel availabilityPercent;
  Group gTransfer;
  BufferedLabel timeElapsed;
  BufferedLabel timeRemaining;
  BufferedLabel download;
  BufferedLabel downloadSpeed;
  BufferedLabel upload;
  BufferedLabel uploadSpeed;
  Text maxUploads;
  BufferedLabel totalSpeed;
  BufferedLabel seeds;
  BufferedLabel peers;
  Group gInfo;
  BufferedLabel fileName;
  BufferedLabel fileSize;
  BufferedLabel saveIn;
  BufferedLabel hash;
  BufferedLabel tracker;
  BufferedLabel trackerUpdateIn;
  Menu menuTracker;
  MenuItem itemSelect;
  BufferedLabel trackerUrlValue;
  BufferedLabel pieceNumber;
  BufferedLabel pieceSize;
  BufferedLabel comment;
  BufferedLabel creation_date;
  BufferedLabel hashFails;
  BufferedLabel shareRatio;
  Button		updateButton;

  private int graphicsUpdate = COConfigurationManager.getIntParameter("Graphics Update");

  public GeneralView(DownloadManager manager) {
    this.manager = manager;
    pieces = new boolean[manager.getNbPieces()];
  }
  /* (non-Javadoc)
   * @see org.gudy.azureus2.ui.swt.IView#initialize(org.eclipse.swt.widgets.Composite)
   */
  public void initialize(Composite composite) {

    this.display = composite.getDisplay();

    genComposite = new Canvas(composite, SWT.NULL);
    GridLayout genLayout = new GridLayout();
    genLayout.marginHeight = 0;
    genLayout.marginWidth = 2;
    genLayout.numColumns = 1;
    genComposite.setLayout(genLayout);

    gFile = new Group(genComposite, SWT.SHADOW_OUT);
    GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
    gFile.setLayoutData(gridData);
    GridLayout fileLayout = new GridLayout();
    fileLayout.marginHeight = 2;
    fileLayout.numColumns = 3;
    gFile.setLayout(fileLayout);

    Label piecesInfo = new Label(gFile, SWT.LEFT);
    Messages.setLanguageText(piecesInfo, "GeneralView.section.downloaded");
    gridData = new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING);
    piecesInfo.setLayoutData(gridData);

    piecesImage = new Canvas(gFile, SWT.NULL);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.widthHint = 150;
    gridData.heightHint = 25;
    piecesImage.setLayoutData(gridData);

    piecesPercent = new BufferedLabel(gFile, SWT.RIGHT);
    gridData = new GridData(GridData.HORIZONTAL_ALIGN_END);
    gridData.widthHint = 50;
    piecesPercent.setLayoutData(gridData);

    Label availabilityInfo = new Label(gFile, SWT.LEFT);
    Messages.setLanguageText(availabilityInfo, "GeneralView.section.availability");
    gridData = new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING);
    availabilityInfo.setLayoutData(gridData);

    availabilityImage = new Canvas(gFile, SWT.NULL);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.widthHint = 150;
    gridData.heightHint = 25;
    availabilityImage.setLayoutData(gridData);
    Messages.setLanguageText(availabilityImage, "GeneralView.label.status.pieces_available.tooltip");

    availabilityPercent = new BufferedLabel(gFile, SWT.RIGHT);
    gridData = new GridData(GridData.HORIZONTAL_ALIGN_END);
    gridData.widthHint = 50;
    availabilityPercent.setLayoutData(gridData);
    Messages.setLanguageText(availabilityPercent, "GeneralView.label.status.pieces_available.tooltip");
    
    gTransfer = new Group(genComposite, SWT.SHADOW_OUT);
    Messages.setLanguageText(gTransfer, "GeneralView.section.transfer"); //$NON-NLS-1$
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gTransfer.setLayoutData(gridData);

    GridLayout layoutTransfer = new GridLayout();
    layoutTransfer.numColumns = 6;
    gTransfer.setLayout(layoutTransfer);

    Label label = new Label(gTransfer, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.timeelapsed"); //$NON-NLS-1$
    timeElapsed = new BufferedLabel(gTransfer, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    timeElapsed.setLayoutData(gridData);
    label = new Label(gTransfer, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.remaining"); //$NON-NLS-1$
    timeRemaining = new BufferedLabel(gTransfer, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    timeRemaining.setLayoutData(gridData);
    label = new Label(gTransfer, SWT.LEFT); //$NON-NLS-1$
    Messages.setLanguageText(label, "GeneralView.label.shareRatio");
    shareRatio = new BufferedLabel(gTransfer, SWT.LEFT); //$NON-NLS-1$
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    shareRatio.setLayoutData(gridData);

    label = new Label(gTransfer, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.downloaded"); //$NON-NLS-1$
    download = new BufferedLabel(gTransfer, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    download.setLayoutData(gridData);
    label = new Label(gTransfer, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.downloadspeed"); //$NON-NLS-1$
    downloadSpeed = new BufferedLabel(gTransfer, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    downloadSpeed.setLayoutData(gridData);
    label = new Label(gTransfer, SWT.LEFT); //$NON-NLS-1$
    Messages.setLanguageText(label, "GeneralView.label.hashfails");
    hashFails = new BufferedLabel(gTransfer, SWT.LEFT); //$NON-NLS-1$
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    hashFails.setLayoutData(gridData);
    
    label = new Label(gTransfer, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.uploaded"); //$NON-NLS-1$
    upload = new BufferedLabel(gTransfer, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    upload.setLayoutData(gridData);    
    label = new Label(gTransfer, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.uploadspeed"); //$NON-NLS-1$
    uploadSpeed = new BufferedLabel(gTransfer, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    uploadSpeed.setLayoutData(gridData);
    label = new Label(gTransfer, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.maxuploads"); //$NON-NLS-1$
    maxUploads = new Text(gTransfer, SWT.BORDER);
    gridData = new GridData();
    gridData.widthHint = 30;
    maxUploads.setLayoutData(gridData);
    maxUploads.setText(String.valueOf(manager.getStats().getMaxUploads()));
    maxUploads.addListener(SWT.Verify, new Listener() {
      public void handleEvent(Event e) {
        String text = e.text;
        char[] chars = new char[text.length()];
        text.getChars(0, chars.length, chars, 0);
        for (int i = 0; i < chars.length; i++) {
          if (!('0' <= chars[i] && chars[i] <= '9')) {
            e.doit = false;
            return;
          }
        }
      }
    });

    maxUploads.addListener(SWT.Modify, new Listener() {
      public void handleEvent(Event event) {
        try {
          int value = Integer.parseInt(maxUploads.getText());
          if (value < 2)
            value = 2;
          manager.getStats().setMaxUploads(value);
        }
        catch (Exception e) {}
      }
    });

    maxUploads.addListener(SWT.FocusOut, new Listener() {
      public void handleEvent(Event event) {
        try {
          int value = Integer.parseInt(maxUploads.getText());
          if (value < 2) {
            maxUploads.setText("2");
          }
        }
        catch (Exception e) {}
      }
    });

    label = new Label(gTransfer, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.seeds"); //$NON-NLS-1$
    seeds = new BufferedLabel(gTransfer, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    seeds.setLayoutData(gridData);

    label = new Label(gTransfer, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.peers"); //$NON-NLS-1$
    peers = new BufferedLabel(gTransfer, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    peers.setLayoutData(gridData);

    label = new Label(gTransfer, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.totalspeed"); //$NON-NLS-1$
    totalSpeed = new BufferedLabel(gTransfer, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    totalSpeed.setLayoutData(gridData);

    gInfo = new Group(genComposite, SWT.SHADOW_OUT);
    Messages.setLanguageText(gInfo, "GeneralView.section.info"); //$NON-NLS-1$
    gridData = new GridData(GridData.FILL_BOTH);
    gInfo.setLayoutData(gridData);

    GridLayout layoutInfo = new GridLayout();
    layoutInfo.numColumns = 4;
    gInfo.setLayout(layoutInfo);

    label = new Label(gInfo, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.filename"); //$NON-NLS-1$
    fileName = new BufferedLabel(gInfo, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    fileName.setLayoutData(gridData);

    label = new Label(gInfo, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.totalsize"); //$NON-NLS-1$
    fileSize = new BufferedLabel(gInfo, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    fileSize.setLayoutData(gridData);

    label = new Label(gInfo, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.savein"); //$NON-NLS-1$
    saveIn = new BufferedLabel(gInfo, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    saveIn.setLayoutData(gridData);

    label = new Label(gInfo, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.hash"); //$NON-NLS-1$
    hash = new BufferedLabel(gInfo, SWT.LEFT);
    Messages.setLanguageText(hash, "GeneralView.label.hash.tooltip", true);
    
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    hash.setLayoutData(gridData);
    	// click on hash -> copy to clipboard
    hash.setCursor(MainWindow.handCursor);
    hash.setForeground(Colors.blue);
    label.addMouseListener(new MouseAdapter() {
    	public void mouseDoubleClick(MouseEvent arg0) {
    		String hash_str = hash.getText();
    		if(hash_str != null && hash_str.length() != 0)
    			new Clipboard(display).setContents(new Object[] {hash_str.replaceAll(" ","")}, new Transfer[] {TextTransfer.getInstance()});
    	}
    	public void mouseDown(MouseEvent arg0) {
    		String hash_str = hash.getText();
    		if(hash_str != null && hash_str.length() != 0)
    			new Clipboard(display).setContents(new Object[] {hash_str.replaceAll(" ","")}, new Transfer[] {TextTransfer.getInstance()});
    	}
    });
    hash.addMouseListener(new MouseAdapter() {
    	public void mouseDoubleClick(MouseEvent arg0) {
    		String hash_str = hash.getText();
    		if(hash_str != null && hash_str.length() != 0)
    			new Clipboard(display).setContents(new Object[] {hash_str.replaceAll(" ","")}, new Transfer[] {TextTransfer.getInstance()});
    	}
    	public void mouseDown(MouseEvent arg0) {
    		String hash_str = hash.getText();
    		if(hash_str != null && hash_str.length() != 0)
    			new Clipboard(display).setContents(new Object[] {hash_str.replaceAll(" ","")}, new Transfer[] {TextTransfer.getInstance()});
    	}
    });
    
    label = new Label(gInfo, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.numberofpieces"); //$NON-NLS-1$
    pieceNumber = new BufferedLabel(gInfo, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    pieceNumber.setLayoutData(gridData);

    label = new Label(gInfo, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.size"); //$NON-NLS-1$
    pieceSize = new BufferedLabel(gInfo, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    pieceSize.setLayoutData(gridData);

    label = new Label(gInfo, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.trackerurl"); //$NON-NLS-1$
    label.setCursor(MainWindow.handCursor);
    label.setForeground(Colors.blue);
    label.addMouseListener(new MouseAdapter() {
      public void mouseDoubleClick(MouseEvent arg0) {
        String announce = trackerUrlValue.getText();
        if(announce != null && announce.length() != 0)
          new Clipboard(display).setContents(new Object[] {announce}, new Transfer[] {TextTransfer.getInstance()});
      }
      public void mouseDown(MouseEvent arg0) {
        String announce = trackerUrlValue.getText();
        if(announce != null && announce.length() != 0)
          new Clipboard(display).setContents(new Object[] {announce}, new Transfer[] {TextTransfer.getInstance()});
      }
    });
    
    menuTracker = new Menu(genComposite.getShell(),SWT.POP_UP);
    itemSelect = new MenuItem(menuTracker,SWT.CASCADE);
    Messages.setLanguageText(itemSelect, "GeneralView.menu.selectTracker");
    MenuItem itemEdit = new MenuItem(menuTracker,SWT.NULL);
    Messages.setLanguageText(itemEdit, "MyTorrentsView.menu.editTracker");
     
    itemEdit.addListener(
    	SWT.Selection,
    	new Listener()
    	{
    		public void 
    		handleEvent(Event e)
    		{
    			final TOTorrent	torrent = manager.getTorrent();
    		
	    		List	group = TorrentUtils.announceGroupsToList( torrent );
	    		
	    		new MultiTrackerEditor(null,group,
	    				new TrackerEditorListener()
	    				{
	    					public void
	    					trackersChanged(
	    							String	str,
									String	str2,
									List	group )
	    					{
	    						TorrentUtils.listToAnnounceGroups( group, torrent );
	    						
	    						try{
	    							TorrentUtils.writeToFile( torrent );
	    						}catch( Throwable e ){
	    							
	    							e.printStackTrace();
	    						}
	    						
	    						TRTrackerClient	tc = manager.getTrackerClient();
	    						
	    						if ( tc != null ){
	    							
	    							tc.resetTrackerUrl( true );
	    						}
	    					}
	    				}, true);	
    		}  		
    	});
    
    final Listener menuListener = new Listener() 
    {
      public void 
      handleEvent(Event e)
      {
         if( e.widget instanceof MenuItem) {
        	
          String text = ((MenuItem)e.widget).getText();
                   
          	
          TOTorrent	torrent = manager.getTorrent();
          	
          TorrentUtils.announceGroupsSetFirst(torrent,text);   
          	
          try{
          	TorrentUtils.writeToFile(torrent);
          	
          }catch( TOTorrentException f){
          		
          	f.printStackTrace();
          }
          	
          TRTrackerClient	tc = manager.getTrackerClient();
          	
          if ( tc != null ){
          		
          	tc.resetTrackerUrl( false );
          }    	
        }
      }
    };
    
    menuTracker.addListener(SWT.Show,new Listener() {
      public void handleEvent(Event e) {
    		Menu menuSelect = itemSelect.getMenu();
    		if(menuSelect != null && ! menuSelect.isDisposed()) {
    		  menuSelect.dispose();
    		}
    		if(manager == null || genComposite == null || genComposite.isDisposed())
    		  return;
     		List groups = TorrentUtils.announceGroupsToList(manager.getTorrent());        		
    		menuSelect = new Menu(genComposite.getShell(),SWT.DROP_DOWN);
    		itemSelect.setMenu(menuSelect);
    		Iterator iterGroups = groups.iterator();
    		while(iterGroups.hasNext()) {
    		  List trackers = (List) iterGroups.next();
    		  MenuItem menuItem = new MenuItem(menuSelect,SWT.CASCADE);
    		  Messages.setLanguageText(menuItem,"wizard.multitracker.group");
    		  Menu menu = new Menu(genComposite.getShell(),SWT.DROP_DOWN);
    		  menuItem.setMenu(menu);
    		  Iterator iterTrackers = trackers.iterator();
    		  while(iterTrackers.hasNext()) {
    		    String url = (String) iterTrackers.next();
    		    MenuItem menuItemTracker = new MenuItem(menu,SWT.CASCADE);
    		    menuItemTracker.setText(url);
    		    menuItemTracker.addListener(SWT.Selection,menuListener);
    		  }
    		}
      }
    });
    
    trackerUrlValue = new BufferedLabel(gInfo, SWT.LEFT);        
    
    trackerUrlValue.addMouseListener(new MouseAdapter() {
      public void mouseUp(MouseEvent event) {        
        if(event.button == 1) {
	        String url = trackerUrlValue.getText();
	        if(url.startsWith("http://" ) || url.startsWith("https://")) {
	          int pos = -1;
	          if((pos = url.indexOf("/announce")) != -1) {
	            url = url.substring(0,pos);
	            Program.launch(url);
	          }
        	}
        } else if(event.button == 3){
          menuTracker.setVisible(true);
        }
      }
    });
    
    
    label = new Label(gInfo, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.creationdate"); //$NON-NLS-1$
    creation_date = new BufferedLabel(gInfo, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 1;
    creation_date.setLayoutData(gridData);
    
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 1;
    trackerUrlValue.setLayoutData(gridData);

    
    label = new Label(gInfo, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.tracker"); //$NON-NLS-1$
    tracker = new BufferedLabel(gInfo, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.horizontalSpan = 3;
    tracker.setLayoutData(gridData);    
        
    label = new Label(gInfo, SWT.LEFT);
    Messages.setLanguageText(label, "GeneralView.label.updatein"); //$NON-NLS-1$
    trackerUpdateIn = new BufferedLabel(gInfo, SWT.LEFT);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    trackerUpdateIn.setLayoutData(gridData);
    

    updateButton = new Button(gInfo, SWT.LEFT);
    Messages.setLanguageText(updateButton, "GeneralView.label.trackerurlupdate"); //$NON-NLS-1$
    gridData = new GridData(GridData.HORIZONTAL_ALIGN_CENTER);
		updateButton.setLayoutData(gridData);
	    
		updateButton.addSelectionListener(new SelectionAdapter() {
	      public void widgetSelected(SelectionEvent event) {
	        manager.checkTracker();
	      }
	    });
    
    label = new Label(gInfo, SWT.LEFT);
    
    	// row
    
    label = new Label(gInfo, SWT.LEFT);
    gridData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
    label.setLayoutData(gridData);
    
    Messages.setLanguageText(label, "GeneralView.label.comment"); //$NON-NLS-1$
    comment = new BufferedLabel(gInfo, SWT.LEFT | SWT.WRAP);
    gridData = new GridData(GridData.FILL_BOTH);
    gridData.horizontalSpan = 3;
    comment.setLayoutData(gridData);
 
    
    piecesImage.addListener(SWT.Paint, new Listener() {
      public void handleEvent(Event e) {
        if (e.count == 0 && e.width > 0 && e.height > 0) {
          updatePiecesInfo(true);
        }
      }
    });
    availabilityImage.addListener(SWT.Paint, new Listener() {
      public void handleEvent(Event e) {
        if (e.count == 0 && e.width > 0 && e.height > 0) {
          updateAvailability();
        }
      }
    });

    if(System.getProperty("os.name").equals("Mac OS X")) {
      Shell shell = MainWindow.getWindow().getShell();
      Point size = shell.getSize();
      shell.setSize(size.x-1,size.y-1);
      shell.setSize(size);
    }
    
    genComposite.layout();
    //Utils.changeBackgroundComposite(genComposite,MainWindow.getWindow().getBackground());

    COConfigurationManager.addParameterListener("Graphics Update", this);
  }

  /* (non-Javadoc)
   * @see org.gudy.azureus2.ui.swt.IView#getComposite()
   */
  public Composite getComposite() {
    return genComposite;
  }

  /* (non-Javadoc)
   * @see org.gudy.azureus2.ui.swt.IView#refresh()
   */
  public void refresh() {
    if(getComposite() == null || getComposite().isDisposed())
      return;

    loopFactor++;
    if ((loopFactor % graphicsUpdate) == 0) {
      updateAvailability();
      updatePiecesInfo(false);
    }
    
    DiskManager dm = manager.getDiskManager();
    setTime(manager.getStats().getElapsedTime(), 
            DisplayFormatters.formatETA(manager.getStats().getETA()) +
            ((dm != null) ? " " + DisplayFormatters.formatByteCountToKiBEtc(dm.getRemaining()) : "")  );
    TRTrackerScraperResponse hd = manager.getTrackerScrapeResponse();
    String seeds = manager.getNbSeeds() +" "+ MessageText.getString("GeneralView.label.connected");
    String peers = manager.getNbPeers() +" "+ MessageText.getString("GeneralView.label.connected");
    if(hd != null && hd.isValid()) {
      seeds += " (" + hd.getSeeds() +" "+ MessageText.getString("GeneralView.label.in_swarm") + ")";
      peers += " (" + hd.getPeers() +" "+ MessageText.getString("GeneralView.label.in_swarm") + ")";
    } else {
      //seeds += " (?)";
      //peers += " (?)";
    }
    String _shareRatio = "";
    int sr = manager.getStats().getShareRatio();
    
    if(sr == -1) _shareRatio = Constants.INFINITY_STRING;
    if(sr >  0){ 
      String partial = "" + sr%1000;
      while(partial.length() < 3) partial = "0" + partial;
      _shareRatio = (sr/1000) + "." + partial;
    
    }
    DownloadManagerStats	stats = manager.getStats();
    
    setStats(
		DisplayFormatters.formatDownloaded(stats),
		DisplayFormatters.formatByteCountToKiBEtc(stats.getUploaded()),
		DisplayFormatters.formatByteCountToKiBEtcPerSec(stats.getDownloadAverage()),
		DisplayFormatters.formatByteCountToKiBEtcPerSec(stats.getUploadAverage()),
		DisplayFormatters.formatByteCountToKiBEtcPerSec(stats.getTotalAverage()),
      	seeds,
      	peers,
		DisplayFormatters.formatHashFails(manager),
      _shareRatio);
      
    setTracker(manager.getTrackerStatus(), manager.getTrackerTime(),manager.getTrackerClient());
    
    setInfos(
      manager.getName(),
	  DisplayFormatters.formatByteCountToKiBEtc(manager.getSize()),
      manager.getSavePath(),
      ByteFormatter.nicePrintTorrentHash(manager.getTorrent()),
      manager.getNbPieces(),
      manager.getPieceLength(),
      manager.getTorrentComment(),
	  DisplayFormatters.formatDate(manager.getTorrentCreationDate()*1000));
  }

  /* (non-Javadoc)
   * @see org.gudy.azureus2.ui.swt.IView#delete()
   */
  public void delete() {
	if (aImage != null)
		aImage.dispose();
	aImage = null;
	if (pImage != null)
		pImage.dispose();
	pImage = null;
  Utils.disposeComposite(genComposite);    
    COConfigurationManager.removeParameterListener("Graphics Update", this);
  }

  public String getData() {
    return "GeneralView.title.short"; //$NON-NLS-1$
  }

  /* (non-Javadoc)
   * @see org.gudy.azureus2.ui.swt.IView#getFullTitle()
   */
  public String getFullTitle() {
    return MessageText.getString("GeneralView.title.full"); //$NON-NLS-1$
  }

  public synchronized void updateAvailability() {
    if (manager.getPeerManager() == null) {
      if (availabilityPercent.getText() != "")
        availabilityPercent.setText("");
      return;
    }
    final int[] available = manager.getPeerManager().getAvailability();
    if (display == null || display.isDisposed())
      return;

    if (availabilityImage == null || availabilityImage.isDisposed()) {
      return;
    }
    Rectangle bounds = availabilityImage.getClientArea();
    GC gc = new GC(availabilityImage);
    if (aImage != null && !aImage.isDisposed())
      aImage.dispose();
    aImage = new Image(display, bounds.width, bounds.height);
    GC gcImage = new GC(aImage);
    gcImage.setForeground(Colors.grey);
    gcImage.drawRectangle(0, 0, bounds.width-1, bounds.height-1);
    int allMin = 0;
    int allMax = 0;
    int total = 0;
    String sTotal = "000"; //$NON-NLS-1$
    if (available != null) {
      int xMax = bounds.width - 2;
      int yMax = bounds.height - 2;
      if (xMax < 10 || yMax < 5)
        return;

      allMin = available[0];
      allMax = available[0];
      int nbPieces = available.length;
      for (int i = 0; i < nbPieces; i++) {
        if (available[i] < allMin)
          allMin = available[i];
        if (available[i] > allMax)
          allMax = available[i];
      }
      int maxAboveMin = allMax - allMin;
      if (maxAboveMin == 0) {
        // all the same.. easy paint
        gcImage.setBackground(Colors.blues[allMin == 0 ? Colors.BLUES_LIGHTEST : Colors.BLUES_DARKEST]);
        gcImage.fillRectangle(1, 1, xMax, yMax);
      } else {
        for (int i = 0; i < nbPieces; i++) {
          if (available[i] > allMin)
            total++;
        }
        total = (total * 1000) / nbPieces;
        sTotal = "" + total;
        if (total < 10) sTotal = "0" + sTotal;
        if (total < 100) sTotal = "0" + sTotal;
  
        for (int i = 0; i < xMax; i++) {
          int a0 = (i * nbPieces) / xMax;
          int a1 = ((i + 1) * nbPieces) / xMax;
          if (a1 == a0)
            a1++;
          if (a1 > nbPieces)
            a1 = nbPieces;
          int max = 0;
          int min = available[a0];
          int Pi = 1000;
          for (int j = a0; j < a1; j++) {
            if (available[j] > max)
              max = available[j];
            if (available[j] < min)
              min = available[j];
            Pi *= available[j];
            Pi /= (available[j] + 1);
          }
          int pond = Pi;
          if (max == 0)
            pond = 0;
          else {
            int PiM = 1000;
            for (int j = a0; j < a1; j++) {
              PiM *= (max + 1);
              PiM /= max;
            }
            pond *= PiM;
            pond /= 1000;
            pond *= (max - min);
            pond /= 1000;
            pond += min;
          }
          int index;
          if (pond <= 0 || allMax == 0) {
            index = 0;
          } else {
            // we will always have allMin, so subtract that
            index = (pond - allMin) * (Colors.BLUES_DARKEST - 1) / maxAboveMin + 1;
            // just in case?
            if (index > Colors.BLUES_DARKEST) {
              index = Colors.BLUES_DARKEST;
            }
          }
            
          gcImage.setBackground(Colors.blues[index]);
          gcImage.fillRectangle(i+1, 1, 1, yMax);
        }
      }
    }
    gcImage.dispose();
    if (availabilityPercent == null || availabilityPercent.isDisposed()) {
      gc.dispose();
      return;
    }
    availabilityPercent.setText(allMin + "." + sTotal);
    gc.drawImage(aImage, bounds.x, bounds.y);
    gc.dispose();
  }

  public synchronized void updatePiecesInfo(boolean bForce) {
    if (display == null || display.isDisposed())
      return;

    if (piecesImage == null || piecesImage.isDisposed())
      return;
    boolean valid = !bForce;
    if (valid) {
      boolean newPieces[] = manager.getPiecesStatus();
      if (newPieces == null) {
        pieces = null;
      } else {
        for (int i = 0; i < pieces.length; i++) {
          if (pieces[i] != newPieces[i]) {
            valid = false;
            pieces[i] = newPieces[i];
          }
        }
      }
    } else {
      // clone so it doesn't auto-update..
      try {
        pieces = (boolean[])manager.getPiecesStatus().clone();
      } catch (Exception e) {
        e.printStackTrace();
        pieces = null;
      }
    }

    if (!valid) {
      Rectangle bounds = piecesImage.getClientArea();
      GC gc = new GC(piecesImage);
      if (pImage != null && !pImage.isDisposed())
        pImage.dispose();
      int xMax = bounds.width - 2;
      int yMax = bounds.height - 2 - 6;
      if (xMax < 10 || yMax < 5)
        return;
      pImage = new Image(display, bounds.width, bounds.height);
      GC gcImage = new GC(pImage);
      gcImage.setForeground(Colors.grey);
      gcImage.drawRectangle(0, 0, bounds.width-1, bounds.height-1);
      gcImage.drawLine(1,6,xMax,6);

      int total = 0;
      if (pieces != null) {
        int nbPieces = pieces.length;
        for (int i = 0; i < nbPieces; i++) {
          if (pieces[i])
            total++;
        }
        for (int i = 0; i < xMax; i++) {
          int a0 = (i * nbPieces) / xMax;
          int a1 = ((i + 1) * nbPieces) / xMax;
          if (a1 == a0)
            a1++;
          if (a1 > nbPieces)
            a1 = nbPieces;
          int nbAvailable = 0;
          for (int j = a0; j < a1; j++) {
            if (pieces[j]) {
              nbAvailable++;
            }
            int index = (nbAvailable * Colors.BLUES_DARKEST) / (a1 - a0);
            gcImage.setBackground(Colors.blues[index]);
            gcImage.fillRectangle(i+1,7,1,yMax);
          }
        }
  
        total = (total * 1000) / nbPieces;
      } else {
        total = manager.getStats().getDownloadCompleted(true);
      }
      
      // draw file % bar above
      int limit = (xMax * total) / 1000;
      gcImage.setBackground(Colors.colorProgressBar);
      gcImage.fillRectangle(1,1,limit,5);
      if (limit < xMax) {
        gcImage.setBackground(Colors.blues[Colors.BLUES_LIGHTEST]);
        gcImage.fillRectangle(limit+1,1,xMax-limit,5);
      }
      

      gcImage.dispose();

      if (piecesPercent != null && !piecesPercent.isDisposed())
        piecesPercent.setText((total / 10) + "." + (total % 10) + " %"); //$NON-NLS-1$ //$NON-NLS-2$

      if (pImage == null || pImage.isDisposed()) {
        gc.dispose();
        return;
      }
      gc.drawImage(pImage, bounds.x, bounds.y);
      gc.dispose();
    }
  }

  public void setTime(String elapsed, String remaining) {
    timeElapsed.setText( elapsed );
    timeRemaining.setText( remaining);
  }

  public void setStats(String _dl, String _ul, String _dls, String _uls, String _ts, String _s, String _p,String _hashFails,String _shareRatio) {
    if (display == null || display.isDisposed())
      return;

    final String dls = _dls;
    final String uls = _uls;
    final String dl = _dl;
    final String ul = _ul;
    final String ts = _ts;
    final String s = _s;
    final String p = _p;
    
	download.setText( dl );
	downloadSpeed.setText( dls );
	upload.setText( ul );
	uploadSpeed.setText( uls );
	totalSpeed.setText( ts );
	seeds.setText( s); //$NON-NLS-1$
	peers.setText( p); //$NON-NLS-1$
	hashFails.setText( _hashFails);
	shareRatio.setText( _shareRatio);     
  }

  public void setTracker( String status,  int time, TRTrackerClient trackerClient ){
    if (display == null || display.isDisposed())
      return;
	tracker.setText( status);
		
	if ( time < 0 ){
		
		trackerUpdateIn.setText( MessageText.getString("GeneralView.label.updatein.querying"));
		
	}else{
	
	    int minutes = time / 60;
	    int seconds = time % 60;
	    String strSeconds = "" + seconds;
 	    if (seconds < 10){
 	    
      		strSeconds = "0" + seconds; //$NON-NLS-1$
 	    }
 	    
		trackerUpdateIn.setText(  minutes + ":" + strSeconds); 
	}
    
    boolean	update_state;
    
    if(trackerClient != null){
    	
    	String trackerURL = trackerClient.getTrackerUrl();
    
    	if ( trackerURL != null ){
    	
				trackerUrlValue.setText( trackerURL);
				if((trackerURL.startsWith("http://")||trackerURL.startsWith("https://")) && (trackerURL.indexOf("/announce") != -1)) {
				  trackerUrlValue.setForeground(Colors.blue);
				  trackerUrlValue.setCursor(MainWindow.handCursor);
				  Messages.setLanguageText(trackerUrlValue, "GeneralView.label.trackerurlopen.tooltip", true);
				} else {
				  trackerUrlValue.setForeground(null);
				  trackerUrlValue.setCursor(null);
				  Messages.setLanguageText(trackerUrlValue, null);	
				  trackerUrlValue.setToolTipText(null);
				}
    	}
    	
    	update_state = ((SystemTime.getCurrentTime()/1000 - trackerClient.getLastUpdateTime() >= TRTrackerClient.REFRESH_MINIMUM_SECS ));
    	
    }else{
    	update_state = false;
    }
    
    if ( updateButton.getEnabled() != update_state ){
    
    	updateButton.setEnabled( update_state );
    }
  }

  public void setInfos(
    final String _fileName,
    final String _fileSize,
    final String _path,
    final String _hash,
    final int _pieceNumber,
    final String _pieceLength,
    final String _comment,
	final String _creation_date ) {
    if (display == null || display.isDisposed())
      return;
    display.asyncExec(new Runnable() {
      public void run() {
		fileName.setText(_fileName);
		fileSize.setText( _fileSize);
		saveIn.setText( _path);
		hash.setText( _hash);
		pieceNumber.setText( "" + _pieceNumber); //$NON-NLS-1$
		pieceSize.setText( _pieceLength);
		comment.setText( _comment);
		creation_date.setText( _creation_date );
      }
    });
  }
 

  public void parameterChanged(String parameterName) {
    graphicsUpdate = COConfigurationManager.getIntParameter("Graphics Update");
  }
}
