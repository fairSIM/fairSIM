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

package org.fairsim.linalg;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

/** Fixtures and reference implementations for the linalg tests */
class LinalgFixtures {

	static Vec2d.Real randomReal(int w, int h, long seed) {
		Vec2d.Real v = Vec2d.createReal(w, h);
		fill(v.vectorData(), seed);
		v.syncBuffer();
		return v;
	}

	static Vec2d.Cplx randomCplx(int w, int h, long seed) {
		Vec2d.Cplx v = Vec2d.createCplx(w, h);
		fill(v.vectorData(), seed);
		v.syncBuffer();
		return v;
	}

	static Vec3d.Cplx randomCplx(int w, int h, int d, long seed) {
		Vec3d.Cplx v = Vec3d.createCplx(w, h, d);
		fill(v.vectorData(), seed);
		v.syncBuffer();
		return v;
	}

	static void fill(float[] d, long seed) {
		Random r = new Random(seed);
		for (int i = 0; i < d.length; i++)
			d[i] = (float) r.nextGaussian();
	}

	/** copy of the raw data as double */
	static double[] toDouble(float[] in) {
		double[] ret = new double[in.length];
		for (int i = 0; i < in.length; i++)
			ret[i] = in[i];
		return ret;
	}

	/**
	 * Naive DFT in double precision, interleaved complex, index x + w*(y + h*z).
	 * forward: exp(-2 pi i k.n/N), unscaled; inverse: exp(+...), scaled 1/N
	 */
	static double[] dft(double[] in, int w, int h, int d, boolean inverse) {
		final double sgn = inverse ? 1 : -1;
		final int n = w * h * d;
		double[] out = new double[2 * n];
		for (int kz = 0; kz < d; kz++)
			for (int ky = 0; ky < h; ky++)
				for (int kx = 0; kx < w; kx++) {
					double re = 0, im = 0;
					for (int z = 0; z < d; z++)
						for (int y = 0; y < h; y++)
							for (int x = 0; x < w; x++) {
								double ph = sgn * 2 * Math.PI
										* ((double) kx * x / w + (double) ky * y / h + (double) kz * z / d);
								double c = Math.cos(ph), s = Math.sin(ph);
								int i = x + w * (y + h * z);
								re += in[2 * i] * c - in[2 * i + 1] * s;
								im += in[2 * i] * s + in[2 * i + 1] * c;
							}
					int o = kx + w * (ky + h * kz);
					out[2 * o] = inverse ? re / n : re;
					out[2 * o + 1] = inverse ? im / n : im;
				}
		return out;
	}

	/** max abs difference relative to the max abs value of 'ref' */
	static double relDiff(float[] got, double[] ref) {
		double diff = 0, max = 0;
		for (int i = 0; i < ref.length; i++) {
			diff = Math.max(diff, Math.abs(got[i] - ref[i]));
			max = Math.max(max, Math.abs(ref[i]));
		}
		return diff / max;
	}

	static void assertClose(float[] got, double[] ref, double tol, String what) {
		assertTrue(got.length == ref.length, what + ": length " + got.length + " vs " + ref.length);
		double d = relDiff(got, ref);
		assertTrue(d <= tol, what + ": relative difference " + d);
	}
}
