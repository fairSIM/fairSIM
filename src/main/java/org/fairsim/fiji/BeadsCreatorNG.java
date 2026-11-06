/*
This file is part of Free Analysis and Interactive Reconstruction
for Structured Illumination Microscopy (fairSIM).

fairSIM is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or
(at your option) any later version.

fairSIM is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with fairSIM.  If not, see <http://www.gnu.org/licenses/>
*/

package org.fairsim.fiji;

import org.fairsim.utils.Tool;
import org.fairsim.utils.Args;
import org.fairsim.linalg.Vec2d;

import java.util.List;
import java.util.ArrayList;
import java.util.Random;

import ij.plugin.PlugIn;
import ij.ImagePlus;
import ij.IJ;
import ij.gui.GenericDialog;
import ij.measure.Calibration;

/**
 * Small plugin that generate bead surfaces, next generation.
 * Compared to {@link BeadsCreator}, the surface generation is split in two:
 * first a list of bead positions is created (either randomly distributed or
 * grouped into clusters), then that list is rendered into the image.
 */
public class BeadsCreatorNG implements PlugIn {

	final int width, height, oversample;
	final double pxlSize;

	Vec2d.Real groundTruth;

	Random posPRNG, sizePRNG, hollowPRNG;

	// how often to retry placing a bead before giving up on it
	static final int MAX_TRIES = 20;

	/** A single bead: center position in (oversampled) pxl, radius in micron */
	public static class Bead {

		public final double x, y, rad;
		public final boolean hollow;

		Bead(double x, double y, double rad, boolean hollow) {
			this.x = x;
			this.y = y;
			this.rad = rad;
			this.hollow = hollow;
		}
	}

	// non-sense constructor only to enable this as a ImageJ plugin
	public BeadsCreatorNG() {
		width = height = oversample = -1;
		pxlSize = -1;
	}

	private BeadsCreatorNG(int w, int h, double pxl, int over, int prngSEED) {
		width = w * over;
		height = h * over;
		pxlSize = pxl / over;
		oversample = over;

		groundTruth = Vec2d.createReal(width, height);

		// one PRNG per property, so changing one does not shift the others
		posPRNG = new Random(prngSEED);
		sizePRNG = new Random(prngSEED * 5 + 123);
		hollowPRNG = new Random(prngSEED * 123 + 3);
	}

	// ---------- part 1: generating the bead positions ----------

	/**
	 * Checks if a bead of radius 'rad' (micron) fits at (x,y) (pxl): it has to lie
	 * within the image, and keep at least 'gap' (micron) distance to every bead
	 * already in the list. 'ignore' is skipped, use it for a bead that is tangent
	 * to the candidate by construction (may be null).
	 */
	private boolean beadFits(List<Bead> beads, double x, double y, double rad, double gap, Bead ignore) {

		if (x < 0 || x >= width || y < 0 || y >= height)
			return false;

		for (Bead b : beads) {

			if (b == ignore)
				continue;

			// distances are in pxl, radii and gap in micron
			if (Math.hypot(x - b.x, y - b.y) < (b.rad + rad + gap) / pxlSize)
				return false;
		}

		return true;
	}

	/**
	 * Creates a list of non-overlapping, uniformly distributed beads. Radii are drawn
	 * from [minRad,maxRad] (micron), beads are hollow with probability 'doughnutFraction'.
	 * Beads that find no free spot are dropped, so the list may be shorter than 'nrBeads'.
	 */
	List<Bead> randomPositions(int nrBeads, double minRad, double maxRad,
			double gap, double doughnutFraction) {

		List<Bead> beads = new ArrayList<Bead>();

		for (int i = 0; i < nrBeads; i++) {

			double rad = minRad + (maxRad - minRad) * sizePRNG.nextDouble();
			boolean hollow = (hollowPRNG.nextDouble() < doughnutFraction);

			boolean success = false;

			for (int t = 0; t < MAX_TRIES; t++) {

				double x = posPRNG.nextDouble() * width;
				double y = posPRNG.nextDouble() * height;

				if (!success && beadFits(beads, x, y, rad, gap, null)) {
					beads.add(new Bead(x, y, rad, hollow));
					success = true; // don't break, to call the PRNG equally often each time
				}
			}

			IJ.showProgress(i, nrBeads - 1);
		}

		return beads;
	}

