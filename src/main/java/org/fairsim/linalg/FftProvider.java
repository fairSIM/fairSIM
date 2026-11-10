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

import org.fairsim.utils.Tool;

/**
 * Interface defining all the methods we need an FFT implementation to provide
 */
public abstract class FftProvider {

	public static interface Instance {
		/** run the fft */
		public void fftTransform(float[] v, boolean inverse);
	}

	private static FftProvider defaultFftFactory = null;

	public abstract Instance create1Dfft(int n);

	public abstract Instance create2Dfft(int x, int y);

	public abstract Instance create3Dfft(int x, int y, int z);

	public abstract String getImplementationName();

	// this code pokes classes to add them to the factory system
	static {
		poke("org.fairsim.linalg.JTransformsConnector", "Original JTransforms");
		poke("org.fairsim.linalg.JTransformsForkConnector", "forked/internal JTransforms");

		// see if any of those have succeeded in setting an FFT factory
		if (isFftFactorySet() == false) {
			Tool.error("No FFT implementation available!", true);
		}
	}

	/** instantiate a connector by name; it registers itself if its library is present */
	private static void poke(String cls, String name) {
		try {
			Class.forName(cls).getDeclaredConstructor().newInstance();
		} catch (ClassNotFoundException e) {
			Tool.trace(name + " not available");
		} catch (java.lang.reflect.InvocationTargetException e) {
			if (e.getCause() instanceof ClassNotFoundException)
				Tool.trace(name + " not available");
			else
				Tool.trace(name + " could not be initialized: " + e.getCause());
		} catch (ReflectiveOperationException e) {
			Tool.trace(name + " could not be initialized: " + e);
		}
	}

	public static void setFftFactory(FftProvider ft) {
		defaultFftFactory = ft;
		Tool.trace("FFT implementation set to: " + ft.getImplementationName());
	}

	public static boolean isFftFactorySet() {
		return defaultFftFactory != null;
	}

	final static Instance get1Dfft(int n) {
		if (defaultFftFactory == null) {
			throw new RuntimeException("No FFT implementation available!");
		}
		return defaultFftFactory.create1Dfft(n);
	}

	final static Instance get2Dfft(int x, int y) {
		if (defaultFftFactory == null) {
			throw new RuntimeException("No FFT implementation available!");
		}
		return defaultFftFactory.create2Dfft(x, y);
	}

	final static Instance get3Dfft(int x, int y, int z) {
		if (defaultFftFactory == null) {
			throw new RuntimeException("No FFT implementation available!");
		}
		return defaultFftFactory.create3Dfft(x, y, z);
	}

	public static void main(String[] args) {

		/*
		 * Instance ft1 = get1Dfft( 512 );
		 * Instance ft2 = get2Dfft( 512, 512 );
		 * Instance ft3 = get3Dfft( 512, 512, 128 );
		 */

		Tool.trace("Creating and fft'ing 2D vector");
		Vec2d.Cplx vec2d = Vec2d.createCplx(512, 512);
		vec2d.fft2d(true);
		vec2d.fft2d(false);

		Tool.trace("Creating and fft'ing 3D vector");
		Vec3d.Cplx vec3d = Vec3d.createCplx(128, 128, 16);
		vec3d.fft3d(true);
		vec3d.fft3d(false);

		Tool.trace("done");
		System.exit(0);
	}

}
