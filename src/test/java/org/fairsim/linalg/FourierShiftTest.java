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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * fourierShift multiplies element (x,y,z) by exp(2 pi i (kx x/w + ky y/h + kz z/d)),
 * so after an FFT, content at index k sits at k + (kx,ky,kz). No axis is flipped.
 */
public class FourierShiftTest {

	/** reference phase multiply in double */
	static double[] shiftRef(double[] in, int w, int h, int d, double kx, double ky, double kz) {
		double[] out = new double[in.length];
		for (int z = 0; z < d; z++)
			for (int y = 0; y < h; y++)
				for (int x = 0; x < w; x++) {
					double ph = 2 * Math.PI * (kx * x / w + ky * y / h + kz * z / d);
					double c = Math.cos(ph), s = Math.sin(ph);
					int i = x + w * (y + h * z);
					out[2 * i] = in[2 * i] * c - in[2 * i + 1] * s;
					out[2 * i + 1] = in[2 * i] * s + in[2 * i + 1] * c;
				}
		return out;
	}

	/** whole-pixel shift of a single frequency: moves it to (x0+kx, y0+ky), mod size */
	@ParameterizedTest(name = "{0}x{1} by ({2},{3})")
	@CsvSource({ "16,16,2,1", "16,12,-4,3", "15,9,5,-7", "12,16,0,-7" })
	void integerShiftMovesContent(int w, int h, int kx, int ky) {
		final int x0 = 3, y0 = 5;
		Vec2d.Cplx v = Vec2d.createCplx(w, h);
		v.set(x0, y0, new Cplx.Float(1, 0));
		v.fft2d(true);
		v.fourierShift(kx, ky);
		v.fft2d(false);

		double[] ref = new double[2 * w * h];
		ref[2 * (((x0 + kx) % w + w) % w + w * (((y0 + ky) % h + h) % h))] = 1;
		assertClose(v.vectorData(), ref, 1e-5, "content position");
	}

	/** fractional shifts on non-square data, against double precision */
	@ParameterizedTest(name = "{0}x{1} by ({2},{3})")
	@CsvSource({ "16,12,1.5,-2.25", "15,9,0.3,4.7", "512,512,130.3,-97.7" })
	void fractionalShiftPrecision(int w, int h, double kx, double ky) {
		Vec2d.Cplx v = randomCplx(w, h, 1);
		double[] ref = shiftRef(toDouble(v.vectorData()), w, h, 1, kx, ky, 0);
		v.fourierShift(kx, ky);
		assertClose(v.vectorData(), ref, 2e-6, "phase multiply");
	}

	@ParameterizedTest(name = "{0}x{1}x{2} by ({3},{4},{5})")
	@CsvSource({ "8,6,4,1.5,-2.25,0.75", "64,48,16,30.3,-21.7,5.1" })
	void fractionalShift3d(int w, int h, int d, double kx, double ky, double kz) {
		Vec3d.Cplx v = randomCplx(w, h, d, 2);
		double[] ref = shiftRef(toDouble(v.vectorData()), w, h, d, kx, ky, kz);
		v.fourierShift(kx, ky, kz);
		assertClose(v.vectorData(), ref, 2e-6, "phase multiply");
	}
}
