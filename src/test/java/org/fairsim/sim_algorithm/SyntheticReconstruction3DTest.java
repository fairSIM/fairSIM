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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fairsim.linalg.Vec2d;
import org.fairsim.linalg.Vec3d;
import org.fairsim.utils.Tool;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Full 3D reconstruction of synthetic beads in a volume. Asserts physical
 * results only (pattern frequency, 3D bead positions, lateral and axial
 * resolution), so these hold independent of linalg index conventions.
 * The reconstruction gets the simulation's OTF as a table. Lateral size is
 * square, as the 3D path scales lateral frequencies by the image width.
 */
public class SyntheticReconstruction3DTest {

	static SyntheticSim3D sim;
	static SimParam param;
	static Vol result, widefield;

	@BeforeAll
	static void reconstruct() {
		sim = new SyntheticSim3D().run();
		final int w = sim.size, d = sim.depth;

		// OTF table from the simulation's model (OtfProvider3D clips band 1 at 1.4x the axial cutoff)
		final double step = 0.05;
		final int nLat = (int) Math.ceil(sim.cutoffLateral() / step) + 2;
		final int nAx = (int) Math.ceil((sim.cutoffAxial() + sim.kz1) / step) + 2;
		OtfProvider3D otf3d = OtfProvider3D.createFromData(3, sim.otfTable(step, nLat, nAx),
				step, step, nLat, nAx);
		otf3d.setNA(sim.na);
		otf3d.setLambda(sim.lambdaEm);
		OtfProvider otf2d = OtfProvider.fromEstimate(sim.na, sim.lambdaEm, 0.7); // only to build SimParam

		param = SimParam.create3d(3, sim.nrDirs, sim.nrPhases, w, d, sim.pxl, sim.dz, otf2d, otf3d);
		param.setWienerFilter(0.05);
		param.setApoCutoff(2.0);
		param.setApoCutoffAxial(1.4);
		param.setApoType(1);

		// input: phases, then slices, then directions; background subtracted
		Vec2d.Real[] in = new Vec2d.Real[sim.nrDirs * sim.nrPhases * d];
		float[] wf = new float[w * w * d];
		for (int a = 0; a < sim.nrDirs; a++)
			for (int p = 0; p < sim.nrPhases; p++)
				for (int z = 0; z < d; z++) {
					Vec2d.Real img = Vec2d.createReal(w, w);
					float[] dat = img.vectorData();
					for (int i = 0; i < w * w; i++) {
						dat[i] = sim.raw[a][p][i + w * w * z] - (float) sim.offset;
						wf[i + w * w * z] += dat[i];
					}
					img.syncBuffer();
					in[p + sim.nrPhases * z + sim.nrPhases * d * a] = img;
				}
		widefield = new Vol(w, w, d, wf);
		sim.raw = null;

		Vec3d.Cplx res = SimAlgorithm3D.runReconstruction(in, param, 0, 3, false);
		res.fft3d(true);
		result = Vol.real(res);

		TestImages.saveStack("synthetic-3d-widefield", w, w, d, widefield.v);
		TestImages.saveStack("synthetic-3d-result", 2 * w, 2 * w, d, result.v);
	}

	@AfterAll
	static void release() {
		sim = null;
		param = null;
		result = null;
		widefield = null;
	}

	/** Isolated beads sit at their true positions in the summed raw data (pins the simulation's FFT convention) */
	@Test
	void simulationBeadPositions() {
		double[] err = positionErrors(widefield, 1, "widefield");
		assertTrue(err[2] > 15, "too few isolated beads: " + err[2]);
		assertTrue(err[0] < 0.3, "max lateral error " + err[0] + " px");
		assertTrue(err[1] < 0.3, "max axial error " + err[1] + " slices");
		assertTrue(err[3] < 0.1, "mean offset " + err[3] + " px or slices");
	}

	/** Estimated pattern frequency matches the simulated one in length (sign- and axis-free) */
	@Test
	void patternFrequencyLength() {
		for (int a = 0; a < sim.nrDirs; a++) {
			double[] k = sim.kPixels(a);
			double est = param.dir(a).getPxPyLen(2);
			Tool.trace(String.format("dir %d: sim k (%7.3f, %7.3f) est p (%7.3f, %7.3f)",
					a, k[0], k[1], param.dir(a).px(2), param.dir(a).py(2)));
			assertEquals(Math.hypot(k[0], k[1]), est, 0.05, "dir " + a);
		}
	}

