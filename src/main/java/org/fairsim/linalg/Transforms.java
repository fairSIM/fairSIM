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

import org.fairsim.utils.SimpleMT;
import org.fairsim.utils.Tool;

import java.util.Map;
import java.util.TreeMap;

/**
 * Provides FFTs for vector elements.
 */
public abstract class Transforms {

	/** Two-dimensional FFT of the input vector. */
	static public void fft2d(Vec2d.Cplx in, boolean inverse) {
		in.fft2d(inverse);
	}

	/** Three-dimensional FFT of the input vector. */
	static public void fft3d(Vec3d.Cplx in, boolean inverse) {
		in.fft3d(inverse);
	}

	static public void runfft(Vec2d.Cplx in, boolean inverse) {
		// get parameters
		final int w = in.vectorWidth();
		final int h = in.vectorHeight();
		// see if we have an instance already, otherwise make one
		final FFTkey key = keyFor(w, h, 1);
		if (key == null)
			return;
		FftProvider.Instance ffti = getOrCreateInstance(key);
		float[] dat = in.vectorData();
		transform(ffti, dat, inverse);
		in.syncBuffer();
	}

	static public void runfft3d(Vec3d.Cplx in, boolean inverse) {
		// get parameters
		final int w = in.vectorWidth();
		final int h = in.vectorHeight();
		final int d = in.vectorDepth();
		// see if we have an instance already, otherwise make one
		final FFTkey key = keyFor(w, h, d);
		if (key == null)
			return;
		FftProvider.Instance ffti = getOrCreateInstance(key);
		float[] dat = in.vectorData();
		transform(ffti, dat, inverse);
		in.syncBuffer();
	}

	/** One-dimensional FFT of the complex input vector. */
	static public void fft1d(Vec.Cplx in, boolean inverse) {
		final int len = in.vectorSize();
		FftProvider.Instance ffti = getOrCreateInstance(new FFTkey(len));
		float[] dat = in.vectorData();
		transform(ffti, dat, inverse);
		in.syncBuffer();
	}

	/**
	 * One-dimensional FFT of a standard float array. This assumes
	 * standard packing ( a[i*2] = real[i], a[i*2+1] =imag[i] ) of
	 * the input array
	 */
	static public void fft1d(float[] in, boolean inverse) {
		final int len = in.length / 2;
		FftProvider.Instance ffti = getOrCreateInstance(new FFTkey(len));
		transform(ffti, in, inverse);
	}

	// -----------------------------------------------------------
	// Instance management

	/** key to store instances */
	private static class FFTkey implements Comparable<FFTkey> {
		final int d, x, y, z;

		FFTkey(int xi) {
			d = 1;
			x = xi;
			y = -1;
			z = -1;
		}

		FFTkey(int xi, int yi) {
			d = 2;
			x = xi;
			y = yi;
			z = -1;
		}

		FFTkey(int xi, int yi, int zi) {
			d = 3;
			x = xi;
			y = yi;
			z = zi;
		}

		@Override
		public int compareTo(FFTkey t) {
			if (d != t.d)
				return (t.d - d);
			if (x != t.x)
				return (t.x - x);
			if (y != t.y)
				return (t.y - y);
			if (z != t.z)
				return (t.z - z);
			return 0;
		}
	}

	/** key over the axes longer than 1: size-1 axes do not change the transform, null for a single element */
	static private FFTkey keyFor(int w, int h, int d) {
		int[] n = new int[3];
		int c = 0;
		if (w > 1)
			n[c++] = w;
		if (h > 1)
			n[c++] = h;
		if (d > 1)
			n[c++] = d;
		if (c == 0)
			return null;
		if (c == 1)
			return new FFTkey(n[0]);
		if (c == 2)
			return new FFTkey(n[0], n[1]);
		return new FFTkey(n[0], n[1], n[2]);
	}

	/** FFT instances */
	static private Map<FFTkey, FftProvider.Instance> instances;

	/** static initialization of the instances list. */
	static {
		if (instances == null) {
			instances = new TreeMap<FFTkey, FftProvider.Instance>();
		}
	}

	/** runs a cached instance, one transform at a time: JTransforms instances are not thread-safe */
	static private void transform(FftProvider.Instance ffti, float[] dat, boolean inverse) {
		synchronized (ffti) {
			ffti.fftTransform(dat, inverse);
		}
	}

