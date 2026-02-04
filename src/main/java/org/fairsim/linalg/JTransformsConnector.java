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

import org.jtransforms.fft.*; 

class JTransformsConnector extends FftProvider {

	/** Add us a as an FFT implementation */
	JTransformsConnector() throws ClassNotFoundException {
	    
	    // check if our required dependencies exist
	    try {
		Class.forName("org.jtransforms.fft.FloatFFT_2D");
	    } catch ( ClassNotFoundException e ) {
		Tool.trace("Implementing classes for 'Original JTransforms' not found");
		throw e;
	    }
	    
	    try {
		Class.forName("pl.edu.icm.jlargearrays.LargeArray");
	    } catch ( ClassNotFoundException e ) {
		Tool.trace("Implementing classes for 'JLargeArray' not found");
		throw e;
	    }
	    
	    try {
		Class.forName("org.apache.commons.math3.util.FastMath");
	    } catch ( ClassNotFoundException e ) {
		Tool.trace("Implementing classes for 'Apache fast math 3' not found");
		throw e;
	    }
		    
		    
	    FftProvider.setFftFactory( this );
	}

	/** return a 1D instance of JTransforms */
	public FftProvider.Instance create1Dfft(final int n) {
	    
	    FftProvider.Instance  ret  = new FftProvider.Instance() {

		FloatFFT_1D fft1ds = new FloatFFT_1D(n);
		
		public void fftTransform( float [] v, boolean inverse ) {
		    if (!inverse) {
			fft1ds.complexForward( v );
		    } else {
			fft1ds.complexInverse( v , true );
		    }
		}
	    };
	
	    return ret;
	}
	

	/** return a 1D instance of JTransforms */
	public FftProvider.Instance create2Dfft(final int x, final int y) {
	    
	    FftProvider.Instance  ret  = new FftProvider.Instance() {

		FloatFFT_2D fft2ds = new FloatFFT_2D(y,x);
		
		public void fftTransform( float [] v, boolean inverse ) {
		    if (!inverse) {
			fft2ds.complexForward( v );
		    } else {
			fft2ds.complexInverse( v , true );
		    }
		}
	    };
	
	    return ret;
	}
	
	/** return a 1D instance of JTransforms */
	public FftProvider.Instance create3Dfft(final int x, final int y, final int z) {
	    
	    FftProvider.Instance  ret  = new FftProvider.Instance() {

		FloatFFT_3D fft3ds = new FloatFFT_3D(z,y,x);
		
		public void fftTransform( float [] v, boolean inverse ) {
		    if (!inverse) {
			fft3ds.complexForward( v );
		    } else {
			fft3ds.complexInverse( v , true );
		    }
		}
	    };
	
	    return ret;
	}

	public String getImplementationName() {
	    return "Original JTransforms connector";
	}


}
