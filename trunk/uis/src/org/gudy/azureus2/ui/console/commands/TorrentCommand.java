/*
 * Written and copyright 2001-2003 Tobias Minich. Distributed under the GNU
 * General Public License; see the README file. This code comes with NO
 * WARRANTY.
 * 
 * Torrent.java
 * 
 * Created on 23.03.2004
 *
 */
package org.gudy.azureus2.ui.console.commands;

import java.util.Iterator;
import java.util.List;

import org.gudy.azureus2.core3.download.DownloadManager;
import org.gudy.azureus2.core3.util.ByteFormatter;
import org.gudy.azureus2.ui.console.ConsoleInput;

/**
 * base class for objects which need to operate on specific torrents.
 * this class allows the torrent to be identified by hash, number or
 * 'all' and will pass the appropriate torrent(s) to the subclasses 'performCommand' method 
 * @author tobi
 */
public abstract class TorrentCommand extends IConsoleCommand {
	private final String primaryCommandName;
	private final String action;
	
	/**
	 * initializes the torrent command
	 * @param commandNames (the first item in the array is regarded as the primary command name)
	 * @param action a description to be used when this command is executed
	 */
	public TorrentCommand(String []commandNames, String action)
	{
		super(commandNames);
		this.primaryCommandName = commandNames[0];
		this.action = action;
	}
  
	protected String getCommandName()
	{
		return primaryCommandName;
	}
	protected String getAction()
	{
		return action;
	}
	protected abstract boolean performCommand(ConsoleInput ci, DownloadManager dm);

	public void execute(String commandName, ConsoleInput ci, List args)
	{
		if (!args.isEmpty()) {
		    String subcommand = (String) args.get(0);
			if (ci.torrents.isEmpty()) {
				ci.out.println("> Command '" + getCommandName() + "': No torrents in list (Maybe you forgot to 'show torrents' first).");
			} else {
				String name;
				DownloadManager dm;
				try {
					int number = Integer.parseInt(subcommand);
					if ((number > 0) && (number <= ci.torrents.size())) {
						dm = (DownloadManager) ci.torrents.get(number - 1);
						if (dm.getDisplayName() == null)
							name = "?";
						else
							name = dm.getDisplayName();
						if (performCommand(ci, dm))
							ci.out.println("> " + getAction() + " Torrent #" + subcommand + " (" + name + ") succeeded.");
						else
							ci.out.println("> " + getAction() + " Torrent #" + subcommand + " (" + name + ") failed.");
					} else
						ci.out.println("> Command '" + getCommandName() + "': Torrent #" + subcommand + " unknown.");
				} catch (NumberFormatException e) {
					if (subcommand.equalsIgnoreCase("all")) {
						Iterator torrent = ci.torrents.iterator();
						int nr = 0;
						while (torrent.hasNext()) {
							dm = (DownloadManager) torrent.next();
							if (dm.getDisplayName() == null)
								name = "?";
							else
								name = dm.getDisplayName();
							if (performCommand(ci, dm))
								ci.out.println("> " + getAction() + " Torrent #" + subcommand + " (" + name + ") succeeded.");
							else
								ci.out.println("> " + getAction() + " Torrent #" + subcommand + " (" + name + ") failed.");
						}
					} else if (subcommand.toUpperCase().startsWith("HASH")) {
						String hash = subcommand.substring(subcommand.indexOf(" ") + 1);
						List torrents = ci.gm.getDownloadManagers();
						boolean foundit = false;
						if (!torrents.isEmpty()) {
							Iterator torrent = torrents.iterator();
							while (torrent.hasNext()) {
								dm = (DownloadManager) torrent.next();
								if (hash.equals(ByteFormatter.nicePrintTorrentHash(dm.getTorrent(), true))) {
									if (dm.getDisplayName() == null)
										name = "?";
									else
										name = dm.getDisplayName();
									if (performCommand(ci, dm))
										ci.out.println("> " + getAction() + " Torrent " + hash + " (" + name + ") succeeded.");
									else
										ci.out.println("> " + getAction() + " Torrent " + hash + " (" + name + ") failed.");
									foundit = true;
									break;
								}
							}
							if (!foundit)
								ci.out.println("> Command '" + getCommandName() + "': Hash '" + hash + "' unknown.");
						}
					} else {
						ci.out.println("> Command '" + getCommandName() + "': Subcommand '" + subcommand + "' unknown.");
					}
				}
			}
		} else {
			ci.out.println("> Missing subcommand for '" + getCommandName() + "'\r\n> " + getCommandName() + " syntax: " + getCommandName() + " (<#>|all|hash <hash>)");
		}
	}
}