	/** returns an instance, creates one if none exists */
	static synchronized protected FftProvider.Instance getOrCreateInstance(final FFTkey k) {
		FftProvider.Instance ffti = instances.get(k);
		if (ffti != null)
			return ffti;
		// Tool.trace("FFT: creating new instance");
		if (k.d == 1)
			ffti = FftProvider.get1Dfft(k.x);
		if (k.d == 2)
			ffti = FftProvider.get2Dfft(k.x, k.y);
		if (k.d == 3)
			ffti = FftProvider.get3Dfft(k.x, k.y, k.z);
		if (ffti == null)
			throw new RuntimeException("Unsupported dimensions");
		instances.put(k, ffti);
		return ffti;
	}

	// ---------------------------------------------------------
	//
	// abtract functions for the actual transformations
	//
	// ---------------------------------------------------------

	/**
	 * 1D Fourier tranformation complexFloat2complexFloat.
	 * This has to be implemented in a subclass. For convenience,
	 * all necessary parameters are passed.
	 */
	protected abstract void fft_1d_trans_c2c(float[] x, boolean inverse);

	/**
	 * 2D Fourier tranformation complexFloat2complexFloat.
	 * This has to be implemented in a subclass. For convenience,
	 * all necessary parameters are passed.
	 */
	protected abstract void fft_2d_trans_c2c(float[] x, boolean inverse);

	/**
	 * 3D Fourier tranformation complexFloat2complexFloat.
	 * This has to be implemented in a subclass. For convenience,
	 * all necessary parameters are passed.
	 */
	protected abstract void fft_3d_trans_c2c(float[] x, boolean inverse);

	/** checks if input is 2^n */
	static public boolean powerOf2(int l) {
		int i = 1;
		while (i < l)
			i *= 2;
		return (i == l);
	}

	// ---------------------------------------------------------
	//
	// Power spectrum calculations
	//
	// ---------------------------------------------------------

	/**
	 * Computes a power spectrum, assuming the input vector is an FFT spectrum.
	 * Note: The spectrum will be clipped and quadrant-swapped for display purposes.
	 */
	static public void computePowerSpectrum(Vec2d.Cplx inV, Vec2d.Real outV) {
		computePowerSpectrum(inV, outV, true);
	}

	/**
	 * Computes a power spectrum, assuming the input vector is an FFT spectrum.
	 * Note: The spectrum is clipped to log(max)-log(min)<30, and can be
	 * quadrand-swapped.
	 */
	static public void computePowerSpectrum(Vec2d.Cplx inV, Vec2d.Real outV,
			boolean swap_quad) {

		// TODO: maybe speed this up to higher efficience
		final int w = inV.vectorWidth();
		final int h = inV.vectorHeight();
		float[] in = inV.vectorData();
		float[] out = outV.vectorData();

		if ((w != outV.vectorWidth()) || (h != outV.vectorHeight()))
			throw new RuntimeException("Vector size mismatch");

		// magnitudes, and their min and max for scaling
		final float[] mag = new float[w * h];
		float min = Float.MAX_VALUE;
		float max = 0;
		for (int i = 0; i < w * h; i++) {
			mag[i] = (float) Math.hypot(in[2 * i], in[2 * i + 1]);
			if (mag[i] < min)
				min = mag[i];
			if (mag[i] > max)
				max = mag[i];
		}

		// reduce the range
		max = (float) Math.log(max);
		min = (float) Math.log(min);
		if (Float.isNaN(min) || max - min > 30)
			min = max - 30;

		for (int y = 0; y < h; y++)
			for (int x = 0; x < w; x++) {
				float r = (float) ((Math.log(mag[y * w + x]) - min) / (max - min));
				if (Float.isNaN(r) || r < 0)
					r = 0f;

				if (swap_quad) {
					out[((y + h / 2) % h) * w + (x + w / 2) % w] = r;
				} else {
					out[y * w + x] = r;
				}
			}

		outV.syncBuffer();

	}

	/** Swap quadrants: element (x,y) moves to ((x+w/2)%w, (y+h/2)%h), DC to the centre, also for odd sizes */
	static public void swapQuadrant(Vec2d.Cplx in) {
		final int w = in.vectorWidth();
		final int h = in.vectorHeight();
		final float[] dat = in.vectorData();
		final float[] tmp = dat.clone();
		for (int y = 0; y < h; y++)
			for (int x = 0; x < w; x++) {
				final int o = ((y + h / 2) % h) * w + (x + w / 2) % w;
				dat[2 * o] = tmp[2 * (y * w + x)];
				dat[2 * o + 1] = tmp[2 * (y * w + x) + 1];
			}
		in.syncBuffer();
	}