	/**
	 * Creates a list of beads grouped into clusters of 'beadsPerCluster'. Each cluster
	 * starts from a randomly placed bead, all further beads of the cluster are placed
	 * touching a randomly picked bead of that same cluster. Clusters do not overlap
	 * each other, parameters are as for {@link #randomPositions}.
	 */
	List<Bead> clusteredPositions(int nrBeads, int beadsPerCluster, double minRad, double maxRad,
			double gap, double doughnutFraction) {

		List<Bead> beads = new ArrayList<Bead>();

		while (beads.size() < nrBeads) {

			// start a new cluster, its first bead is placed at random
			List<Bead> cluster = new ArrayList<Bead>();

			double rad = minRad + (maxRad - minRad) * sizePRNG.nextDouble();
			boolean hollow = (hollowPRNG.nextDouble() < doughnutFraction);

			for (int t = 0; t < MAX_TRIES; t++) {

				double x = posPRNG.nextDouble() * width;
				double y = posPRNG.nextDouble() * height;

				if (cluster.isEmpty() && beadFits(beads, x, y, rad, gap, null)) {
					Bead seed = new Bead(x, y, rad, hollow);
					beads.add(seed);
					cluster.add(seed);
				}
			}

			// the surface is too crowded to seed another cluster, so stop here
			if (cluster.isEmpty())
				break;

			// grow the cluster, attaching each bead to a random member of it
			while (cluster.size() < beadsPerCluster && beads.size() < nrBeads) {

				rad = minRad + (maxRad - minRad) * sizePRNG.nextDouble();
				hollow = (hollowPRNG.nextDouble() < doughnutFraction);

				Bead placed = null;

				for (int t = 0; t < MAX_TRIES; t++) {

					Bead anchor = cluster.get(posPRNG.nextInt(cluster.size()));
					double ang = posPRNG.nextDouble() * 2 * Math.PI;

					// sit on the anchor's surface, gap included
					double dist = (anchor.rad + rad + gap) / pxlSize;
					double x = anchor.x + Math.cos(ang) * dist;
					double y = anchor.y + Math.sin(ang) * dist;

					// the anchor is touched by construction, so exclude it from the test
					if (placed == null && beadFits(beads, x, y, rad, gap, anchor)) {
						placed = new Bead(x, y, rad, hollow);
						beads.add(placed);
						cluster.add(placed);
					}
				}

				// no free surface left on this cluster, so start the next one
				if (placed == null)
					break;
			}

			IJ.showProgress(beads.size(), nrBeads);
		}

		return beads;
	}

	// ---------- part 2: rendering the bead surface ----------

	private void addBead(double xPos, double yPos, double rad, boolean hollow) {

		Vec2d.Real dat = groundTruth;

		int s = (int) (2 * rad / pxlSize + 2);

		for (int y = (int) yPos - s; y < (int) yPos + s; y++) {
			for (int x = (int) xPos - s; x < (int) xPos + s; x++) {

				double dist = Math.hypot(x - xPos, y - yPos) / (rad / pxlSize);

				if (y < 0 || y >= height || x < 0 || x >= width)
					continue;

				if (dist > 1)
					continue;

				if (!hollow) {
					if (dist < 0.5) {
						dat.set(x, y, dat.get(x, y) + 1);
					} else {
						dat.set(x, y, dat.get(x, y) + (float) (Math.cos(((dist - 0.5) * 2 * Math.PI)) + 1) * .5f);
					}
				} else {
					if (dist > .2)
						dat.set(x, y, dat.get(x, y) + (float) (Math.sin((dist - .2) / .8 * Math.PI)));
				}

			}
		}

	}

	/** Renders a list of beads into the ground truth image */
	void renderSurface(List<Bead> beads) {

		for (int i = 0; i < beads.size(); i++) {
			Bead b = beads.get(i);
			addBead(b.x, b.y, b.rad, b.hollow);
			IJ.showProgress(i, beads.size() - 1);
		}
	}

	/** Wraps the ground truth into a calibrated ImagePlus */
	private ImagePlus createImage() {

		ImageVector img = ImageVector.create(width, height);
		img.copy(groundTruth);
		img.scal(1000.f);

		ImagePlus ip = new ImagePlus("bead_surface", img.img());
		Calibration cb = new Calibration();
		cb.setUnit("um");
		cb.pixelWidth = pxlSize;
		cb.pixelHeight = pxlSize;
		ip.setCalibration(cb);

		return ip;
	}

