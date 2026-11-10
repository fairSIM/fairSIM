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

/**
 * Basic, pure java implementation of the Vector interfaces
 * ({@link Vec}, {@link Vec2d})
 */
class BasicVector implements VectorFactory {

	static BasicVector instance = null;

	private BasicVector() {
	};

	/** Get the factory (singleton) for BasicVectors */
	/** exp(2 pi i k j/n) for j = 0..n-1, interleaved re/im, computed in double */
	static double[] shiftPhases(final double k, final int n) {
		double[] ret = new double[2 * n];
		for (int j = 0; j < n; j++) {
			final double a = 2 * Math.PI * ((k * j / n) % 1.0);
			ret[2 * j] = Math.cos(a);
			ret[2 * j + 1] = Math.sin(a);
		}
		return ret;
	}

	public static BasicVector getFactory() {
		if (instance == null)
			instance = new BasicVector();
		return instance;
	}

	// ------ Vector factory ------

	@Override
	public BReal createReal(int n) {
		return new BReal(n);
	}

	@Override
	public BCplx createCplx(int n) {
		return new BCplx(n);
	}

	@Override
	public BReal2D createReal2D(int w, int h) {
		return new BReal2D(w, h);
	}

	@Override
	public BCplx2D createCplx2D(int w, int h) {
		return new BCplx2D(w, h);
	}

	@Override
	public BReal3D createReal3D(int w, int h, int d) {
		return new BReal3D(w, h, d);
	}

	@Override
	public BCplx3D createCplx3D(int w, int h, int d) {
		return new BCplx3D(w, h, d);
	}

	@Override
	public void syncConcurrent() {
	};

	/** Minimal / basic vector implementation */
	class BReal extends AbstractVectorReal {

		BReal(int l) {
			super(l);
		}

		@Override
		public BReal duplicate() {
			BReal ret = new BReal(elemCount);
			ret.copy(this);
			return ret;
		}

		@Override
		public void syncBuffer() {
		};

		@Override
		public void readyBuffer() {
		};

		@Override
		public void makeCoherent() {
		};

	}

	/** Minimal / basic vector implementation */
	class BCplx extends AbstractVectorCplx {

		BCplx(int l) {
			super(l);
		}

		@Override
		public BCplx duplicate() {
			BCplx ret = new BCplx(elemCount);
			ret.copy(this);
			return ret;
		}

		@Override
		public BReal createAsReal(int n) {
			return new BReal(n);
		}

		@Override
		public void syncBuffer() {
		};

		@Override
		public void readyBuffer() {
		};

		@Override
		public void makeCoherent() {
		};

	}

	/** Minimal 2d vector implementation */
	class BReal2D extends BReal implements Vec2d.Real {
		final int width, height;

		BReal2D(int w, int h) {
			super(w * h);
			width = w;
			height = h;
		}

		@Override
		public int vectorWidth() {
			return width;
		}

		@Override
		public int vectorHeight() {
			return height;
		}

		@Override
		public BReal2D duplicate() {
			BReal2D ret = new BReal2D(width, height);
			ret.copy(this);
			return ret;
		}

		@Override
		public void paste(Vec2d.Real in, int x, int y, boolean zero) {
			Vec2d.paste(in, this, 0, 0, in.vectorWidth(), in.vectorHeight(), x, y, zero);
		}

		@Override
		public float get(int x, int y) {
			return data[x + y * width];
		}

		@Override
		public void set(int x, int y, float a) {
			data[x + y * width] = a;
		}

		@Override
		public void project(Vec3d.Real inV, int start, int end) {

			final float[] out = this.vectorData();
			final float[] in = inV.vectorData();

			if (inV.vectorWidth() != width || inV.vectorHeight() != height)
				throw new RuntimeException("Wrong vector size when projecting");

			if (start < 0 || end >= inV.vectorDepth() || start > end)
				throw new RuntimeException("z-index out of vector depth bounds");

			// TODO: This could be more efficient in the loop??
			this.zero();

			for (int z = start; z <= end; z++)
				for (int y = 0; y < height; y++)
					for (int x = 0; x < width; x++) {
						out[y * width + x] += in[z * width * height + y * width + x];
					}

		}

		@Override
		public void project(Vec3d.Cplx inV, int start, int end) {

			final float[] out = this.vectorData();
			final float[] in = inV.vectorData();

			if (inV.vectorWidth() != width || inV.vectorHeight() != height)
				throw new RuntimeException("Wrong vector size when projecting");

			if (start < 0 || end >= inV.vectorDepth() || start > end)
				throw new RuntimeException("z-index out of vector depth bounds");

			// TODO: This could be more efficient in the loop??
			this.zero();

			for (int z = start; z <= end; z++)
				for (int y = 0; y < height; y++)
					for (int x = 0; x < width; x++) {
						out[y * width + x] += in[2 * (z * width * height + y * width + x)];
					}

		}

