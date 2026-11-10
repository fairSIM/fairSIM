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
import org.fairsim.utils.Tool;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Full 2D reconstruction of a synthetic bead surface. Asserts physical
 * results only (pattern frequency, bead positions, resolution), so these
 * hold independent of linalg index conventions.
 */
public class SyntheticReconstructionTest {

	static SyntheticSim sim;
	static SimParam param;
	static Vec2d.Real result, widefield;

	@BeforeAll
	static void reconstruct() {
		sim = new SyntheticSim().run();

		OtfProvider otf = OtfProvider.fromEstimate(sim.na, sim.lambda, sim.otfA);
		param = SimParam.create(2, sim.nrDirs, sim.nrPhases, sim.size, sim.pxl, otf);

		// input: background, window, fft
		Vec2d.Cplx[][] in = new Vec2d.Cplx[sim.nrDirs][sim.nrPhases];
		for (int a = 0; a < sim.nrDirs; a++)
			for (int p = 0; p < sim.nrPhases; p++) {
				Vec2d.Real r = Vec2d.createReal(sim.size, sim.size);
				System.arraycopy(sim.raw[a][p], 0, r.vectorData(), 0, sim.size * sim.size);
				r.syncBuffer();
				in[a][p] = Vec2d.createCplx(sim.size, sim.size);
				in[a][p].copy(r);
				SimUtils.subtractBackground(in[a][p], sim.offset);
				SimUtils.fadeBorderCos(in[a][p], 15);
				in[a][p].fft2d(false);
			}

		SimAlgorithm.estimateParameters(param, in, 1, 0.6, null, -1, null);

		param.setWienerFilter(0.05);
		param.setApoCutoff(1.9);
		param.setApoBend(0.8);

		widefield = Vec2d.createReal(2 * sim.size, 2 * sim.size);
		result = SimAlgorithm.runReconstruction(param, in, null, -1, true,
				SimParam.CLIPSCALE.NONE, widefield, null, null);

		TestImages.save("synthetic-beads", sim, result, widefield);
	}

	/** Estimated pattern frequency matches the simulated one in length (sign- and axis-free) */
	@Test
	void patternFrequencyLength() {
		for (int a = 0; a < sim.nrDirs; a++) {
			double[] k = sim.kPixels(a);
			double est = param.dir(a).getPxPyLen(1);
			Tool.trace(String.format("dir %d: sim k (%7.3f, %7.3f) est p (%7.3f, %7.3f)",
					a, k[0], k[1], param.dir(a).px(1), param.dir(a).py(1)));
			assertEquals(Math.hypot(k[0], k[1]), est, 0.05, "dir " + a);
		}
	}

	/** Isolated beads appear at their true positions (catches flips and transposes) */
	@Test
	void beadPositions() {
		int n = 0;
		double maxErr = 0;
		for (SyntheticSim.Bead b : sim.beads) {
			if (b.inPair)
				continue;
			double ex = 2 * (b.x / sim.pxl - sim.binOffset());
			double ey = 2 * (b.y / sim.pxl - sim.binOffset());
			double[] c = TestImages.centroid(result, ex, ey, 3, 2);
			double err = Math.hypot(c[0] - ex, c[1] - ey);
			maxErr = Math.max(maxErr, err);
			n++;
		}
		Tool.trace(String.format("bead positions: %d beads, max error %5.3f px (output grid)", n, maxErr));
		assertTrue(n > 50, "too few isolated beads: " + n);
		assertTrue(maxErr < 0.5, "max bead position error " + maxErr + " px");
	}

	/** Bead pairs below the widefield limit are resolved by SIM, not in widefield */
	@Test
	void pairsResolved() {
		int pairs = 0, simRes = 0, wfRes = 0;
		for (int i = 0; i + 1 < sim.beads.size(); i++) {
			SyntheticSim.Bead b1 = sim.beads.get(i), b2 = sim.beads.get(i + 1);
			if (!b1.inPair || !b2.inPair)
				continue;
			double x1 = 2 * (b1.x / sim.pxl - sim.binOffset()), y1 = 2 * (b1.y / sim.pxl - sim.binOffset());
			double x2 = 2 * (b2.x / sim.pxl - sim.binOffset()), y2 = 2 * (b2.y / sim.pxl - sim.binOffset());
			if (TestImages.dipRatio(result, x1, y1, x2, y2) < 0.9)
				simRes++;
			if (TestImages.dipRatio(widefield, x1, y1, x2, y2) < 0.9)
				wfRes++;
			pairs++;
			i++;
		}
		Tool.trace(String.format("pairs: %d, resolved SIM %d, widefield %d", pairs, simRes, wfRes));
		assertTrue(pairs > 50, "too few pairs: " + pairs);
		assertTrue(simRes > 0.8 * pairs, "SIM resolved only " + simRes + " of " + pairs);
		assertTrue(wfRes < 0.2 * pairs, "widefield resolved " + wfRes + " of " + pairs + ", simulation too easy");
	}
}
