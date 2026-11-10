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
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Element-wise ops and reductions against plain loops in double */
public class VectorOpsTest {

	static final int W = 7, H = 5, N = W * H;
	static final double TOL = 1e-6;

	// --- complex ---

	/** a*b, or a*conj(b), per element, interleaved */
	static double[] mult(double[] a, double[] b, boolean conj) {
		double[] r = new double[a.length];
		for (int i = 0; i < a.length / 2; i++) {
			double br = b[2 * i], bi = conj ? -b[2 * i + 1] : b[2 * i + 1];
			r[2 * i] = a[2 * i] * br - a[2 * i + 1] * bi;
			r[2 * i + 1] = a[2 * i] * bi + a[2 * i + 1] * br;
		}
		return r;
	}

	@Test
	void cplxTimes() {
		Vec2d.Cplx a = randomCplx(W, H, 1), b = randomCplx(W, H, 2);
		double[] ref = mult(toDouble(a.vectorData()), toDouble(b.vectorData()), false);
		a.times(b);
		assertClose(a.vectorData(), ref, TOL, "times");
	}

	@Test
	void cplxTimesConj() {
		Vec2d.Cplx a = randomCplx(W, H, 1), b = randomCplx(W, H, 2);
		double[] ref = mult(toDouble(a.vectorData()), toDouble(b.vectorData()), true);
		a.timesConj(b);
		assertClose(a.vectorData(), ref, TOL, "timesConj");
	}

	@Test
	void cplxTimesReal() {
		Vec2d.Cplx a = randomCplx(W, H, 1);
		Vec2d.Real r = randomReal(W, H, 2);
		double[] ref = toDouble(a.vectorData());
		for (int i = 0; i < N; i++) {
			ref[2 * i] *= r.vectorData()[i];
			ref[2 * i + 1] *= r.vectorData()[i];
		}
		a.times(r);
		assertClose(a.vectorData(), ref, TOL, "times(Real)");
	}

	@Test
	void cplxAxpyAndAdd() {
		Vec2d.Cplx a = randomCplx(W, H, 1), b = randomCplx(W, H, 2);
		double[] x = toDouble(a.vectorData()), y = toDouble(b.vectorData());
		// this += c * y, c = (0.5, -2)
		double[] ref = new double[x.length];
		for (int i = 0; i < N; i++) {
			ref[2 * i] = x[2 * i] + 0.5 * y[2 * i] + 2 * y[2 * i + 1];
			ref[2 * i + 1] = x[2 * i + 1] + 0.5 * y[2 * i + 1] - 2 * y[2 * i];
		}
		a.axpy(new Cplx.Float(0.5f, -2f), b);
		assertClose(a.vectorData(), ref, TOL, "axpy");

		for (int i = 0; i < ref.length; i++)
			ref[i] += y[i];
		a.add(b);
		assertClose(a.vectorData(), ref, TOL, "add");
	}

	@Test
	void cplxReductions() {
		Vec2d.Cplx a = randomCplx(W, H, 1), b = randomCplx(W, H, 2);
		double[] x = toDouble(a.vectorData()), y = toDouble(b.vectorData());
		double dRe = 0, dIm = 0, n2 = 0, sRe = 0, sIm = 0;
		for (int i = 0; i < N; i++) {
			// conj(x) * y
			dRe += x[2 * i] * y[2 * i] + x[2 * i + 1] * y[2 * i + 1];
			dIm += x[2 * i] * y[2 * i + 1] - x[2 * i + 1] * y[2 * i];
			n2 += x[2 * i] * x[2 * i] + x[2 * i + 1] * x[2 * i + 1];
			sRe += x[2 * i];
			sIm += x[2 * i + 1];
		}
		Cplx.Double dot = a.dot(b), sum = a.sumElements();
		assertEquals(dRe, dot.re, 1e-4, "dot re");
		assertEquals(dIm, dot.im, 1e-4, "dot im");
		assertEquals(n2, a.norm2(), 1e-4, "norm2");
		assertEquals(sRe, sum.re, 1e-4, "sum re");
		assertEquals(sIm, sum.im, 1e-4, "sum im");
	}

	@Test
	void cplxReciprocAndAddSqr() {
		Vec2d.Cplx a = randomCplx(W, H, 1), b = randomCplx(W, H, 2);
		double[] x = toDouble(a.vectorData()), y = toDouble(b.vectorData());
		double[] rec = new double[x.length];
		for (int i = 0; i < N; i++) {
			double m = x[2 * i] * x[2 * i] + x[2 * i + 1] * x[2 * i + 1];
			rec[2 * i] = x[2 * i] / m;
			rec[2 * i + 1] = -x[2 * i + 1] / m;
		}
		Vec2d.Cplx r = a.duplicate();
		r.reciproc();
		assertClose(r.vectorData(), rec, 1e-5, "reciproc");

		// addSqr adds |y|^2 to the real part (used for Wiener denominators)
		double[] ref = x.clone();
		for (int i = 0; i < N; i++)
			ref[2 * i] += y[2 * i] * y[2 * i] + y[2 * i + 1] * y[2 * i + 1];
		a.addSqr(b);
		assertClose(a.vectorData(), ref, TOL, "addSqr");
	}