		@Override
		public void project(Vec3d.Real inV) {
			project(inV, 0, inV.vectorDepth() - 1);
		}

		@Override
		public void project(Vec3d.Cplx inV) {
			project(inV, 0, inV.vectorDepth() - 1);
		}

		@Override
		public void slice(Vec3d.Real inV, int n) {
			project(inV, n, n);
		}

		@Override
		public void slice(Vec3d.Cplx inV, int n) {
			project(inV, n, n);
		}

		@Override
		public void setLine(int l, Vec.Real in) {
			if (in.vectorSize() != this.vectorWidth())
				throw new RuntimeException("Incompatible vector width");
			if (l < 0 || l >= height)
				throw new RuntimeException("Line index out of bounds");

			float[] inputData = in.vectorData();
			for (int x = 0; x < width; x++)
				data[x + l * width] = inputData[x];
		}

		@Override
		public void setFrom16bitPixels(short[] in) {
			if (width * height != in.length)
				throw new RuntimeException("Short array to vector size mismatch");
			final float[] out = this.vectorData();
			for (int y = 0; y < height; y++)
				for (int x = 0; x < width; x++)
					out[x + y * width] = in[x + y * width] & 0xFFFF;
		}

	}

	/** Minimal 2d vector implementation */
	class BCplx2D extends BCplx implements Vec2d.Cplx {
		final int width, height;

		BCplx2D(int w, int h) {
			super(w * h);
			width = w;
			height = h;
		}

		@Override
		public BCplx2D duplicate() {
			BCplx2D ret = new BCplx2D(width, height);
			ret.copy(this);
			return ret;
		}

		@Override
		public int vectorWidth() {
			return width;
		}

		@Override
		public int vectorHeight() {
			return height;
		}

		@Override
		public Cplx.Float get(int x, int y) {
			return Cplx.Float.at(data, x + y * width);
		}

		@Override
		public void set(int x, int y, Cplx.Float a) {
			data[(x + y * width) * 2 + 0] = a.re;
			data[(x + y * width) * 2 + 1] = a.im;
		}

		@Override
		public void set(int x, int y, Cplx.Double a) {
			data[(x + y * width) * 2 + 0] = (float) a.re;
			data[(x + y * width) * 2 + 1] = (float) a.im;
		}

		@Override
		public void paste(Vec2d.Cplx in, int x, int y, boolean zero) {
			Vec2d.paste(in, this, 0, 0, in.vectorWidth(), in.vectorHeight(), x, y, zero);
		}

		@Override
		public void pasteFreq(Vec2d.Cplx inV) {
			pasteFreq(inV, 0, 0);
		}

		@Override
		public void pasteFreq(Vec2d.Cplx inV, int xOffset, int yOffset) {

			final int wi = inV.vectorWidth();
			final int hi = inV.vectorHeight();
			final int wo = this.vectorWidth();
			final int ho = this.vectorHeight();

			// check some inputs
			if (wo < wi || ho < hi)
				throw new IllegalArgumentException("pasteFreq: output " + wo + "x" + ho
						+ " smaller than input " + wi + "x" + hi);
			if (inV == this)
				inV = inV.duplicate();

			final float[] out = this.vectorData();
			final float[] in = inV.vectorData();

			this.zero();

			// keep each signed frequency, add offset, wrap into the output
			for (int y = 0; y < hi; y++)
				for (int x = 0; x < wi; x++) {
					int xo = (x < (wi + 1) / 2) ? (x) : (x - wi);
					int yo = (y < (hi + 1) / 2) ? (y) : (y - hi);
					xo = ((xo + xOffset) % wo + wo) % wo;
					yo = ((yo + yOffset) % ho + ho) % ho;
					out[(xo + (wo * yo)) * 2 + 0] = in[(x + wi * y) * 2 + 0];
					out[(xo + (wo * yo)) * 2 + 1] = in[(x + wi * y) * 2 + 1];
				}
			this.syncBuffer();
		}

		@Override
		public void setFrom16bitPixels(short[] in) {
			if (width * height != in.length) {
				RuntimeException ex = new RuntimeException(
						"Short array to vector size mismatch " + width + "/" + height + "/" + in.length);
				Tool.error(ex.toString(), true);
				throw ex;
			}
			final float[] out = this.vectorData();
			for (int y = 0; y < height; y++)
				for (int x = 0; x < width; x++) {
					out[2 * (x + y * width) + 0] = (in[x + y * width] & 0xFFFF);
					out[2 * (x + y * width) + 1] = 0;
				}
		}