	/** Swap quadrants, see {@link #swapQuadrant(Vec2d.Cplx)} */
	static public void swapQuadrant(Vec2d.Real in) {
		final int w = in.vectorWidth();
		final int h = in.vectorHeight();
		final float[] dat = in.vectorData();
		final float[] tmp = dat.clone();
		for (int y = 0; y < h; y++)
			for (int x = 0; x < w; x++)
				dat[((y + h / 2) % h) * w + (x + w / 2) % w] = tmp[y * w + x];
		in.syncBuffer();
	}

	/** Swap quadrands in place. (this only works if all dimensions are even) */
	static public void swapQuadrant(Vec3d.Cplx in) {
		Vec3d.failEvenSizes(in);
		final int w = in.vectorWidth();
		final int h = in.vectorHeight();
		final int d = in.vectorDepth();
		for (int z0 = 0; z0 < (d / 2); z0++)
			for (int y0 = 0; y0 < h / 2; y0++)
				for (int x0 = 0; x0 < w / 2; x0++) {
					int x1 = x0 + w / 2, y1 = y0 + h / 2, z1 = z0 + d / 2;
					Cplx.Float tmpA, tmpB;
					// 000 <> 111
					tmpA = in.get(x0, y0, z0);
					tmpB = in.get(x1, y1, z1);
					in.set(x0, y0, z0, tmpB);
					in.set(x1, y1, z1, tmpA);
					// 010 <> 101
					tmpA = in.get(x0, y1, z0);
					tmpB = in.get(x1, y0, z1);
					in.set(x0, y1, z0, tmpB);
					in.set(x1, y0, z1, tmpA);
					// 100 <> 011
					tmpA = in.get(x1, y0, z0);
					tmpB = in.get(x0, y1, z1);
					in.set(x1, y0, z0, tmpB);
					in.set(x0, y1, z1, tmpA);
					// 001 <> 110
					tmpA = in.get(x0, y0, z1);
					tmpB = in.get(x1, y1, z0);
					in.set(x0, y0, z1, tmpB);
					in.set(x1, y1, z0, tmpA);
				}
	}

	/** Swap quadrands. This works for odd-sizes dimensions. */
	static public void swapQuadrant(Vec3d.Cplx in, Vec3d.Cplx out) {
		Vec3d.failSize(in, out);

		final int w = in.vectorWidth();
		final int h = in.vectorHeight();
		final int d = in.vectorDepth();
		for (int z0 = 0; z0 < d; z0++)
			for (int y0 = 0; y0 < h; y0++)
				for (int x0 = 0; x0 < w; x0++) {
					int x1 = (x0 + w / 2) % w, y1 = (y0 + h / 2) % h, z1 = (z0 + d / 2) % d;
					out.set(x1, y1, z1, in.get(x0, y0, z0));
				}
	}

	/** Swap quadrands in place. (this only works if all dimensions are even) */
	static public void swapQuadrant(Vec3d.Real in) {
		Vec3d.failEvenSizes(in);

		final int w = in.vectorWidth();
		final int h = in.vectorHeight();
		final int d = in.vectorDepth();
		for (int z0 = 0; z0 < d / 2; z0++)
			for (int y0 = 0; y0 < h / 2; y0++)
				for (int x0 = 0; x0 < w / 2; x0++) {
					int x1 = x0 + w / 2, y1 = y0 + h / 2, z1 = z0 + d / 2;
					float tmpA, tmpB;
					// 000 <> 111
					tmpA = in.get(x0, y0, z0);
					tmpB = in.get(x1, y1, z1);
					in.set(x0, y0, z0, tmpB);
					in.set(x1, y1, z1, tmpA);
					// 010 <> 101
					tmpA = in.get(x0, y1, z0);
					tmpB = in.get(x1, y0, z1);
					in.set(x0, y1, z0, tmpB);
					in.set(x1, y0, z1, tmpA);
					// 100 <> 011
					tmpA = in.get(x1, y0, z0);
					tmpB = in.get(x0, y1, z1);
					in.set(x1, y0, z0, tmpB);
					in.set(x0, y1, z1, tmpA);
					// 001 <> 110
					tmpA = in.get(x0, y0, z1);
					tmpB = in.get(x1, y1, z0);
					in.set(x0, y0, z1, tmpB);
					in.set(x1, y1, z0, tmpA);
				}
	}