	/** Pins SimParam's frame: y up, i.e. (px, py) = +-(kx, -ky) for a pattern with y = image row */
	@Test
	void patternVectorConvention() {
		for (int a = 0; a < sim.nrDirs; a++) {
			double[] k = sim.kPixels(a);
			double px = param.dir(a).px(2), py = param.dir(a).py(2);
			double sign = Math.signum(px * k[0] - py * k[1]);
			assertEquals(sign * k[0], px, 0.05, "px, dir " + a);
			assertEquals(-sign * k[1], py, 0.05, "py, dir " + a);
		}
	}

	/** Isolated beads appear at their true 3D positions (catches flips and transposes) */
	@Test
	void beadPositions() {
		double[] err = positionErrors(result, 2, "result");
		assertTrue(err[2] > 15, "too few isolated beads: " + err[2]);
		assertTrue(err[0] < 0.5, "max lateral error " + err[0] + " px");
		assertTrue(err[1] < 0.5, "max axial error " + err[1] + " slices");
		assertTrue(err[3] < 0.1, "mean offset " + err[3] + " px or slices");
	}

	/** Lateral pairs below the widefield limit are resolved by SIM, not in widefield */
	@Test
	void lateralPairsResolved() {
		pairsResolved(SyntheticSim3D.Type.LATERAL_PAIR);
	}

	/** Axial pairs below the widefield axial limit are resolved by SIM, not in widefield */
	@Test
	void axialPairsResolved() {
		pairsResolved(SyntheticSim3D.Type.AXIAL_PAIR);
	}

	private void pairsResolved(SyntheticSim3D.Type type) {
		int pairs = 0, simRes = 0, wfRes = 0;
		for (int i = 0; i + 1 < sim.beads.size(); i++) {
			SyntheticSim3D.Bead b1 = sim.beads.get(i), b2 = sim.beads.get(i + 1);
			if (b1.type != type || b2.type != type)
				continue;
			if (result.dipRatio(pos(b1, 2), pos(b2, 2)) < 0.9)
				simRes++;
			if (widefield.dipRatio(pos(b1, 1), pos(b2, 1)) < 0.9)
				wfRes++;
			pairs++;
			i++;
		}
		Tool.trace(String.format("%s: %d, resolved SIM %d, widefield %d", type, pairs, simRes, wfRes));
		assertTrue(pairs > 15, "too few pairs: " + pairs);
		assertTrue(simRes > 0.8 * pairs, "SIM resolved only " + simRes + " of " + pairs);
		assertTrue(wfRes < 0.2 * pairs, "widefield resolved " + wfRes + " of " + pairs + ", simulation too easy");
	}

	/** Isolated beads: max lateral error (px), max axial error (slices), count, largest mean offset of x, y, z */
	private static double[] positionErrors(Vol v, int lat, String name) {
		double maxL = 0, maxA = 0;
		double[] mean = new double[3];
		int n = 0;
		for (SyntheticSim3D.Bead b : sim.beads) {
			if (b.type != SyntheticSim3D.Type.SINGLE)
				continue;
			double[] e = pos(b, lat);
			double[] c = v.centroid(e, 2 * lat, 3);
			maxL = Math.max(maxL, Math.hypot(c[0] - e[0], c[1] - e[1]));
			maxA = Math.max(maxA, Math.abs(c[2] - e[2]));
			for (int i = 0; i < 3; i++)
				mean[i] += c[i] - e[i];
			n++;
		}
		for (int i = 0; i < 3; i++)
			mean[i] /= n;
		Tool.trace(String.format("%s bead positions: %d beads, max error lateral %5.3f px, axial %5.3f slices, "
				+ "mean offset (%6.3f, %6.3f, %6.3f)", name, n, maxL, maxA, mean[0], mean[1], mean[2]));
		double maxMean = Math.max(Math.abs(mean[0]), Math.max(Math.abs(mean[1]), Math.abs(mean[2])));
		return new double[] { maxL, maxA, n, maxMean };
	}

