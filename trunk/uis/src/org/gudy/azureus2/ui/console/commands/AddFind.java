/*
 * Written and copyright 2001-2003 Tobias Minich. Distributed under the GNU
 * General Public License; see the README file. This code comes with NO
 * WARRANTY.
 * 
 * AddFind.java
 * 
 * Created on 23.03.2004
 *
 */
package org.gudy.azureus2.ui.console.commands;

import java.io.File;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.OptionBuilder;
import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.torrentdownloader.TorrentDownloaderFactory;
import org.gudy.azureus2.ui.console.ConsoleInput;
import org.pf.file.FileFinder;

/**
 * this class allows the user to add and find torrents.
 * when adding, you may specify an output directory
 * when finding, it will cache the files it finds into the ConsoleInput object
 * so that they can then be added by id 
 * @author tobi, fatal
 */
public class AddFind extends OptionsConsoleCommand {
	
	public AddFind()
	{
		super( new String[] { "add", "a" } );
		
		OptionBuilder.withArgName("outputDir");
		OptionBuilder.withLongOpt("output");
		OptionBuilder.hasArg();
		OptionBuilder.withDescription("Output Directory");
		OptionBuilder.withType(File.class);		
		getOptions().addOption( OptionBuilder.create('o') );
		getOptions().addOption("r", "recurse", false, "recurse sub-directories.");
		getOptions().addOption("f", "find", false, "only find files, don't add.");
		getOptions().addOption("h", "help", false, "display help about this command");
		getOptions().addOption("l", "list", false, "list previous find results");
	}
	
	public String getCommandDescriptions()
	{
		return "add [addoptions] [.torrent path|url]\t\ta\tAdd a download from the given .torrent file path or url. Example: 'add /path/to/the.torrent' or 'add http://www.url.com/to/the.torrent'";
	}
	
	public void execute(String commandName, ConsoleInput ci, CommandLine commands) 
	{
		if( commands.hasOption('h') || commands.getArgs().length == 0 )
		{
			printHelp(ci.out, (String)null);
			return;
		}
		if( commands.hasOption('l') )
		{
			showAdds(ci);
			return;
		}
		String outputDir = ".";
		if (commands.hasOption('o'))
			outputDir = commands.getOptionValue('o');
		else
			try {
				outputDir = COConfigurationManager.getDirectoryParameter("Default save path");
			} catch (Exception e) {
				e.printStackTrace();
			}

		boolean scansubdir = commands.hasOption('r'); 
		boolean finding = commands.hasOption('f');

		String[] whatelse = commands.getArgs();
		for (int j = 0; j < whatelse.length; j++) {
			String arg = whatelse[j];
			
			// firstly check if it is a URL
			if (arg.toUpperCase().startsWith("HTTP://")) {
				ci.out.println("> Starting Download of " + arg + " ...");
				try {
					TorrentDownloaderFactory.downloadManaged(arg);
				} catch (Exception e) {
					ci.out.println("An error occurred while downloading torrent: " + e.getMessage());
					e.printStackTrace(ci.out);
				}
				continue;
			} 
			
			// see if the argument is an existing file or directory
			File test = new File(arg);
			if (test.exists()) {
				if (test.isDirectory()) {
					File[] toadd = FileFinder.findFiles(arg, "*.torrent;*.tor", scansubdir);								
					if ((toadd != null) && (toadd.length > 0)) {
						addFiles( ci, toadd, finding, outputDir );
					} else {
						ci.adds = null;
						ci.out.println("> Directory '" + arg + "' seems to contain no torrent files.");
					}
				} else {
					ci.gm.addDownloadManager(arg, outputDir);
					ci.out.println("> '" + arg + "' added.");
					ci.torrents.clear();
				}
				continue;
			} 

			// check to see if they are numeric and if so, try and add them from the 'adds' in ci
			try {
				int id = Integer.parseInt(arg);
				if( ci.adds != null && ci.adds.length > id )
				{
					String torrentPath = ci.adds[id].getAbsolutePath();
					ci.gm.addDownloadManager(torrentPath, outputDir);
					ci.out.println("> '" + torrentPath + "' added.");
					ci.torrents.clear();
				}
				else
				{
					ci.out.println("> No such file id '" + id + "'. Try \"add -l\" to list available files");
				}
				continue;
			} catch (NumberFormatException e)
			{
			}
			
			// last resort - try to process it as a directory/pattern eg: c:/torrents/*.torrent
			int separatorIndex = arg.lastIndexOf(System.getProperty("file.separator"));
			String dirName = arg.substring(0, separatorIndex);
			String filePattern = arg.substring(separatorIndex + 1);
			File []files = FileFinder.findFiles(dirName, filePattern, false);
			if ((files != null) && (files.length > 0)) {
				addFiles(ci, files, finding, outputDir );
			} else {
				ci.adds = null;
				ci.out.println("> No files found. Searched for '" + filePattern + "' in '" + dirName);
			}
		}
	}

	/**
	 * if finding is set, just print the available files and add them to the 'add' list inside the consoleinput object,
	 * otherwise actually add the torrents, saving to the specified output directory
	 * @param toadd
	 * @param finding
	 * @param outputDir
	 */
	private void addFiles(ConsoleInput ci, File[] toadd, boolean finding, String outputDir) {
		ci.out.println("> Found " + toadd.length + " files:");
		
		if( finding )
		{
			ci.adds = toadd;
			showAdds(ci);
		}
		else
		{
			for (int i = 0; i < toadd.length; i++) {
				ci.gm.addDownloadManager(toadd[i].getAbsolutePath(), outputDir);
				ci.out.println("> '" + toadd[i].getAbsolutePath() + "' added.");
				ci.torrents.clear();
			}
		}
	}

	/**
	 * prints out the files in the 'add' list that is stored in the console input object.
	 * @param ci
	 */
	private void showAdds(ConsoleInput ci) {
		if( ci.adds == null || ci.adds.length == 0 )
		{
			ci.out.println("No files found. Try \"add -f <path>\" first");
			return;
		}
		for (int i = 0; i < ci.adds.length; i++) {
			ci.out.print(">\t" + i + ":\t");
			try {
				ci.out.println(ci.adds[i].getCanonicalPath());
			} catch (Exception e) {
				ci.out.println(ci.adds[i].getAbsolutePath());
			}
		}
		ci.out.println("> To add, simply type 'add <id>'");
	}
}
