/*
 * File    : TorrentItem.java
 * Created : 24 nov. 2003
 * By      : Olivier
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

package org.gudy.azureus2.ui.swt.views.tableitems.peers;

import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.gudy.azureus2.core3.peer.PEPeer;
import org.gudy.azureus2.ui.swt.mainwindow.Colors;
import org.gudy.azureus2.ui.swt.mainwindow.MainWindow;
import org.gudy.azureus2.core3.download.DownloadManager;

/**
 * @author Olivier
 *
 */
public class PiecesItem extends PeerGraphicItem  {
  private final static int INDEX_COLOR_INVERSE = Colors.BLUES_DARKEST + 1;
  // only supports 0 or 1 border width
  private final static int borderHorizontalSize = 1;
  private final static int borderVerticalSize = 1;
  private final static int borderSplit = 1;
  private final static int completionHeight = 2;

  /**
   * @param row
   * @param position
   */
  public PiecesItem(PeerRow peerRow, int position) {
    super(peerRow, position);
  }

  public void dispose() {
    super.dispose();
    //if (peerRow.getPeerSocket() == null) System.out.println("crap! infoObj == null! Graphic possibly == " + getGraphic());

    PEPeer infoObj = peerRow.getPeerSocket();

    setGraphic(null);
    Image img = (Image)infoObj.getData("Image");
    if (img != null && !img.isDisposed())
      img.dispose();

    infoObj.setData("ImageBuffer", null);
    infoObj.setData("Image", null);
  }

  public void refresh() {
    /* Notes:
     * We store our image and imageBufer in PEPeer using
     * setData & getData.
     * This means, we can ignore peerRow.isValid(). peerRow gets marked
     * invalid when its link between peerRow and the table row changes.
     * However, since our data isn't reliant on the table row, it may not
     * really be invalid.
     */
    //Bounds of canvas without padding
    Rectangle bounds = getBoundsForCanvas();

    //In case item isn't displayed bounds is null
    if(bounds == null)
      return;

    PEPeer infoObj = peerRow.getPeerSocket();
    DownloadManager dm = infoObj.getManager().getDownloadManager();

    int x0 = borderVerticalSize;
    int x1 = bounds.width - 1 - borderVerticalSize;
    int y0 = completionHeight + borderHorizontalSize + borderSplit;
    int y1 = bounds.height - 1 - borderHorizontalSize;
    int drawWidth = x1 - x0 + 1;
    if (drawWidth < 10 || y1 < 3)
      return;
    boolean bImageBufferValid = true;
    int[] imageBuffer = (int [])infoObj.getData("ImageBuffer");
    if (imageBuffer == null || imageBuffer.length != x1) {
      imageBuffer = new int[x1];
      bImageBufferValid = false;
    }

    Image image = (Image)infoObj.getData("Image");
    GC gcImage;
    boolean bImageChanged;
    Rectangle imageBounds;
    if (image == null || image.isDisposed()) {
      bImageChanged = true;
    } else {
      imageBounds = image.getBounds();
      bImageChanged = imageBounds.width != bounds.width ||
                      imageBounds.height != bounds.height;
    }
    if (bImageChanged) {
      if (image != null && !image.isDisposed()) {
        image.dispose();
      }
      image = new Image(peerRow.getTableItem().getDisplay(), bounds.width, bounds.height);
      imageBounds = image.getBounds();
      bImageBufferValid = false;

      // draw border
      gcImage = new GC(image);
      gcImage.setForeground(Colors.grey);
      if (borderHorizontalSize > 0) {
        if (borderVerticalSize > 0) {
          gcImage.drawRectangle(0, 0, bounds.width - 1, bounds.height - 1);
        } else {
          gcImage.drawLine(0, 0, bounds.width - 1, 0);
          gcImage.drawLine(0, bounds.height -1, bounds.width - 1, bounds.height - 1);
        }
      } else if (borderVerticalSize > 0) {
        gcImage.drawLine(0, 0, 0, bounds.height - 1);
        gcImage.drawLine(bounds.width - 1, 0, bounds.width - 1, bounds.height - 1);
      }

      if (borderSplit > 0) {
        gcImage.setForeground(Colors.white);
        gcImage.drawLine(x0, completionHeight + borderHorizontalSize,
                         x1, completionHeight + borderHorizontalSize);
      }
    } else {
      gcImage = new GC(image);
    }

    boolean available[] = infoObj.getAvailable();
    
    if (available != null && available.length > 0) {
    try {
      boolean pieces[] = dm.getPiecesStatus();
      int nbComplete = 0;
      int nbPieces = available.length;
      int a0;
      int a1 = 0;
      for (int i = 0; i < drawWidth; i++) {
        if (i == 0) {
          // always start out with one piece
          a0 = 0;
          a1 = nbPieces / drawWidth;
          if (a1 == 0)
            a1 = 1;
        } else {
          // the last iteration, a1 will be nbPieces
          a0 = a1;
          a1 = ((i + 1) * nbPieces) / (drawWidth);
        }

        int index;
        boolean needed = false;

        if (a1 <= a0) {
          index = imageBuffer[i - 1];
        } else {
          int nbAvailable = 0;
          for (int j = a0; j < a1; j++) {
            if (available[j]) {
            	if (!pieces[j]) {
            	  needed = true;
            	}
              nbAvailable++;
            }
          }
          nbComplete += nbAvailable;
          if (needed) {
            // cheat
            index = INDEX_COLOR_INVERSE;
          } else {
            index = (nbAvailable * Colors.BLUES_DARKEST) / (a1 - a0);
          }
        }

        if (!bImageBufferValid || imageBuffer[i] != index) {
          imageBuffer[i] = index;
          bImageChanged = true;
          if (imageBuffer[i] == INDEX_COLOR_INVERSE)
            gcImage.setForeground(Colors.colorInverse);
          else
            gcImage.setForeground(Colors.blues[index]);
          gcImage.drawLine(i + x0, y0, i + x0, y1);
        }
      }
      int limit = (drawWidth * nbComplete) / nbPieces;
      if (limit < drawWidth) {
        gcImage.setBackground(Colors.blues[Colors.BLUES_LIGHTEST]);
        gcImage.fillRectangle(limit+x0, borderHorizontalSize,
                              x1-limit, completionHeight);
      }
      gcImage.setBackground(Colors.colorProgressBar);
      gcImage.fillRectangle(x0, borderHorizontalSize,
                            limit, completionHeight);
    } catch (Exception e) {
      System.out.println("Error Drawing PiecesItem");
      e.printStackTrace();
    } }
    gcImage.dispose();

    Image oldImage = getGraphic();
    if (bImageChanged || image != oldImage) {
      setGraphic(image);
      infoObj.setData("Image", image);
      infoObj.setData("ImageBuffer", imageBuffer);
    }
  }
}