	/** an op with the vector itself as argument */
	@Test
	void cplxTimesSelf() {
		Vec2d.Cplx a = randomCplx(W, H, 1);
		double[] x = toDouble(a.vectorData());
		a.times(a);
		assertClose(a.vectorData(), mult(x, x, false), TOL, "times(self)");

		Vec2d.Cplx b = randomCplx(W, H, 2);
		double[] y = toDouble(b.vectorData());
		b.timesConj(b);
		assertClose(b.vectorData(), mult(y, y, true), TOL, "timesConj(self)");

		Vec2d.Cplx c = randomCplx(W, H, 3);
		double[] z = toDouble(c.vectorData()), ref = new double[z.length];
		for (int i = 0; i < N; i++) {
			// z + (0.5 - 2i) z
			ref[2 * i] = 1.5 * z[2 * i] + 2 * z[2 * i + 1];
			ref[2 * i + 1] = 1.5 * z[2 * i + 1] - 2 * z[2 * i];
		}
		c.axpy(new Cplx.Float(0.5f, -2f), c);
		assertClose(c.vectorData(), ref, TOL, "axpy(self)");
	}

	/** 1/z for large and small |z| (|z|^2 leaves the float range) */
	@Test
	void cplxReciprocRange() {
		Vec2d.Cplx a = Vec2d.createCplx(2, 1);
		a.set(0, 0, new Cplx.Float(1e20f, 1e20f));
		a.set(1, 0, new Cplx.Float(1e-25f, -1e-25f));
		a.reciproc();
		assertClose(a.vectorData(), new double[] { 0.5e-20, -0.5e-20, 0.5e25, 0.5e25 }, 1e-5, "reciproc range");

		Cplx.Float r = Cplx.reciproc(new Cplx.Float(1e20f, 1e20f));
		assertClose(new float[] { r.re, r.im }, new double[] { 0.5e-20, -0.5e-20 }, 1e-5, "scalar reciproc");
	}

	// --- real ---

	@Test
	void realElementOps() {
		Vec2d.Real a = randomReal(W, H, 1), b = randomReal(W, H, 2);
		double[] x = toDouble(a.vectorData()), y = toDouble(b.vectorData());
		double[] ref = new double[N];
		for (int i = 0; i < N; i++)
			ref[i] = (x[i] + 0.5 * y[i]) * y[i] + y[i] * y[i] + 1.5;
		a.axpy(0.5f, b);
		a.times(b);
		a.addSqr(b);
		a.addConst(1.5f);
		assertClose(a.vectorData(), ref, TOL, "axpy, times, addSqr, addConst");
	}

	@Test
	void realReductions() {
		Vec2d.Real a = randomReal(W, H, 1), b = randomReal(W, H, 2);
		double[] x = toDouble(a.vectorData()), y = toDouble(b.vectorData());
		double dot = 0, n2 = 0, sum = 0;
		for (int i = 0; i < N; i++) {
			dot += x[i] * y[i];
			n2 += x[i] * x[i];
			sum += x[i];
		}
		assertEquals(dot, a.dot(b), 1e-4, "dot");
		assertEquals(n2, a.norm2(), 1e-4, "norm2");
		assertEquals(sum, a.sumElements(), 1e-4, "sum");
		assertEquals(sum / N, a.avr(), 1e-5, "avr");
	}

	/** median of an even count is the mean of the two middle values */
	@Test
	void realMedianEven() {
		Vec.Real a = Vec.createReal(4);
		float[] d = a.vectorData();
		d[0] = 4;
		d[1] = 1;
		d[2] = 3;
		d[3] = 2;
		a.syncBuffer();
		assertEquals(2.5, a.median(), 1e-9);
	}

	/** max and normalize on all-negative data */
	@Test
	void realMaxNormalizeNegative() {
		Vec.Real a = Vec.createReal(3);
		float[] d = a.vectorData();
		d[0] = -3;
		d[1] = -2;
		d[2] = -1;
		a.syncBuffer();
		assertEquals(-1f, a.max(), "max");
		a.normalize();
		assertClose(a.vectorData(), new double[] { 0, 0.5, 1 }, 1e-6, "normalize");
	}

	/** normalize maps constant input to the lower bound, not NaN */
	@Test
	void realNormalizeConstant() {
		Vec.Real a = Vec.createReal(3);
		java.util.Arrays.fill(a.vectorData(), 2.5f);
		a.syncBuffer();
		a.normalize(1, 3);
		assertClose(a.vectorData(), new double[] { 1, 1, 1 }, 0, "normalize constant");
	}
}