	public void run(String arg) {

		GenericDialog gd = new GenericDialog("Bead surface simulator (NG)");

		gd.addNumericField("#beads", 300, 0);
		gd.addNumericField("image size (pxl)", 256, 0);
		gd.addNumericField("oversample", 4, 0);
		gd.addNumericField("pxl size (nm)", 80, 0);
		gd.addNumericField("min. bead size (nm)", 200, 0);
		gd.addNumericField("max. bead size (nm)", 220, 0);
		gd.addNumericField("min. bead gap (nm)", 0, 0);
		gd.addNumericField("doughnut fraction", 0.5, 2);
		gd.addChoice("distribution", new String[] { "random", "clustered" }, "random");
		gd.addNumericField("beads per cluster", 5, 0);
		gd.addNumericField("PRNG seed", 42, 0);

		gd.showDialog();
		if (gd.wasCanceled()) {
			return;
		}

		final int nrBeads = (int) gd.getNextNumber();
		final int imgSize = (int) gd.getNextNumber();
		final int overSample = (int) gd.getNextNumber();
		final double pxlSize = gd.getNextNumber();
		final double minBeadSize = gd.getNextNumber();
		final double maxBeadSize = gd.getNextNumber();
		final double beadGap = gd.getNextNumber();
		final double doughnutFraction = gd.getNextNumber();
		final String distribution = gd.getNextChoice();
		final int beadsPerCluster = (int) gd.getNextNumber();
		final int prngSeed = (int) gd.getNextNumber();

		BeadsCreatorNG bc = new BeadsCreatorNG(imgSize, imgSize, pxlSize / 1000., overSample, prngSeed);

		// part 1: where the beads go
		List<Bead> beads;

		if (distribution.equals("clustered")) {
			beads = bc.clusteredPositions(nrBeads, beadsPerCluster, minBeadSize / 1000.,
					maxBeadSize / 1000., beadGap / 1000., doughnutFraction);
		} else {
			beads = bc.randomPositions(nrBeads, minBeadSize / 1000.,
					maxBeadSize / 1000., beadGap / 1000., doughnutFraction);
		}

		if (beads.size() < nrBeads)
			IJ.log("BeadsCreatorNG: surface too crowded, placed only "
					+ beads.size() + " of " + nrBeads + " beads");

		// part 2: what they look like
		bc.renderSurface(beads);

		bc.createImage().show();
	}

	public static void main(String[] attr) {

		// parse the parameters

		Args arg = new Args();

		arg.addInt("outputSize", 256, "Size of the output image (pxls)");
		arg.addDbl("pxlSize", 0.08, "Size of the pixels (micron)");
		arg.addInt("oversample", 8, "Oversampling of the ground truth");
		arg.addInt("nrBeads", 300, "Number of beads to simulate");
		arg.addDbl("minBeadSize", 0.2, "Minimum bead size (micron)");
		arg.addDbl("maxBeadSize", 0.22, "Maximum bead size (micron)");
		arg.addDbl("beadGap", 0.0, "Minimum gap between beads (micron)");
		arg.addDbl("doughnutFraction", 0.5, "Fraction of hollow beads");
		arg.addInt("beadsPerCluster", 1, "Beads per cluster, set to 1 for a random distribution");
		arg.addStr("output", "", "Filename to save the ground truth to (tif)");
		arg.addInt("prngSEED", 1337, "Seed for the random number generators");

		if (arg.parseArgs(attr) < 0)
			System.exit(-1);

		int outputSize = arg.getInt("outputSize");
		double pxlSize = arg.getDbl("pxlSize");
		int oversample = arg.getInt("oversample");
		int nrBeads = arg.getInt("nrBeads");
		double minBeadSize = arg.getDbl("minBeadSize");
		double maxBeadSize = arg.getDbl("maxBeadSize");
		double beadGap = arg.getDbl("beadGap");
		double doughnutFraction = arg.getDbl("doughnutFraction");
		int beadsPerCluster = arg.getInt("beadsPerCluster");
		String output = arg.getStr("output");
		int prngSEED = arg.getInt("prngSEED");

		arg.printParams();

		// generate the ground truth

		ij.ImageJ inst = new ij.ImageJ(ij.ImageJ.EMBEDDED);

		BeadsCreatorNG bc = new BeadsCreatorNG(outputSize, outputSize, pxlSize, oversample, prngSEED);

		List<Bead> beads;

		if (beadsPerCluster > 1) {
			beads = bc.clusteredPositions(nrBeads, beadsPerCluster, minBeadSize,
					maxBeadSize, beadGap, doughnutFraction);
		} else {
			beads = bc.randomPositions(nrBeads, minBeadSize, maxBeadSize, beadGap, doughnutFraction);
		}

		Tool.trace("placed " + beads.size() + " of " + nrBeads + " beads");

		bc.renderSurface(beads);

		DisplayWrapper dwGT = new DisplayWrapper(bc.width, bc.height, "Bead Simulation: full res");
		dwGT.addImage(bc.groundTruth, "Ground truth");
		dwGT.display();

		// store the ground truth, if a filename was given

		if (!output.isEmpty()) {
			IJ.saveAsTiff(bc.createImage(), output);
			Tool.trace("saved ground truth to " + output);
		}
	}

}
