//
// SeriesDialog.java
//

/*
LOCI Plugins for ImageJ: a collection of ImageJ plugins including the
Bio-Formats Importer, Bio-Formats Exporter, Bio-Formats Macro Extensions,
Data Browser, Stack Colorizer and Stack Slicer. Copyright (C) 2005-@year@
Melissa Linkert, Curtis Rueden and Christopher Peterson.

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package loci.plugins.importer;

import ij.IJ;
import ij.gui.GenericDialog;

import java.awt.Button;
import java.awt.Checkbox;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.StringTokenizer;

import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JLabel;

import loci.formats.FormatTools;
import loci.formats.IFormatReader;
import loci.formats.gui.AWTImageTools;
import loci.formats.gui.BufferedImageReader;
import loci.plugins.prefs.OptionsDialog;
import loci.plugins.util.WindowTools;

/**
 * Bio-Formats Importer series chooser dialog box.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/components/loci-plugins/src/loci/plugins/importer/SeriesDialog.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/components/loci-plugins/src/loci/plugins/importer/SeriesDialog.java">SVN</a></dd></dl>
 */
public class SeriesDialog extends OptionsDialog implements ActionListener {

  // -- Fields --

  /** LOCI plugins configuration. */
  protected ImporterOptions options;

  protected BufferedImageReader r;
  protected String[] seriesLabels;
  protected boolean[] series;

  protected Checkbox[] boxes;

  // -- Constructor --

  /**
   * Creates a series chooser dialog for the Bio-Formats Importer.
   *
   * @param r The reader to use for extracting details of each series.
   * @param seriesLabels Label to display to user identifying each series.
   * @param series Boolean array indicating which series to include
   *   (populated by this method).
   */
  public SeriesDialog(ImporterOptions options,
    IFormatReader r, String[] seriesLabels, boolean[] series)
  {
    super(options);
    this.options = options;
    this.r = r instanceof BufferedImageReader ?
      (BufferedImageReader) r : new BufferedImageReader(r);
    this.seriesLabels = seriesLabels;
    this.series = series;
  }

  // -- OptionsDialog methods --

  /**
   * Gets which image series to open from macro options,
   * or user prompt if necessary.
   *
   * @return status of operation
   */
  public int showDialog() {
    String seriesString = options.getSeries();

    if (options.isWindowless()) {
      if (seriesString != null) {
        if (seriesString.startsWith("[")) {
          seriesString = seriesString.substring(1, seriesString.length() - 2);
        }
        Arrays.fill(series, false);
        StringTokenizer tokens = new StringTokenizer(seriesString, " ");
        while (tokens.hasMoreTokens()) {
          String token = tokens.nextToken().trim();
          int n = Integer.parseInt(token);
          if (n < series.length) series[n] = true;
        }
      }
      options.setSeries(seriesString);
      return STATUS_OK;
    }
    int seriesCount = r.getSeriesCount();

    // prompt user to specify series inclusion (or grab from macro options)
    GenericDialog gd = new GenericDialog("Bio-Formats Series Options");

    GridBagLayout gdl = (GridBagLayout) gd.getLayout();
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 2;
    gbc.gridwidth = GridBagConstraints.REMAINDER;

    Panel[] p = new Panel[seriesCount];
    for (int i=0; i<seriesCount; i++) {
      gd.addCheckbox(seriesLabels[i], series[i]);
      r.setSeries(i);
      int sx = r.getThumbSizeX() + 10; // a little extra padding
      int sy = r.getThumbSizeY();
      p[i] = new Panel();
      p[i].add(Box.createRigidArea(new Dimension(sx, sy)));
      gbc.gridy = i;
      if (options.isForceThumbnails()) {
        IJ.showStatus("Reading thumbnail for series #" + (i + 1));
        int z = r.getSizeZ() / 2;
        int t = r.getSizeT() / 2;
        int ndx = r.getIndex(z, 0, t);
        try {
          BufferedImage img = r.openThumbImage(ndx);
          if (options.isAutoscale() && r.getPixelType() != FormatTools.FLOAT) {
            img = AWTImageTools.autoscale(img);
          }
          ImageIcon icon = new ImageIcon(img);
          p[i].removeAll();
          p[i].add(new JLabel(icon));
        }
        catch (Exception e) { }
      }
      gdl.setConstraints(p[i], gbc);
      gd.add(p[i]);
    }
    WindowTools.addScrollBars(gd);

    // add Select All and Deselect All buttons

    boxes = (Checkbox[]) gd.getCheckboxes().toArray(new Checkbox[0]);

    Panel buttons = new Panel();

    Button select = new Button("Select All");
    select.setActionCommand("select");
    select.addActionListener(this);
    Button deselect = new Button("Deselect All");
    deselect.setActionCommand("deselect");
    deselect.addActionListener(this);

    buttons.add(select);
    buttons.add(deselect);

    gbc.gridx = 0;
    gbc.gridy = seriesCount;
    gdl.setConstraints(buttons, gbc);
    gd.add(buttons);

    if (options.isForceThumbnails()) gd.showDialog();
    else {
      ThumbLoader loader = new ThumbLoader(r, p, gd, options.isAutoscale());
      gd.showDialog();
      loader.stop();
    }
    if (gd.wasCanceled()) return STATUS_CANCELED;

    seriesString = "[";
    for (int i=0; i<seriesCount; i++) {
      series[i] = gd.getNextBoolean();
      if (series[i]) seriesString += i + " ";
    }
    seriesString += "]";

    options.setSeries(seriesString);
    return STATUS_OK;
  }

  // -- ActionListener methods --

  public void actionPerformed(ActionEvent e) {
    String cmd = e.getActionCommand();
    if ("select".equals(cmd)) {
      for (int i=0; i<boxes.length; i++) boxes[i].setState(true);
    }
    else if ("deselect".equals(cmd)) {
      for (int i=0; i<boxes.length; i++) boxes[i].setState(false);
    }
  }

}
