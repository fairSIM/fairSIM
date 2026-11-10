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

package org.fairsim.sim_algorithm;

import java.io.File;

import org.fairsim.linalg.Vec2d;
import org.fairsim.utils.Tool;

import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileSaver;
import ij.process.FloatProcessor;

/** Image measurements and optional TIFF output for tests */
class TestImages {

	/** Output folder for visual checks, set with -Dfairsim.test.out=DIR (off if unset) */
	static final String OUT = System.getProperty("fairsim.test.out");

	/** Bilinear interpolation, clamped to the image */
	static double interp(Vec2d.Real img, double x, double y) {
		int w = img.vectorWidth(), h = img.vectorHeight();
		x = Math.max(0, Math.min(w - 1.001, x));
		y = Math.max(0, Math.min(h - 1.001, y));
		int x0 = (int) x, y0 = (int) y;
		double fx = x - x0, fy = y - y0;
		return (1 - fx) * (1 - fy) * img.get(x0, y0) + fx * (1 - fy) * img.get(x0 + 1, y0)
				+ (1 - fx) * fy * img.get(x0, y0 + 1) + fx * fy * img.get(x0 + 1, y0 + 1);
	}

	/**
	 * Centroid around the brightest pixel near (x,y).
	 * @param search search radius for the maximum, pixels
	 * @param win half width of the centroid window, pixels
	 */
	static double[] centroid(Vec2d.Real img, double x, double y, int search, int win) {
		int w = img.vectorWidth(), h = img.vectorHeight();
		int mx = (int) Math.round(x), my = (int) Math.round(y);
		float max = -Float.MAX_VALUE;
		for (int yi = (int) Math.round(y) - search; yi <= (int) Math.round(y) + search; yi++)
			for (int xi = (int) Math.round(x) - search; xi <= (int) Math.round(x) + search; xi++)
				if (xi >= 0 && yi >= 0 && xi < w && yi < h && img.get(xi, yi) > max) {
					max = img.get(xi, yi);
					mx = xi;
					my = yi;
				}

		// background: window minimum
		double min = Double.MAX_VALUE;
		for (int yi = my - win; yi <= my + win; yi++)
			for (int xi = mx - win; xi <= mx + win; xi++)
				min = Math.min(min, img.get(Math.max(0, Math.min(w - 1, xi)), Math.max(0, Math.min(h - 1, yi))));

		double sx = 0, sy = 0, s = 0;
		for (int yi = my - win; yi <= my + win; yi++)
			for (int xi = mx - win; xi <= mx + win; xi++) {
				if (xi < 0 || yi < 0 || xi >= w || yi >= h)
					continue;
				double v = img.get(xi, yi) - min;
				sx += v * xi;
				sy += v * yi;
				s += v;
			}
		return new double[] { sx / s, sy / s };
	}

	/** Midpoint value over mean of the two peak values, along the line (x1,y1)-(x2,y2) */
	static double dipRatio(Vec2d.Real img, double x1, double y1, double x2, double y2) {
		double p1 = interp(img, x1, y1), p2 = interp(img, x2, y2);
		double mid = interp(img, (x1 + x2) / 2, (y1 + y2) / 2);
		return mid / ((p1 + p2) / 2);
	}

	/** Save raw stack, ground truth, widefield and result as TIFF, if an output folder is set */
	static void save(String name, SyntheticSim sim, Vec2d.Real result, Vec2d.Real widefield) {
		if (OUT == null)
			return;
		new File(OUT).mkdirs();

		ImageStack raw = new ImageStack(sim.size, sim.size);
		for (int a = 0; a < sim.nrDirs; a++)
			for (int p = 0; p < sim.nrPhases; p++)
				raw.addSlice("a" + a + " p" + p, new FloatProcessor(sim.size, sim.size, sim.raw[a][p].clone(), null));
		write(new ImagePlus(name + "-raw", raw), name + "-raw.tif");

		int n = sim.size * sim.over;
		float[] gt = new float[n * n];
		for (int i = 0; i < gt.length; i++)
			gt[i] = (float) sim.groundTruth[i];
		write(new ImagePlus(name + "-gt", new FloatProcessor(n, n, gt, null)), name + "-groundtruth.tif");

		write(toImage(name + "-result", result), name + "-result.tif");
		write(toImage(name + "-widefield", widefield), name + "-widefield.tif");
	}

	/** Save a stack, data in [x + w*y + w*h*z], if an output folder is set */
	static void saveStack(String name, int w, int h, int d, float[] data) {
		if (OUT == null)
			return;
		new File(OUT).mkdirs();
		ImageStack is = new ImageStack(w, h);
		for (int z = 0; z < d; z++) {
			float[] sl = new float[w * h];
			System.arraycopy(data, z * w * h, sl, 0, w * h);
			is.addSlice("z" + z, new FloatProcessor(w, h, sl, null));
		}
		write(new ImagePlus(name, is), name + ".tif");
	}

	private static ImagePlus toImage(String title, Vec2d.Real v) {
		return new ImagePlus(title, new FloatProcessor(v.vectorWidth(), v.vectorHeight(),
				v.vectorData().clone(), null));
	}

	private static void write(ImagePlus ip, String file) {
		String path = new File(OUT, file).getPath();
		if (ip.getStackSize() > 1)
			new FileSaver(ip).saveAsTiffStack(path);
		else
			new FileSaver(ip).saveAsTiff(path);
		Tool.trace("test image written: " + path);
	}
}