		@Override
		public void fft2d(boolean inverse) {
			Transforms.runfft(this, inverse);
		}

		@Override
		public void fourierShift(final double kx, final double ky) {

			final float[] val = vectorData();
			final int w = vectorWidth(), h = vectorHeight();
			final double[] phx = shiftPhases(kx, w), phy = shiftPhases(ky, h);

			// run outer loop in parallel
			new SimpleMT.PFor(0, h) {
				public void at(int y) {
					for (int x = 0; x < w; x++) {
						final double co = phx[2 * x] * phy[2 * y] - phx[2 * x + 1] * phy[2 * y + 1];
						final double si = phx[2 * x] * phy[2 * y + 1] + phx[2 * x + 1] * phy[2 * y];
						final int i = (y * w + x) * 2;
						final double re = val[i], im = val[i + 1];
						val[i] = (float) (re * co - im * si);
						val[i + 1] = (float) (re * si + im * co);
					}
				}
			};
			syncBuffer();
		}

		@Override
		public void project(Vec3d.Cplx inV, int start, int end) {

			final float[] out = this.vectorData();
			final float[] in = inV.vectorData();

			if (inV.vectorWidth() != width || inV.vectorHeight() != height)
				throw new RuntimeException("Wrong vector size when projecting");

			if (start < 0 || end >= inV.vectorDepth() || start > end)
				throw new RuntimeException("z-index out of vector depth bounds");

			// TODO: This could be more efficient in the loop??
			this.zero();

			for (int z = start; z <= end; z++)
				for (int y = 0; y < height; y++)
					for (int x = 0; x < width; x++) {
						out[(y * width + x) * 2 + 0] += in[(z * width * height + y * width + x) * 2 + 0];
						out[(y * width + x) * 2 + 1] += in[(z * width * height + y * width + x) * 2 + 1];
					}

		}

		@Override
		public void project(Vec3d.Cplx inV) {
			project(inV, 0, inV.vectorDepth() - 1);
		}

		@Override
		public void slice(Vec3d.Cplx inV, int n) {
			project(inV, n, n);
		}

		@Override
		public void setLine(int l, Vec.Cplx in) {
			if (in.vectorSize() != width)
				throw new RuntimeException("Incompatible vector width");
			if (l < 0 || l >= height)
				throw new RuntimeException("Line index out of bounds");
			float[] inputData = in.vectorData();
			for (int x = 0; x < width; x++) {
				data[2 * (x + l * width) + 0] = inputData[2 * x + 0];
				data[2 * (x + l * width) + 1] = inputData[2 * x + 1];
			}
		}

		@Override
		public void setLine(int l, Vec.Real in) {
			if (in.vectorSize() != width)
				throw new RuntimeException("Incompatible vector width");
			if (l < 0 || l >= height)
				throw new RuntimeException("Line index out of bounds");
			float[] inputData = in.vectorData();
			for (int x = 0; x < width; x++) {
				data[2 * (x + l * width) + 0] = inputData[x];
				data[2 * (x + l * width) + 1] = 0f;
			}
		}

	}

	/** Minimal 2d vector implementation */
	class BReal3D extends BReal implements Vec3d.Real {
		final int width, height, depth;

		BReal3D(int w, int h, int d) {
			super(w * h * d);
			width = w;
			height = h;
			depth = d;
		}

		@Override
		public int vectorWidth() {
			return width;
		}

		@Override
		public int vectorHeight() {
			return height;
		}

		@Override
		public int vectorDepth() {
			return depth;
		}

		@Override
		public BReal3D duplicate() {
			BReal3D ret = new BReal3D(width, height, depth);
			ret.copy(this);
			return ret;
		}

		@Override
		public float get(int x, int y, int z) {
			return data[x + y * width + z * width * height];
		}

		@Override
		public void set(int x, int y, int z, float a) {
			data[x + y * width + z * width * height] = a;
		}

		@Override
		public void setSlice(int z, Vec2d.Real vec) {

			if (z < 0 || z > depth || vec.vectorWidth() != width || vec.vectorHeight() != height)
				throw new RuntimeException("Vector dimension mismatch");

			System.arraycopy(vec.vectorData(), 0, data, z * width * height, width * height);
		}

	}

	/** Minimal 2d vector implementation */
	class BCplx3D extends BCplx implements Vec3d.Cplx {
		final int width, height, depth;

		BCplx3D(int w, int h, int d) {
			super(w * h * d);
			width = w;
			height = h;
			depth = d;
		}

		@Override
		public BCplx3D duplicate() {
			BCplx3D ret = new BCplx3D(width, height, depth);
			ret.copy(this);
			return ret;
		}

		@Override
		public int vectorWidth() {
			return width;
		}

		@Override
		public int vectorHeight() {
			return height;
		}

