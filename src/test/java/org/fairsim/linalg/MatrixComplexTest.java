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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Inverse, pseudo-inverse and LU decomposition of MatrixComplex */
public class MatrixComplexTest {

	static MatrixComplex real(int m, int n, double... v) {
		return MatrixComplex.initReal(m, n, v);
	}

	/** non-singular, but the pivot after the first elimination step is 0 without row swaps */
	@Test
	void inverseNeedsPivoting() {
		MatrixComplex a = real(3, 3, 1, 1, 0, 1, 1, 1, 0, 1, 1);
		double d = a.mult(a.inverse()).diffToEye();
		assertTrue(d < 1e-12, "A * inv(A) - I: " + d);

		Cplx.Double[] x = a.solve(new Cplx.Double[] { new Cplx.Double(1), new Cplx.Double(2), new Cplx.Double(3) });
		Cplx.Double[] b = a.mult(x);
		assertEquals(1, b[0].re, 1e-12);
		assertEquals(2, b[1].re, 1e-12);
		assertEquals(3, b[2].re, 1e-12);
	}

	@Test
	void randomComplexInverse() {
		Random r = new Random(1);
		MatrixComplex a = new MatrixComplex(5, 5);
		for (int i = 0; i < 5; i++)
			for (int j = 0; j < 5; j++)
				a.set(i, j, new Cplx.Double(r.nextGaussian(), r.nextGaussian()));
		double d = a.mult(a.inverse()).diffToEye();
		assertTrue(d < 1e-10, "A * inv(A) - I: " + d);
	}

	/** band separation matrices as built in BandSeparation: phases x (2 bands - 1) */
	@ParameterizedTest(name = "{0} bands, {1} phases, offset {2}")
	@CsvSource({ "2,3,0", "3,5,0", "3,7,0", "3,5,0.37" })
	void separationMatrixInverse(int bands, int nrPhases, double jitter) {
		MatrixComplex m = new MatrixComplex(nrPhases, 2 * bands - 1);
		for (int i = 0; i < nrPhases; i++) {
			m.set(i, 0, new Cplx.Double(1));
			// non-equidistant phases for jitter > 0
			double pha = 2 * Math.PI * i / nrPhases + jitter * i * i;
			for (int b = 1; b < bands; b++) {
				m.set(i, 2 * b - 1, Cplx.Double.fromPhase(b * pha));
				m.set(i, 2 * b, Cplx.Double.fromPhase(-b * pha));
			}
		}
		MatrixComplex inv = (nrPhases == 2 * bands - 1) ? m.inverse() : m.pseudoInverse();
		double d = inv.mult(m).diffToEye();
		assertTrue(d < 1e-10, "inv(M) * M - I: " + d);
	}

	@Test
	void pseudoInverseWide() {
		MatrixComplex a = real(2, 3, 1, 2, 3, 0, 1, 4);
		double d = a.mult(a.pseudoInverse()).diffToEye();
		assertTrue(d < 1e-10, "A * pinv(A) - I: " + d);
	}

	@Test
	void initRealAndMatrixVector() {
		MatrixComplex a = real(3, 2, 1, 2, 3, 4, 5, 6);
		assertEquals(3, a.get(1, 0).re, 0);
		assertEquals(6, a.get(2, 1).re, 0);

		Cplx.Double[] y = MatrixComplex.mult(a, new Cplx.Double[] { new Cplx.Double(1), new Cplx.Double(1) });
		assertEquals(3, y.length);
		assertEquals(3, y[0].re, 0);
		assertEquals(7, y[1].re, 0);
		assertEquals(11, y[2].re, 0);
	}
}
