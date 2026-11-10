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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.fairsim.linalg.Vec2d;

/**
 * Synthetic 2-beam 2D SIM data for tests: a bead surface, illuminated by a
 * cosine pattern defined in real space, blurred by an estimated OTF, binned
 * and with approximated shot and camera noise.
 * Coordinates: x = column, y = row (y down), both in microns from pixel 0.
 * The pattern and the beads use plain double math, so linalg frequency
 * conventions cannot cancel out between simulation and reconstruction.
 * Only the OTF blur runs through the linalg FFT (radially symmetric).
 */
public class SyntheticSim {

	// geometry
	public int size = 256; // raw image size, pixels (square)
	public int over = 4; // oversampling of the ground truth
	public double pxl = 0.080; // raw pixel size, microns

	// optics
	public double na = 1.4, lambda = 525, otfA = 0.7;

	// pattern
	public int nrDirs = 3, nrPhases = 3;
	public double resImpr = 0.9; // k0 as fraction of OTF cutoff
	public double modDepth = 0.8;
	public double angleOffset = 0.2; // first angle, fraction of 2pi

	// object
	public double cellSize = 1.2; // area per bead (or pair), microns
	public double minSep = 0.8; // minimum distance between beads or pairs, microns
	public double margin = 2.0; // bead-free border, microns
	public double beadRadius = 0.06; // microns
	public double pairDist = 0.16; // separation of bead pairs, microns
	public double pairFraction = 0.5;

	// camera
	public double photons = 2000; // peak photons of one bead, full illumination
	public double offset = 100, readNoise = 2;
	public long seed = 42;

	/** One bead, position in microns */
	public static class Bead {
		public final double x, y;
		public final boolean inPair;

		Bead(double x, double y, boolean inPair) {
			this.x = x;
			this.y = y;
			this.inPair = inPair;
		}
	}

	// results
	public final List<Bead> beads = new ArrayList<Bead>();
	public double[] kx, ky; // pattern wave vector, cycles/micron, per direction
	public double[][] phase; // pattern phase, per direction and phase step
	public float[][][] raw; // [dir][phase][x + size*y], camera counts
	public double[] groundTruth; // oversampled, [x + size*over*y]
	public OtfProvider otf;

	/** Run the simulation with the current parameters */
	public SyntheticSim run() {
		otf = OtfProvider.fromEstimate(na, lambda, otfA);
		final double k0 = otf.getCutoff() * resImpr;

		// pattern parameters
		kx = new double[nrDirs];
		ky = new double[nrDirs];
		phase = new double[nrDirs][nrPhases];
		for (int a = 0; a < nrDirs; a++) {
			double ang = 2 * Math.PI * (angleOffset + a / (double) nrDirs);
			kx[a] = k0 * Math.cos(ang);
			ky[a] = k0 * Math.sin(ang);
			for (int p = 0; p < nrPhases; p++)
				phase[a][p] = 0.3 + 0.7 * a + 2 * Math.PI * p / nrPhases;
		}

		makeBeads();
		renderGroundTruth();

		// image each direction and phase
		final int n = size * over;
		final double po = pxl / over;
		final double[] otfVals = otfOnGrid(n, po);
		final Random noise = new Random(seed * 3 + 123);
		final Vec2d.Cplx buf = Vec2d.createCplx(n, n);

		raw = new float[nrDirs][nrPhases][];
		for (int a = 0; a < nrDirs; a++)
			for (int p = 0; p < nrPhases; p++) {

				// illuminate
				float[] d = buf.vectorData();
				for (int y = 0; y < n; y++)
					for (int x = 0; x < n; x++) {
						double ill = 1 - modDepth / 2
								+ modDepth / 2 * Math.cos(2 * Math.PI * (kx[a] * x * po + ky[a] * y * po) + phase[a][p]);
						d[2 * (x + n * y)] = (float) (groundTruth[x + n * y] * ill);
						d[2 * (x + n * y) + 1] = 0;
					}
				buf.syncBuffer();

				// blur
				buf.fft2d(false);
				d = buf.vectorData();
				for (int i = 0; i < n * n; i++) {
					d[2 * i] *= otfVals[i];
					d[2 * i + 1] *= otfVals[i];
				}
				buf.syncBuffer();
				buf.fft2d(true);

				raw[a][p] = binAndNoise(buf.vectorData(), noise);
			}
		return this;
	}