	/** Bead position in voxels, lateral upsampling factor lat */
	private static double[] pos(SyntheticSim3D.Bead b, int lat) {
		return new double[] { lat * b.x / sim.pxl, lat * b.y / sim.pxl, b.z / sim.dz };
	}

	/** Real volume, data in [x + w*y + w*h*z] */
	static class Vol {
		final int w, h, d;
		final float[] v;

		Vol(int w, int h, int d, float[] v) {
			this.w = w;
			this.h = h;
			this.d = d;
			this.v = v;
		}

		static Vol real(Vec3d.Cplx c) {
			int w = c.vectorWidth(), h = c.vectorHeight(), d = c.vectorDepth();
			float[] dat = c.vectorData(), r = new float[w * h * d];
			for (int i = 0; i < r.length; i++)
				r[i] = dat[2 * i];
			return new Vol(w, h, d, r);
		}

		float get(int x, int y, int z) {
			x = Math.max(0, Math.min(w - 1, x));
			y = Math.max(0, Math.min(h - 1, y));
			z = Math.max(0, Math.min(d - 1, z));
			return v[x + w * y + w * h * z];
		}

		/** Trilinear interpolation, clamped to the volume */
		double interp(double[] p) {
			double x = Math.max(0, Math.min(w - 1.001, p[0]));
			double y = Math.max(0, Math.min(h - 1.001, p[1]));
			double z = Math.max(0, Math.min(d - 1.001, p[2]));
			int x0 = (int) x, y0 = (int) y, z0 = (int) z;
			double fx = x - x0, fy = y - y0, fz = z - z0, ret = 0;
			for (int k = 0; k < 2; k++)
				for (int j = 0; j < 2; j++)
					for (int i = 0; i < 2; i++)
						ret += ((i == 0) ? 1 - fx : fx) * ((j == 0) ? 1 - fy : fy) * ((k == 0) ? 1 - fz : fz)
								* get(x0 + i, y0 + j, z0 + k);
			return ret;
		}

		/** Midpoint value over mean of the two peak values, infinite (unresolved) if the peaks are not positive */
		double dipRatio(double[] p1, double[] p2) {
			double[] mid = new double[] { (p1[0] + p2[0]) / 2, (p1[1] + p2[1]) / 2, (p1[2] + p2[2]) / 2 };
			double peak = (interp(p1) + interp(p2)) / 2;
			return (peak > 0) ? interp(mid) / peak : Double.POSITIVE_INFINITY;
		}

		/**
		 * Centroid around the brightest voxel within +-1 of p.
		 * @param latWin half width of the centroid window, lateral, voxels
		 * @param axWin same, axial
		 */
		double[] centroid(double[] p, int latWin, int axWin) {
			int mx = (int) Math.round(p[0]), my = (int) Math.round(p[1]), mz = (int) Math.round(p[2]);
			float max = -Float.MAX_VALUE;
			int cx = mx, cy = my, cz = mz;
			for (int z = mz - 1; z <= mz + 1; z++)
				for (int y = my - 1; y <= my + 1; y++)
					for (int x = mx - 1; x <= mx + 1; x++)
						if (get(x, y, z) > max) {
							max = get(x, y, z);
							cx = x;
							cy = y;
							cz = z;
						}

			// background: window minimum
			double min = Double.MAX_VALUE;
			for (int z = cz - axWin; z <= cz + axWin; z++)
				for (int y = cy - latWin; y <= cy + latWin; y++)
					for (int x = cx - latWin; x <= cx + latWin; x++)
						min = Math.min(min, get(x, y, z));

			double sx = 0, sy = 0, sz = 0, s = 0;
			for (int z = cz - axWin; z <= cz + axWin; z++)
				for (int y = cy - latWin; y <= cy + latWin; y++)
					for (int x = cx - latWin; x <= cx + latWin; x++) {
						if (x < 0 || y < 0 || z < 0 || x >= w || y >= h || z >= d)
							continue;
						double val = get(x, y, z) - min;
						sx += val * x;
						sy += val * y;
						sz += val * z;
						s += val;
					}
			return new double[] { sx / s, sy / s, sz / s };
		}
	}
}
