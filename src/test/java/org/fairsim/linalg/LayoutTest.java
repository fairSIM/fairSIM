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

import static org.fairsim.linalg.LinalgFixtures.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Layout ops: pasteFreq, project / slice, quadrant swap, size checks */
public class LayoutTest {

	/** reference zero-padding: signed frequency index kept, plus offset, unshifted layout */
	static double[] pasteFreqRef(double[] in, int wi, int hi, int wo, int ho, int xOff, int yOff) {
		double[] out = new double[2 * wo * ho];
		for (int y = 0; y < hi; y++)
			for (int x = 0; x < wi; x++) {
				int fx = ((x < (wi + 1) / 2) ? x : x - wi) + xOff;
				int fy = ((y < (hi + 1) / 2) ? y : y - hi) + yOff;
				int o = (fx % wo + wo) % wo + wo * ((fy % ho + ho) % ho);
				out[2 * o] = in[2 * (x + wi * y)];
				out[2 * o + 1] = in[2 * (x + wi * y) + 1];
			}
		return out;
	}

	static void checkPasteFreq(int wi, int hi, int wo, int ho, int xOff, int yOff) {
		Vec2d.Cplx in = randomCplx(wi, hi, 1);
		Vec2d.Cplx out = Vec2d.createCplx(wo, ho);
		double[] ref = pasteFreqRef(toDouble(in.vectorData()), wi, hi, wo, ho, xOff, yOff);
		out.pasteFreq(in, xOff, yOff);
		assertClose(out.vectorData(), ref, 0, "pasteFreq " + wi + "x" + hi + " -> " + wo + "x" + ho);
	}

	/** 2x even (the reconstruction case), odd sizes, other factors, offsets */
	@ParameterizedTest(name = "{0}x{1} -> {2}x{3}, offset ({4},{5})")
	@CsvSource({ "8,6,16,12,0,0", "7,5,14,10,0,0", "8,6,24,18,0,0", "8,6,16,12,3,-2", "7,5,14,10,-9,4", "8,6,8,6,0,0" })
	void pasteFreqGeneral(int wi, int hi, int wo, int ho, int xOff, int yOff) {
		checkPasteFreq(wi, hi, wo, ho, xOff, yOff);
	}

	@Test
	void pasteFreqTooSmallThrows() {
		final Vec2d.Cplx in = randomCplx(8, 6, 1), out = Vec2d.createCplx(6, 6);
		assertThrows(IllegalArgumentException.class, () -> out.pasteFreq(in));
	}

	/** 3D, same signed-frequency rule on all three axes */
	@ParameterizedTest(name = "{0}x{1}x{2} -> {3}x{4}x{5}")
	@CsvSource({ "8,6,4,16,12,4", "7,5,3,14,10,6" })
	void pasteFreq3d(int wi, int hi, int ti, int wo, int ho, int to) {
		Vec3d.Cplx in = randomCplx(wi, hi, ti, 1);
		Vec3d.Cplx out = Vec3d.createCplx(wo, ho, to);
		double[] d = toDouble(in.vectorData());
		double[] ref = new double[2 * wo * ho * to];
		for (int z = 0; z < ti; z++)
			for (int y = 0; y < hi; y++)
				for (int x = 0; x < wi; x++) {
					int fx = (x < (wi + 1) / 2) ? x : x - wi;
					int fy = (y < (hi + 1) / 2) ? y : y - hi;
					int fz = (z < (ti + 1) / 2) ? z : z - ti;
					int o = (fx + wo) % wo + wo * ((fy + ho) % ho + ho * ((fz + to) % to));
					int i = x + wi * (y + hi * z);
					ref[2 * o] = d[2 * i];
					ref[2 * o + 1] = d[2 * i + 1];
				}
		out.pasteFreq(in);
		assertClose(out.vectorData(), ref, 0, "pasteFreq 3D");
	}

	/** project sums z planes, slice extracts one */
	static double[] projectRef(double[] in, int w, int h, int start, int end) {
		double[] out = new double[2 * w * h];
		for (int z = start; z <= end; z++)
			for (int i = 0; i < w * h; i++) {
				out[2 * i] += in[2 * (i + w * h * z)];
				out[2 * i + 1] += in[2 * (i + w * h * z) + 1];
			}
		return out;
	}

	@Test
	void realProjectAndSlice() {
		Vec3d.Real v = Vec3d.createReal(5, 4, 3);
		fill(v.vectorData(), 1);
		v.syncBuffer();
		double[] in = toDouble(v.vectorData());
		Vec2d.Real p = Vec2d.createReal(5, 4);

		double[] ref = new double[20];
		for (int z = 0; z < 3; z++)
			for (int i = 0; i < 20; i++)
				ref[i] += in[i + 20 * z];
		p.project(v);
		assertClose(p.vectorData(), ref, 1e-6, "project");

		p.slice(v, 2);
		double[] sl = new double[20];
		System.arraycopy(in, 40, sl, 0, 20);
		assertClose(p.vectorData(), sl, 0, "slice");
	}