	/*
	 * Return a vector containing phases for a Fourier shift theorems shift to
	 * kx,ky.
	 * 
	 * @param N Width and heigt of vector
	 * 
	 * @param kx x-coordinate of shift
	 * 
	 * @param ky y-coordinate of shift
	 * 
	 * @param fast Use faster, but less precise sin/cos (see {@link MTool#fsin})
	 */
	/*
	 * static public Vec2d.Cplx createShiftVector(
	 * final int N, final double kx, final double ky, final boolean fast ) {
	 * Vec2d.Cplx shft = Vec2d.createCplx(N,N);
	 * final float [] val = shft.vectorData();
	 * 
	 * // run outer loop in parallel
	 * new SimpleMT.PFor(0,N) {
	 * public void at(int y) {
	 * for (int x=0; x<N; x++) {
	 * float phaVal = (float)(2*Math.PI*(kx*x+ky*y)/N);
	 * if (fast) {
	 * val[ (y*N+x)*2+0 ] = (float)MTool.fcos( phaVal );
	 * val[ (y*N+x)*2+1 ] = (float)MTool.fsin( phaVal );
	 * } else {
	 * val[ (y*N+x)*2+0 ] = (float)Math.cos( phaVal );
	 * val[ (y*N+x)*2+1 ] = (float)Math.sin( phaVal );
	 * }
	 * }
	 * }
	 * };
	 * 
	 * shft.syncBuffer();
	 * 
	 * return shft;
	 * }
	 */

	/* See {@link #createShiftVector}, with fast='false' */
	/*
	 * static public Vec2d.Cplx createShiftVector(int N, double kx, double ky ) {
	 * return createShiftVector(N, kx, ky, false);
	 * }
	 */

	/**
	 * Multiply a vector with Fourier shift theorem phases, see {@link Vec2d.Cplx#fourierShift}.
	 * 
	 * @param kx   x-coordinate of shift
	 * @param ky   y-coordinate of shift
	 * @param fast ignored, phases are always computed in double precision
	 */
	@Deprecated
	static public void timesShiftVector(final Vec2d.Cplx vec,
			final double kx, final double ky, final boolean fast) {
		vec.fourierShift(kx, ky);
	}

	/** See {@link #timesShiftVector} */
	static public void timesShiftVector(final Vec2d.Cplx vec, double kx, double ky) {
		timesShiftVector(vec, kx, ky, false);
	}

	/**
	 * Compute the radial average around point x,y, with step size n.
	 * Every point in the input vector gets added to a bucket in the output vector.
	 * Position of the bucket is determined by the distance to x,y, divided by
	 * stepsize.
	 * The contribution is a weighted average, with the weight being the distance to
	 * the bucket center.
	 */
	public static void radialAveraging(Vec2d.Cplx in, Vec.Cplx out,
			double xPos, double yPos, double stepsize) {

		float[] inData = in.vectorData();
		float[] outData = out.vectorData();
		float[] weightData = new float[out.vectorSize()];
		final int w = in.vectorWidth();
		final int h = in.vectorHeight();
		final int nBuckets = out.vectorSize();
		final boolean normalize = true;

		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int idx = y * w + x;
				float re = inData[idx * 2 + 0];
				float im = inData[idx * 2 + 1];
				double dist = Math.sqrt(Math.pow(x - xPos, 2) + Math.pow(y - yPos, 2));
				int bucketLow = (int) (dist / stepsize);
				int bucketHigh = bucketLow + 1;
				double weightLow = bucketHigh * stepsize - dist;
				double weightHigh = 1 - weightLow;
				if (bucketLow < nBuckets && bucketLow >= 0) {
					outData[bucketLow * 2 + 0] += re * weightLow;
					outData[bucketLow * 2 + 1] += im * weightLow;
					// Tool.trace(String.format("Adding to low bucket %d: %f + i%f",
					// bucketLow, re * weightLow, im * weightLow));

					weightData[bucketLow] += weightLow;
				}
				if (bucketHigh < nBuckets && bucketHigh >= 0) {
					outData[bucketHigh * 2 + 0] += re * weightHigh;
					outData[bucketHigh * 2 + 1] += im * weightHigh;
					// Tool.trace(String.format("Adding to high bucket %d: %f + i%f",
					// bucketHigh, re * weightHigh, im * weightHigh));

					weightData[bucketHigh] += weightHigh;
				}
			}
		}
		// normalize the output vector
		if (normalize) {
			for (int i = 0; i < nBuckets; i++) {
				if (weightData[i] > 0) {
					outData[i * 2 + 0] /= weightData[i];
					outData[i * 2 + 1] /= weightData[i];
				} else {
					outData[i * 2 + 0] = 0;
					outData[i * 2 + 1] = 0;
				}
			}
		}
		out.syncBuffer();
	}

}
