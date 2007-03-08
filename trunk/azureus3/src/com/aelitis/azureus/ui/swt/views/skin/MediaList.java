/**
 * Copyright (C) 2006 Aelitis, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * AELITIS, SAS au capital de 63.529,40 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 *
 */

package com.aelitis.azureus.ui.swt.views.skin;

import java.io.ByteArrayInputStream;
import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.gudy.azureus2.core3.download.DownloadManager;
import org.gudy.azureus2.core3.global.GlobalManager;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.torrent.TOTorrent;
import org.gudy.azureus2.core3.util.*;
import org.gudy.azureus2.ui.swt.ImageRepository;
import org.gudy.azureus2.ui.swt.Messages;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.mainwindow.Colors;
import org.gudy.azureus2.ui.swt.views.table.TableRowSWT;

import com.aelitis.azureus.core.AzureusCore;
import com.aelitis.azureus.core.AzureusCoreFactory;
import com.aelitis.azureus.core.torrent.MetaDataUpdateListener;
import com.aelitis.azureus.core.torrent.PlatformTorrentUtils;
import com.aelitis.azureus.ui.common.table.TableRowCore;
import com.aelitis.azureus.ui.common.table.TableSelectionAdapter;
import com.aelitis.azureus.ui.swt.skin.*;
import com.aelitis.azureus.ui.swt.skin.SWTSkinButtonUtility.ButtonListenerAdapter;
import com.aelitis.azureus.ui.swt.utils.PublishUtils;
import com.aelitis.azureus.ui.swt.views.TorrentListView;
import com.aelitis.azureus.ui.swt.views.TorrentListViewListener;
import com.aelitis.azureus.ui.swt.views.list.ListRow;

/**
 * @author TuxPaper
 * @created Oct 12, 2006
 *
 */
