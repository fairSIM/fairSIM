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

import org.fairsim.linalg.Vec3d;

/**
 * Synthetic 3-beam 3D SIM data for tests: beads in a volume, pattern fixed to
 * the focal plane, pupil-cap OTF, pixel integration, approximated noise.
 * Coordinates: x = column, y = row, z = slice, in microns from the centre of voxel 0.
 * Raw stacks are built in Fourier space with plain double math (all bands below
 * Nyquist, so alias-free); only the final inverse FFT runs through linalg.
 */
public class SyntheticSim3D {

	// geometry
	public int size = 160; // lateral raw size, pixels (square: the 3D path scales frequencies by width)
	public int depth = 32; // slices
	public double pxl = 0.080, dz = 0.125; // microns

	// optics
	public double na = 1.4, n = 1.518, lambdaEm = 525, lambdaEx = 488;

	// pattern
	public int nrDirs = 3, nrPhases = 5;
	public double resImpr = 0.85; // band-2 frequency as fraction of the lateral OTF cutoff
	public double modDepth = 0.8; // scales bands 1 and 2
	public double angleOffset = 0.2; // first angle, fraction of 2pi

	// object
	public double margin = 1.6, zMargin = 0.8; // bead-free borders, microns
	public double minSepLat = 1.0, minSepAx = 1.6; // sites closer laterally need this axial distance, microns
	public double beadSigma = 0.04; // gaussian bead, microns
	public double pairLat = 0.16, pairAx = 0.5; // pair separations, microns (widefield resolves from ~0.2 / ~0.54)
	public int sites = 400; // placement attempts

	// camera
	public double photons = 2000; // brightest raw voxel
	public double offset = 100, readNoise = 2;
	public long seed = 42;

	/** Bead types; pairs are stored as consecutive beads */
	public enum Type {
		SINGLE, LATERAL_PAIR, AXIAL_PAIR
	}

	/** One bead, position in microns */
	public static class Bead {
		public final double x, y, z;
		public final Type type;