	/** complex project / slice keep the imaginary part */
	@Test
	void cplxProjectAndSlice() {
		Vec3d.Cplx v = randomCplx(5, 4, 3, 1);
		double[] in = toDouble(v.vectorData());
		Vec2d.Cplx p = Vec2d.createCplx(5, 4);

		p.project(v);
		assertClose(p.vectorData(), projectRef(in, 5, 4, 0, 2), 1e-6, "project");

		p.slice(v, 1);
		assertClose(p.vectorData(), projectRef(in, 5, 4, 1, 1), 0, "slice");
	}

	/** reference fftshift: element (x,y) moves to ((x + w/2) % w, (y + h/2) % h) */
	static double[] fftshiftRef(double[] in, int w, int h) {
		double[] out = new double[in.length];
		for (int y = 0; y < h; y++)
			for (int x = 0; x < w; x++) {
				int o = (x + w / 2) % w + w * ((y + h / 2) % h);
				out[2 * o] = in[2 * (x + w * y)];
				out[2 * o + 1] = in[2 * (x + w * y) + 1];
			}
		return out;
	}

	@Test
	void swapQuadrantEven() {
		Vec2d.Cplx v = randomCplx(6, 4, 1);
		double[] ref = fftshiftRef(toDouble(v.vectorData()), 6, 4);
		Transforms.swapQuadrant(v);
		assertClose(v.vectorData(), ref, 0, "swapQuadrant 6x4");
	}

	/** swapQuadrant on odd sizes (display only) */
	@ParameterizedTest(name = "{0}x{1}")
	@CsvSource({ "5,4", "4,5", "5,5" })
	void swapQuadrantOdd(int w, int h) {
		Vec2d.Cplx v = randomCplx(w, h, 1);
		double[] ref = fftshiftRef(toDouble(v.vectorData()), w, h);
		Transforms.swapQuadrant(v);
		assertClose(v.vectorData(), ref, 0, "swapQuadrant cplx");

		Vec2d.Real r = randomReal(w, h, 2);
		double[] rd = toDouble(r.vectorData()), rref = new double[rd.length];
		for (int y = 0; y < h; y++)
			for (int x = 0; x < w; x++)
				rref[(x + w / 2) % w + w * ((y + h / 2) % h)] = rd[x + w * y];
		Transforms.swapQuadrant(r);
		assertClose(r.vectorData(), rref, 0, "swapQuadrant real");
	}

	/** power spectrum scales by magnitudes: the largest |z| maps to 1, also if it has negative components */
	@Test
	void powerSpectrumScaling() {
		Vec2d.Cplx v = Vec2d.createCplx(5, 4);
		java.util.Arrays.fill(v.vectorData(), 0.01f);
		v.syncBuffer();
		v.set(0, 0, new Cplx.Float(-5f, 0f));
		v.set(1, 0, new Cplx.Float(1f, 0f));
		Vec2d.Real out = Vec2d.createReal(5, 4), sw = Vec2d.createReal(5, 4);
		Transforms.computePowerSpectrum(v, out, false);
		Transforms.computePowerSpectrum(v, sw, true);

		float[] o = out.vectorData();
		assertClose(new float[] { o[0] }, new double[] { 1 }, 1e-6, "max at the largest magnitude");
		for (float f : o)
			org.junit.jupiter.api.Assertions.assertTrue(f >= 0 && f <= 1, "range 0..1: " + f);

		// swapped output is the same values, moved
		double[] ref = new double[o.length];
		for (int y = 0; y < 4; y++)
			for (int x = 0; x < 5; x++)
				ref[(x + 2) % 5 + 5 * ((y + 2) % 4)] = o[x + 5 * y];
		assertClose(sw.vectorData(), ref, 0, "swapped power spectrum");
	}

	/** element-wise ops on 2D vectors of different shape but equal element count */
	@Test
	void shapeMismatchThrows() {
		final Vec2d.Cplx a = randomCplx(6, 4, 1), b = randomCplx(4, 6, 2);
		assertThrows(RuntimeException.class, () -> a.times(b));
		final Vec3d.Cplx c = randomCplx(6, 4, 2, 1), d = randomCplx(4, 6, 2, 2);
		assertThrows(RuntimeException.class, () -> c.add(d));

		// a plain vector of the same length still combines with a 2D one
		Vec.Cplx e = Vec.createCplx(24);
		a.times(e);
	}
}
