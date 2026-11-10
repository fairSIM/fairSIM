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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

/** Same-size FFTs from several threads must not interfere (shared cached instances) */
public class TransformsConcurrencyTest {

	static final int N = 64, THREADS = 4, ROUNDS = 300;

	static Vec2d.Cplx random(long seed) {
		Vec2d.Cplx v = Vec2d.createCplx(N, N);
		Random r = new Random(seed);
		float[] d = v.vectorData();
		for (int i = 0; i < d.length; i++)
			d[i] = (float) r.nextGaussian();
		v.syncBuffer();
		return v;
	}

	/** max abs difference, relative to the max abs value of b */
	static double relDiff(Vec2d.Cplx a, Vec2d.Cplx b) {
		float[] x = a.vectorData(), y = b.vectorData();
		double diff = 0, max = 0;
		for (int i = 0; i < x.length; i++) {
			diff = Math.max(diff, Math.abs(x[i] - y[i]));
			max = Math.max(max, Math.abs(y[i]));
		}
		return diff / max;
	}

	@Test
	void concurrentSameSizeFft() throws Exception {

		// references, computed single-threaded
		final Vec2d.Cplx[] in = new Vec2d.Cplx[THREADS], ref = new Vec2d.Cplx[THREADS];
		for (int t = 0; t < THREADS; t++) {
			in[t] = random(t);
			ref[t] = in[t].duplicate();
			ref[t].fft2d(false);
		}

		// each thread: forward and inverse, compare both
		ExecutorService ex = Executors.newFixedThreadPool(THREADS);
		List<Callable<Integer>> jobs = new ArrayList<Callable<Integer>>();
		for (int t = 0; t < THREADS; t++) {
			final int tt = t;
			jobs.add(new Callable<Integer>() {
				public Integer call() {
					int bad = 0;
					for (int i = 0; i < ROUNDS; i++) {
						Vec2d.Cplx v = in[tt].duplicate();
						v.fft2d(false);
						if (relDiff(v, ref[tt]) > 1e-5)
							bad++;
						v.fft2d(true);
						if (relDiff(v, in[tt]) > 1e-5)
							bad++;
					}
					return bad;
				}
			});
		}
		int bad = 0;
		for (Future<Integer> f : ex.invokeAll(jobs))
			bad += f.get();
		ex.shutdown();
		assertEquals(0, bad, "wrong transforms under concurrency");

		// cached instance still intact afterwards
		Vec2d.Cplx v = random(99), r = v.duplicate();
		v.fft2d(false);
		v.fft2d(true);
		assertTrue(relDiff(v, r) < 1e-5, "FFT instance corrupted after concurrent use");
	}
}
