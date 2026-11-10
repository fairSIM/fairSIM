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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/** FFT against a naive double-precision DFT, on odd and non-square sizes */
public class FftTest {

	static final double TOL = 1e-5;

	@ParameterizedTest(name = "{0}x{1}")
	@CsvSource({ "8,6", "7,5", "5,9", "16,12", "32,32" })
	void fft2dMatchesDft(int w, int h) {
		Vec2d.Cplx v = randomCplx(w, h, 1);
		double[] in = toDouble(v.vectorData());

		v.fft2d(false);
		assertClose(v.vectorData(), dft(in, w, h, 1, false), TOL, "forward");

		v.fft2d(true);
		assertClose(v.vectorData(), in, TOL, "forward then inverse");
	}

	@ParameterizedTest(name = "n={0}")
	@CsvSource({ "7", "8", "30" })
	void fft1dMatchesDft(int n) {
		Vec.Cplx v = Vec.createCplx(n);
		fill(v.vectorData(), 2);
		v.syncBuffer();
		double[] in = toDouble(v.vectorData());

		Transforms.fft1d(v, false);
		assertClose(v.vectorData(), dft(in, n, 1, 1, false), TOL, "forward");

		Transforms.fft1d(v, true);
		assertClose(v.vectorData(), in, TOL, "forward then inverse");
	}

	@ParameterizedTest(name = "{0}x{1}x{2}")
	@CsvSource({ "8,6,4", "5,4,3" })
	void fft3dMatchesDft(int w, int h, int d) {
		Vec3d.Cplx v = randomCplx(w, h, d, 3);
		double[] in = toDouble(v.vectorData());

		v.fft3d(false);
		assertClose(v.vectorData(), dft(in, w, h, d, false), TOL, "forward");

		v.fft3d(true);
		assertClose(v.vectorData(), in, TOL, "forward then inverse");
	}

	/** a dimension of size 1 should reduce to the lower-dimensional FFT */
	@Test
	@Tag("defect")
	void fft2dWithHeightOne() {
		Vec2d.Cplx v = randomCplx(8, 1, 4);
		double[] in = toDouble(v.vectorData());
		v.fft2d(false);
		assertClose(v.vectorData(), dft(in, 8, 1, 1, false), TOL, "forward");
	}
}