	/** Random positions (no grid: a lattice gives correlation peaks at k0+G) with minimum separation */
	private void makeBeads() {
		beads.clear();
		Random pos = new Random(seed);
		final double ext = size * pxl;
		final int target = (int) (Math.pow((ext - 2 * margin) / cellSize, 2));
		List<double[]> centers = new ArrayList<double[]>();
		for (int i = 0; i < target; i++) {
			double x = 0, y = 0;
			boolean ok = false;
			for (int t = 0; t < 50 && !ok; t++) {
				x = margin + (ext - 2 * margin) * pos.nextDouble();
				y = margin + (ext - 2 * margin) * pos.nextDouble();
				ok = true;
				for (double[] c : centers)
					if (Math.hypot(c[0] - x, c[1] - y) < minSep)
						ok = false;
			}
			if (!ok)
				continue;
			centers.add(new double[] { x, y });
			if (pos.nextDouble() < pairFraction) {
				double ang = Math.PI * pos.nextDouble();
				double dx = Math.cos(ang) * pairDist / 2, dy = Math.sin(ang) * pairDist / 2;
				beads.add(new Bead(x - dx, y - dy, true));
				beads.add(new Bead(x + dx, y + dy, true));
			} else {
				beads.add(new Bead(x, y, false));
			}
		}
	}

	/** Filled beads, flat core with cosine edge (as in BeadsCreator) */
	private void renderGroundTruth() {
		final int n = size * over;
		final double po = pxl / over;
		groundTruth = new double[n * n];
		final int s = (int) Math.ceil(beadRadius / po) + 1;
		for (Bead b : beads) {
			int bx = (int) Math.round(b.x / po), by = (int) Math.round(b.y / po);
			for (int y = by - s; y <= by + s; y++)
				for (int x = bx - s; x <= bx + s; x++) {
					if (x < 0 || y < 0 || x >= n || y >= n)
						continue;
					double dist = Math.hypot(x * po - b.x, y * po - b.y) / beadRadius;
					if (dist > 1)
						continue;
					double v = (dist < 0.5) ? 1 : (Math.cos((dist - 0.5) * 2 * Math.PI) + 1) * .5;
					groundTruth[x + n * y] += v;
				}
		}
	}

	/** OTF values on an unshifted n x n frequency grid (radial, sign-free) */
	private double[] otfOnGrid(int n, double po) {
		double[] ret = new double[n * n];
		final double cpm = 1. / (n * po);
		for (int y = 0; y < n; y++)
			for (int x = 0; x < n; x++) {
				int fx = (x < n / 2) ? x : x - n, fy = (y < n / 2) ? y : y - n;
				double r = Math.hypot(fx, fy) * cpm;
				ret[x + n * y] = (r < otf.getCutoff()) ? otf.getOtfVal(0, r, false).re : 0;
			}
		return ret;
	}

	/** Bin over x over, scale to photons, add approximate shot and read noise */
	private float[] binAndNoise(float[] cplx, Random noise) {
		final int n = size * over;
		float[] ret = new float[size * size];
		final double scale = photons / (over * over);
		for (int y = 0; y < size; y++)
			for (int x = 0; x < size; x++) {
				double acc = 0;
				for (int y1 = 0; y1 < over; y1++)
					for (int x1 = 0; x1 < over; x1++)
						acc += cplx[2 * ((x * over + x1) + n * (y * over + y1))];
				acc *= scale;
				acc += noise.nextGaussian() * Math.sqrt(Math.max(acc, 0));
				acc += offset + noise.nextGaussian() * readNoise;
				ret[x + size * y] = (float) Math.max(acc, 0);
			}
		return ret;
	}

	/** Raw pixel coordinate represented by binned pixel 0 (box-average centre), in raw pixels */
	public double binOffset() {
		return (over - 1) / (2. * over);
	}

	/** Pattern wave vector of direction a in raw-image Fourier pixels (cycles per image) */
	public double[] kPixels(int a) {
		return new double[] { kx[a] * size * pxl, ky[a] * size * pxl };
	}
}