public class MediaList
	extends SkinView
{
	private static final int ASYOUTYPE_UPDATEDELAY = 150;

	private SWTSkinObjectText lblCountAreaNotOurs;

	private SWTSkinObjectText lblCountAreaOurs;

	private TorrentListView view;

	private String PREFIX = "my-media-";

	private SWTSkinButtonUtility btnShare;

	private SWTSkinButtonUtility btnStop;

	private SWTSkinButtonUtility btnDelete;

	private SWTSkinButtonUtility btnDetails;

	private AzureusCore core;

	private SWTSkinButtonUtility btnComments;

	private SWTSkinButtonUtility btnPlay;

	private SWTSkinObjectImage skinImgThumb;

	private SWTSkinObjectText skinDetailInfo;

	private MetaDataUpdateListener listener;

	protected String sLastSearch = "";

	private Text txtFilter;

	private boolean bRegexSearch;

	private Label lblX;

	private TimerEvent searchUpdateEvent;

	// @see com.aelitis.azureus.ui.swt.views.skin.SkinView#showSupport(com.aelitis.azureus.ui.swt.skin.SWTSkinObject, java.lang.Object)
	public Object showSupport(SWTSkinObject skinObject, Object params) {
		final SWTSkin skin = skinObject.getSkin();
		core = AzureusCoreFactory.getSingleton();

		final Composite cData = (Composite) skinObject.getControl();
		Composite cHeaders = null;

		skinObject = skin.getSkinObject(PREFIX + "list-headers");
		if (skinObject != null) {
			cHeaders = (Composite) skinObject.getControl();
		}

		view = new TorrentListView(core, skin, skin.getSkinProperties(), cHeaders,
				null, cData, TorrentListView.VIEW_MY_MEDIA, false, true) {
			public boolean isOurDownload(DownloadManager dm) {
				if (sLastSearch.length() == 0) {
					return true;
				}

				boolean bOurs = true;
				try {
					String[][] names = {
						{
							"",
							dm.getDisplayName()
						},
						{
							"t:",
							dm.getTorrent().getAnnounceURL().getHost()
						},
						{
							"st:",
							"" + dm.getState()
						}
					};

					String name = names[0][1];
					String tmpSearch = sLastSearch;

					for (int i = 0; i < names.length; i++) {
						if (tmpSearch.startsWith(names[i][0])) {
							tmpSearch = tmpSearch.substring(names[i][0].length());
							name = names[i][1];
						}
					}

					String s = bRegexSearch ? tmpSearch : "\\Q"
							+ tmpSearch.replaceAll("[|;]", "\\\\E|\\\\Q") + "\\E";
					Pattern pattern = Pattern.compile(s, Pattern.CASE_INSENSITIVE);

					if (!pattern.matcher(name).find()) {
						bOurs = false;
					}
				} catch (Exception e) {
					// Future: report PatternSyntaxException message to user.
				}
				return bOurs;
			}

			public void updateUI() {
				super.updateUI();

				Control control = skinDetailInfo.getControl();
				if (control == null || control.isDisposed() || !control.isVisible()) {
					return;
				}

				if (view.getSelectedRows().length != 1) {
					updateDetailsInfo();
				}
			}
		};

		btnShare = TorrentListViewsUtils.addShareButton(skin, PREFIX, view);
		btnStop = TorrentListViewsUtils.addStopButton(skin, PREFIX, view);
		btnDetails = TorrentListViewsUtils.addDetailsButton(skin, PREFIX, view);
		btnComments = TorrentListViewsUtils.addCommentsButton(skin, PREFIX, view);
		btnPlay = TorrentListViewsUtils.addPlayButton(skin, PREFIX, view, false,
				true);

		view.addListener(new TorrentListViewListener() {
			boolean countChanging = false;

			// @see com.aelitis.azureus.ui.swt.views.TorrentListViewListener#stateChanged(org.gudy.azureus2.core3.download.DownloadManager)

			public void stateChanged(final DownloadManager manager) {
				Utils.execSWTThread(new AERunnable() {
					public void runSupport() {
						if (manager == null) {
							return;
						}
						TableRowSWT row = view.getRowSWT(manager);
						if (row == null) {
							return;
						}
						if (manager.isDownloadComplete(false)) {
							row.setForeground(null);
						} else {
							Color c = skin.getSkinProperties().getColor(
									"color.library.incomplete");
							row.setForeground(c);
						}

					}
				});
			}

			// @see com.aelitis.azureus.ui.swt.views.TorrentListViewListener#countChanged()
			public void countChanged() {
				if (countChanging) {
					return;
				}

				countChanging = true;
				Utils.execSWTThread(new AERunnable() {
					public void runSupport() {
						countChanging = false;

						long totalOurs = 0;
						long totalNotOurs = 0;

						GlobalManager globalManager = core.getGlobalManager();
						Object[] dms = globalManager.getDownloadManagers().toArray();

						for (int i = 0; i < dms.length; i++) {
							DownloadManager dm = (DownloadManager) dms[i];
							if (dm.isDownloadComplete(false)) {
								if (PublishUtils.isPublished(dm)) {
									totalOurs++;
								} else {
									totalNotOurs++;
								}
							}
						}

						if (lblCountAreaOurs != null) {
							lblCountAreaOurs.setText(MessageText.getString("MainWindow.v3."
									+ PREFIX + "ours.count", new String[] {
								"" + totalOurs
							}));
						}
						if (lblCountAreaNotOurs != null) {
							lblCountAreaNotOurs.setText(MessageText.getString(
									"MainWindow.v3." + PREFIX + "notours.count", new String[] {
										"" + totalNotOurs
									}));
							lblCountAreaNotOurs.getControl().getParent().layout(true, true);
						}

						int count = view.getSelectedRowsSize();
						if (count == 0 || count > 1) {
							updateDetailsInfo();
						}
					}
				});
			}
		});

		skinObject = skin.getSkinObject(PREFIX + "delete");
		if (skinObject instanceof SWTSkinObjectContainer) {
			btnDelete = new SWTSkinButtonUtility(skinObject);

			btnDelete.addSelectionListener(new ButtonListenerAdapter() {
				public void pressed(SWTSkinButtonUtility buttonUtility) {
					TableRowCore[] selectedRows = view.getSelectedRows();
					for (int i = 0; i < selectedRows.length; i++) {
						DownloadManager dm = (DownloadManager) selectedRows[i].getDataSource(true);
						TorrentListViewsUtils.removeDownload(dm, view, true, true);
					}
				}
			});
		}

		SWTSkinButtonUtility[] buttonsNeedingRow = {
			btnDelete,
			btnStop,
		};
		SWTSkinButtonUtility[] buttonsNeedingPlatform = {
			btnDetails,
			btnComments,
			btnShare,
		};
		SWTSkinButtonUtility[] buttonsNeedingSingleSelection = {
			btnDetails,
			btnComments,
			btnShare,
		};
		TorrentListViewsUtils.addButtonSelectionDisabler(view, buttonsNeedingRow,
				buttonsNeedingPlatform, buttonsNeedingSingleSelection, btnStop);

		view.addSelectionListener(new TableSelectionAdapter() {
			public void selected(TableRowCore[] rows) {
				boolean bDisable = view.getSelectedRowsSize() != 1;
				if (!bDisable) {
					DownloadManager dm = (DownloadManager) view.getSelectedDataSources()[0];
					if (dm != null) {
						bDisable = !dm.isDownloadComplete(false);
					}
				}
				btnPlay.setDisabled(bDisable);
			}
		}, false);

		skinObject = skin.getSkinObject(PREFIX + "bigthumb");
		if (skinObject instanceof SWTSkinObjectImage) {
			listener = new MetaDataUpdateListener() {
				public void metaDataUpdated(TOTorrent torrent) {
					ListRow rowFocused = view.getRowFocused();
					if (rowFocused != null) {
						DownloadManager dm = (DownloadManager) rowFocused.getDataSource(true);
						if (dm.getTorrent().equals(torrent)) {
							update();
						}
					}
				}
			};
			PlatformTorrentUtils.addListener(listener);

			skinImgThumb = (SWTSkinObjectImage) skinObject;
			view.addSelectionListener(new TableSelectionAdapter() {
				public void deselected(TableRowCore[] rows) {
					update();
				}

				public void selected(TableRowCore[] rows) {
					update();
				}

				public void focusChanged(TableRowCore focusedRow) {
					update();
				}

			}, false);
		}

		skinObject = skin.getSkinObject(PREFIX + "detail-info");
		if (skinObject instanceof SWTSkinObjectText) {
			skinDetailInfo = (SWTSkinObjectText) skinObject;
			view.addSelectionListener(new TableSelectionAdapter() {
				public void deselected(TableRowCore[] rows) {
					updateDetailsInfo();
				}

				public void selected(TableRowCore[] rows) {
					updateDetailsInfo();
				}

				public void focusChanged(TableRowCore focusedRow) {
					updateDetailsInfo();
				}
			}, true);
		}

		skinObject = skin.getSkinObject(PREFIX + "filter-box");
		if (skinObject != null) {
			Control control = skinObject.getControl();
			if (control instanceof Composite) {
				final Composite composite = (Composite) control;
				txtFilter = new Text(composite, SWT.BORDER);
				txtFilter.addModifyListener(new ModifyListener() {
					public void modifyText(ModifyEvent e) {
						sLastSearch = ((Text) e.widget).getText();
						updateLastSearch();
					}
				});
				FormData formData = Utils.getFilledFormData();
				formData.top = null;
				txtFilter.setLayoutData(formData);
				composite.layout();

				FontData[] fontData = txtFilter.getFont().getFontData();
				int h = txtFilter.getClientArea().height - (Constants.isOSX ? 0 : 2);
				int size = Utils.pixelsToPoint(h,
						txtFilter.getDisplay().getDPI().y);

				Font font = null;
				do {
					if (font != null) {
						font.dispose();
					}
					fontData[0].setHeight(size);

					font = new Font(txtFilter.getDisplay(), fontData);
					txtFilter.setFont(font);

					size--;
				} while (txtFilter.getLineHeight() > h);

				composite.getParent().layout();

				final Font fFont = font;

				txtFilter.addDisposeListener(new DisposeListener() {
					public void widgetDisposed(DisposeEvent e) {
						if (fFont != null && !fFont.isDisposed()) {
							txtFilter.setFont(null);
							fFont.dispose();
						}
					}
				});

				view.addKeyListener(new KeyListener() {
					public void keyReleased(KeyEvent e) {
					}

					public void keyPressed(KeyEvent e) {
						if (e.keyCode != SWT.BS) {
							if ((e.stateMask & (~SWT.SHIFT)) != 0 || e.character < 32) {
								return;
							}
						}

						if (e.keyCode == SWT.BS) {
							if (e.stateMask == SWT.CONTROL) {
								sLastSearch = "";
							} else if (sLastSearch.length() > 0) {
								sLastSearch = sLastSearch.substring(0, sLastSearch.length() - 1);
							}
						} else {
							sLastSearch += String.valueOf(e.character);
						}

						if (txtFilter != null && !txtFilter.isDisposed()) {
							txtFilter.setFocus();
						}
						updateLastSearch();

						e.doit = false;
					}

				});
			}
		}

		return null;
	}

	/**
	 * 
	 */
	protected void updateLastSearch() {
		if (txtFilter != null && !txtFilter.isDisposed()) {
			if (!sLastSearch.equals(txtFilter.getText())) {
				txtFilter.setText(sLastSearch);
				txtFilter.setSelection(sLastSearch.length());
			}

			if (sLastSearch.length() > 0) {
				if (bRegexSearch) {
					try {
						Pattern.compile(sLastSearch, Pattern.CASE_INSENSITIVE);
						txtFilter.setBackground(Colors.colorAltRow);
						Messages.setLanguageTooltip(txtFilter,
								"MyTorrentsView.filter.tooltip");
					} catch (Exception e) {
						txtFilter.setBackground(Colors.colorErrorBG);
						txtFilter.setToolTipText(e.getMessage());
					}
				} else {
					txtFilter.setBackground(null);
					Messages.setLanguageTooltip(txtFilter,
							"MyTorrentsView.filter.tooltip");
				}
			}
		}
		if (lblX != null && !lblX.isDisposed()) {
			Image img = ImageRepository.getImage(sLastSearch.length() > 0 ? "smallx"
					: "smallx-gray");

			lblX.setImage(img);
		}

		if (searchUpdateEvent != null) {
			searchUpdateEvent.cancel();
		}
		searchUpdateEvent = SimpleTimer.addEvent("SearchUpdate",
				SystemTime.getOffsetTime(ASYOUTYPE_UPDATEDELAY),
				new TimerEventPerformer() {
					public void perform(TimerEvent event) {
						searchUpdateEvent = null;
						doFilter();
					}
				});
	}

	/**
	 * 
	 */
	protected void doFilter() {
		view.tableStructureChanged();
	}

	private void updateDetailsInfo() {
		if (skinDetailInfo == null) {
			return;
		}
		int count = view.getSelectedRowsSize();
		if (count == 0 || count > 1) {
			int completed = 0;
			ListRow[] rowsUnsorted = view.getRowsUnsorted();

			int all = rowsUnsorted.length;
			for (int i = 0; i < all; i++) {
				ListRow row = rowsUnsorted[i];
				DownloadManager dm = (DownloadManager) row.getDataSource(true);
				if (dm != null) {
					if (dm.isDownloadComplete(false)) {
						completed++;
					}
				}

			}

			skinDetailInfo.setText(MessageText.getString(
					"MainWindow.v3.myMedia.noneSelected", new String[] {
						"" + all,
						"" + completed
					}));
			return;
		}
		TableRowCore[] rows = view.getSelectedRows();
		String sText = "";
		DownloadManager dm = (DownloadManager) rows[0].getDataSource(true);
		if (dm != null) {
			TOTorrent torrent = dm.getTorrent();
			String s;
			s = PlatformTorrentUtils.getContentTitle(torrent);
			if (s != null) {
				sText += s + "\n\n";
			}

			s = PlatformTorrentUtils.getContentDescription(torrent);
			if (s != null) {
				sText += s + "\n";
			}
		}
		skinDetailInfo.setText(sText);
	}

	private void update() {
		Utils.execSWTThread(new AERunnable() {
			public void runSupport() {
				int count = view.getSelectedRowsSize();
				if (count != 1) {
					skinImgThumb.setImage(null);
					return;
				}
				TableRowCore[] rows = view.getSelectedRows();
				Image image = null;
				DownloadManager dm = (DownloadManager) rows[0].getDataSource(true);
				if (dm != null) {
					byte[] imageBytes = PlatformTorrentUtils.getContentThumbnail(dm.getTorrent());
					if (imageBytes != null) {
						ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
						image = new Image(skinImgThumb.getControl().getDisplay(), bais);
					}
				}
				Image oldImage = skinImgThumb.getImage();
				Utils.disposeSWTObjects(new Object[] {
					oldImage
				});
				skinImgThumb.setImage(image);
			}
		});
	}

}