		@Override
		public int vectorDepth() {
			return depth;
		}

		@Override
		public Cplx.Float get(int x, int y, int z) {
			return Cplx.Float.at(data, x + y * width + z * width * height);
		}

		@Override
		public void set(int x, int y, int z, Cplx.Float a) {
			data[(x + y * width + z * width * height) * 2 + 0] = a.re;
			data[(x + y * width + z * width * height) * 2 + 1] = a.im;
		}

		@Override
		public void set(int x, int y, int z, Cplx.Double a) {
			data[(x + y * width + z * width * height) * 2 + 0] = (float) a.re;
			data[(x + y * width + z * width * height) * 2 + 1] = (float) a.im;
		}

		@Override
		public void fft3d(boolean inverse) {
			Transforms.runfft3d(this, inverse);
		}

		@Override
		public void setSlice(final int z, Vec2d.Cplx vec) {
			if ((vec.vectorWidth() != width) ||
					(vec.vectorHeight() != height) ||
					z < 0 || z >= depth)
				throw new RuntimeException("Index mismatch");

			float[] in = vec.vectorData();

			for (int y = 0; y < height; y++)
				for (int x = 0; x < width; x++) {
					data[2 * (x + width * y + z * width * height) + 0] = in[2 * (x + width * y) + 0];
					data[2 * (x + width * y + z * width * height) + 1] = in[2 * (x + width * y) + 1];
				}

		}

		@Override
		public void setSlice(int z, Vec2d.Real vec) {
			if ((vec.vectorWidth() != width) ||
					(vec.vectorHeight() != height) ||
					z < 0 || z >= depth)
				throw new RuntimeException("Index mismatch");

			float[] in = vec.vectorData();

			for (int y = 0; y < height; y++)
				for (int x = 0; x < width; x++) {
					data[2 * (x + width * y + z * width * height) + 0] = in[x + width * y];
					data[2 * (x + width * y + z * width * height) + 1] = 0;
				}
		}

		/** Look into source code for usage */
		@Override
		public void pasteFreq(Vec3d.Cplx inV) {

			final int wi = inV.vectorWidth();
			final int hi = inV.vectorHeight();
			final int ti = inV.vectorDepth();
			final int wo = this.vectorWidth();
			final int ho = this.vectorHeight();
			final int to = this.vectorDepth();

			// check some inputs
			if (wo < wi || ho < hi || to < ti)
				throw new IllegalArgumentException("pasteFreq: output " + wo + "x" + ho + "x" + to
						+ " smaller than input " + wi + "x" + hi + "x" + ti);
			if (inV == this)
				inV = inV.duplicate();

			final float[] out = this.vectorData();
			final float[] in = inV.vectorData();

			this.zero();

			// keep each signed frequency, wrap into the output
			for (int z = 0; z < ti; z++)
				for (int y = 0; y < hi; y++)
					for (int x = 0; x < wi; x++) {
						int xo = (x < (wi + 1) / 2) ? (x) : (x - wi + wo);
						int yo = (y < (hi + 1) / 2) ? (y) : (y - hi + ho);
						int zo = (z < (ti + 1) / 2) ? (z) : (z - ti + to);
						out[(xo + wo * yo + wo * ho * zo) * 2 + 0] = in[(x + wi * y + wi * hi * z) * 2 + 0];
						out[(xo + wo * yo + wo * ho * zo) * 2 + 1] = in[(x + wi * y + wi * hi * z) * 2 + 1];
					}
			this.syncBuffer();
		}

		@Override
		public void fourierShift(
				final double kx, final double ky, final double kz) {

			final float[] val = vectorData();
			final int w = vectorWidth();
			final int h = vectorHeight();
			final int d = vectorDepth();
			final double[] phx = shiftPhases(kx, w), phy = shiftPhases(ky, h), phz = shiftPhases(kz, d);

			// run outer loop in parallel
			new SimpleMT.PFor(0, d) {
				public void at(int z) {
					for (int y = 0; y < h; y++) {
						// y and z factor
						final double cyz = phy[2 * y] * phz[2 * z] - phy[2 * y + 1] * phz[2 * z + 1];
						final double syz = phy[2 * y] * phz[2 * z + 1] + phy[2 * y + 1] * phz[2 * z];
						for (int x = 0; x < w; x++) {
							final double co = phx[2 * x] * cyz - phx[2 * x + 1] * syz;
							final double si = phx[2 * x] * syz + phx[2 * x + 1] * cyz;
							final int i = (z * w * h + y * w + x) * 2;
							final double re = val[i], im = val[i + 1];
							val[i] = (float) (re * co - im * si);
							val[i + 1] = (float) (re * si + im * co);
						}
					}
				}
			};
			syncBuffer();
		}

	}

}