		Bead(double x, double y, double z, Type type) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.type = type;
		}
	}

	// results
	public final List<Bead> beads = new ArrayList<Bead>();
	public double[] kx, ky; // band-2 pattern wave vector, cycles/micron, per direction
	public double kz1; // axial modulation of band 1, cycles/micron
	public double[][] phase; // pattern phase (band 1), per direction and phase step
	public float[][][] raw; // [dir][phase][x + size*y + size*size*z], camera counts

	/** Run the simulation with the current parameters */
	public SyntheticSim3D run() {
		final double k2 = cutoffLateral() * resImpr;
		final double k1 = k2 / 2;
		final double kEx = n * 1000 / lambdaEx;
		if (k1 >= na * 1000 / lambdaEx)
			throw new IllegalArgumentException("side beams outside the NA");
		kz1 = kEx - Math.sqrt(kEx * kEx - k1 * k1);

		kx = new double[nrDirs];
		ky = new double[nrDirs];
		phase = new double[nrDirs][nrPhases];
		for (int a = 0; a < nrDirs; a++) {
			double ang = 2 * Math.PI * (angleOffset + a / (double) nrDirs);
			kx[a] = k2 * Math.cos(ang);
			ky[a] = k2 * Math.sin(ang);
			for (int p = 0; p < nrPhases; p++)
				phase[a][p] = 0.3 + 0.7 * a + 2 * Math.PI * p / nrPhases;
		}

		makeBeads();

		// detection transfer per band on the raw grid: OTF times pixel integration;
		// the pattern moves with the focus, so band 1's axial cos(kz1 z) shifts its OTF by +-kz1
		final int nv = size * size * depth;
		final double[] h0 = new double[nv], h1 = new double[nv];
		for (int z = 0; z < depth; z++)
			for (int y = 0; y < size; y++)
				for (int x = 0; x < size; x++) {
					double fx = freqLat(x), fy = freqLat(y), fz = freqAx(z);
					double q = Math.hypot(fx, fy);
					double pix = sinc(fx * pxl) * sinc(fy * pxl);
					int i = x + size * y + size * size * z;
					h0[i] = otf(q, fz) * pix;
					h1[i] = 0.5 * (otf(q, fz - kz1) + otf(q, fz + kz1)) * pix;
				}

		// pattern components: 1 + md (4/3) cos(k1 r + phi) cos(kz1 z) + md (2/3) cos(k2 r + 2 phi), md = modDepth
		final double[] cm = new double[] { modDepth / 3, modDepth * 2 / 3, 1, modDepth * 2 / 3, modDepth / 3 };

		raw = new float[nrDirs][nrPhases][];
		double max = 0;
		for (int a = 0; a < nrDirs; a++) {

			// object spectrum at shifted frequencies, weighted, per band m = -2..2
			double[][] g = new double[5][];
			for (int m = -2; m <= 2; m++) {
				double[] o = objectSpectrum(m * kx[a] / 2, m * ky[a] / 2);
				double[] h = (Math.abs(m) == 1) ? h1 : h0;
				for (int i = 0; i < nv; i++) {
					o[2 * i] *= cm[m + 2] * h[i];
					o[2 * i + 1] *= cm[m + 2] * h[i];
				}
				g[m + 2] = o;
			}

			// sum bands with the phase of each step, back to real space
			for (int p = 0; p < nrPhases; p++) {
				Vec3d.Cplx buf = Vec3d.createCplx(size, size, depth);
				float[] d = buf.vectorData();
				for (int m = -2; m <= 2; m++) {
					double cr = Math.cos(m * phase[a][p]), ci = Math.sin(m * phase[a][p]);
					double[] o = g[m + 2];
					for (int i = 0; i < nv; i++) {
						d[2 * i] += (float) (o[2 * i] * cr - o[2 * i + 1] * ci);
						d[2 * i + 1] += (float) (o[2 * i] * ci + o[2 * i + 1] * cr);
					}
				}
				buf.syncBuffer();
				buf.fft3d(true);
				d = buf.vectorData();
				float[] s = new float[nv];
				for (int i = 0; i < nv; i++) {
					s[i] = d[2 * i];
					max = Math.max(max, s[i]);
				}
				raw[a][p] = s;
			}
		}

		// scale, noise
		final Random noise = new Random(seed * 3 + 123);
		for (int a = 0; a < nrDirs; a++)
			for (int p = 0; p < nrPhases; p++)
				for (int i = 0; i < nv; i++) {
					double v = raw[a][p][i] * photons / max;
					v += noise.nextGaussian() * Math.sqrt(Math.max(v, 0));
					v += offset + noise.nextGaussian() * readNoise;
					raw[a][p][i] = (float) Math.max(v, 0);
				}
		return this;
	}

	/** Lateral OTF cutoff 2 NA / lambda, cycles/micron */
	public double cutoffLateral() {
		return 2 * na * 1000 / lambdaEm;
	}

	/** Axial OTF cutoff (n - sqrt(n^2 - NA^2)) / lambda, cycles/micron */
	public double cutoffAxial() {
		return (n - Math.sqrt(n * n - na * na)) * 1000 / lambdaEm;
	}

	/**
	 * Widefield OTF, autocorrelation of a uniform pupil cap (closed form), 1 at DC.
	 * @param q lateral frequency, cycles/micron
	 * @param kz axial frequency, cycles/micron (sign-free)
	 */
	public double otf(double q, double kz) {
		return otfShell(q, kz) / otfShell(0, 0);
	}

	private double otfShell(double q, double kz) {
		// held below one axial frequency step, to keep DC finite
		final double vMin = 1 / (depth * dz);
		final double kk = n * 1000 / lambdaEm;
		final double cosA = Math.sqrt(1 - (na / n) * (na / n));
		kz = Math.abs(kz);
		double v = Math.hypot(q, kz);
		if (v < vMin) {
			q = (v > 0) ? q * vMin / v : vMin;
			kz = (v > 0) ? kz * vMin / v : 0;
			v = vMin;
		}
		if (v >= 2 * kk || q <= 0)
			return 0;
		// shells |k| = K and |k - v| = K meet on a circle of radius rho = sqrt(K^2 - v^2/4),
		// weight K^2 / v per unit angle; both points lie on the caps (kz >= K cos a)
		// where cos(psi) <= -s, an arc of 2 acos(s), s = v (K cos a + |kz|/2) / (rho q)
		double rho = Math.sqrt(kk * kk - v * v / 4);
		double s = v * (kk * cosA + kz / 2) / (rho * q);
		if (s >= 1)
			return 0;
		return 2 * kk * kk / v * Math.acos(s);
	}

	/** Spectrum of all beads, evaluated at the raw grid frequencies minus (sx, sy), interleaved complex */
	private double[] objectSpectrum(double sx, double sy) {
		final int nv = size * size * depth;
		double[] ret = new double[2 * nv];
		double[] ex = new double[2 * size], ey = new double[2 * size], ez = new double[2 * depth];
		final double g = -2 * Math.PI * Math.PI * beadSigma * beadSigma;

		for (Bead b : beads) {
			// separable factors: gaussian bead times phase ramp exp(-2 pi i f r)
			for (int x = 0; x < size; x++)
				phaseFactor(ex, x, freqLat(x) - sx, b.x, g);
			for (int y = 0; y < size; y++)
				phaseFactor(ey, y, freqLat(y) - sy, b.y, g);
			for (int z = 0; z < depth; z++)
				phaseFactor(ez, z, freqAx(z), b.z, g);

			for (int z = 0; z < depth; z++)
				for (int y = 0; y < size; y++) {
					double tr = ey[2 * y] * ez[2 * z] - ey[2 * y + 1] * ez[2 * z + 1];
					double ti = ey[2 * y] * ez[2 * z + 1] + ey[2 * y + 1] * ez[2 * z];
					int off = 2 * (size * y + size * size * z);
					for (int x = 0; x < size; x++) {
						ret[off + 2 * x] += tr * ex[2 * x] - ti * ex[2 * x + 1];
						ret[off + 2 * x + 1] += tr * ex[2 * x + 1] + ti * ex[2 * x];
					}
				}
		}
		return ret;
	}

	private static void phaseFactor(double[] t, int i, double f, double pos, double g) {
		double amp = Math.exp(g * f * f);
		t[2 * i] = amp * Math.cos(-2 * Math.PI * f * pos);
		t[2 * i + 1] = amp * Math.sin(-2 * Math.PI * f * pos);
	}

	/** Random sites (no grid) with minimum separation; each is a single bead or a pair */
	private void makeBeads() {
		beads.clear();
		Random pos = new Random(seed);
		final double ext = size * pxl, extZ = depth * dz;
		List<double[]> centers = new ArrayList<double[]>();
		for (int i = 0; i < sites; i++) {
			double x = margin + (ext - 2 * margin) * pos.nextDouble();
			double y = margin + (ext - 2 * margin) * pos.nextDouble();
			double z = zMargin + (extZ - 2 * zMargin) * pos.nextDouble();
			boolean ok = true;
			for (double[] c : centers)
				if (Math.hypot(c[0] - x, c[1] - y) < minSepLat && Math.abs(c[2] - z) < minSepAx)
					ok = false;
			if (!ok)
				continue;
			centers.add(new double[] { x, y, z });

			double r = pos.nextDouble();
			if (r < 1. / 3) {
				beads.add(new Bead(x, y, z, Type.SINGLE));
			} else if (r < 2. / 3) {
				double ang = Math.PI * pos.nextDouble();
				double dx = Math.cos(ang) * pairLat / 2, dy = Math.sin(ang) * pairLat / 2;
				beads.add(new Bead(x - dx, y - dy, z, Type.LATERAL_PAIR));
				beads.add(new Bead(x + dx, y + dy, z, Type.LATERAL_PAIR));
			} else {
				beads.add(new Bead(x, y, z - pairAx / 2, Type.AXIAL_PAIR));
				beads.add(new Bead(x, y, z + pairAx / 2, Type.AXIAL_PAIR));
			}
		}
	}

	/** Lateral frequency of raw grid index i, cycles/micron */
	double freqLat(int i) {
		return ((i < size / 2) ? i : i - size) / (size * pxl);
	}

	/** Axial frequency of raw grid index i, cycles/micron */
	double freqAx(int i) {
		return ((i < depth / 2) ? i : i - depth) / (depth * dz);
	}

	private static double sinc(double u) {
		return (u == 0) ? 1 : Math.sin(Math.PI * u) / (Math.PI * u);
	}

	/** OTF table for OtfProvider3D.createFromData: [band][2 * (axial * samplesLat + lateral)], real, no pixel integration */
	public float[][] otfTable(double step, int samplesLat, int samplesAx) {
		float[][] ret = new float[3][2 * samplesLat * samplesAx];
		for (int z = 0; z < samplesAx; z++)
			for (int l = 0; l < samplesLat; l++) {
				int i = 2 * (z * samplesLat + l);
				double q = l * step, fz = z * step;
				ret[0][i] = (float) otf(q, fz);
				ret[1][i] = (float) (0.5 * (otf(q, fz - kz1) + otf(q, fz + kz1)));
				ret[2][i] = ret[0][i];
			}
		return ret;
	}

	/** Band-2 pattern wave vector of direction a in raw-image Fourier pixels (cycles per image) */
	public double[] kPixels(int a) {
		return new double[] { kx[a] * size * pxl, ky[a] * size * pxl };
	}
}
