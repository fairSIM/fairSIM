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


// use our own, stripped-down version of JTransforms
import org.fairsim.extern.jtransforms.*;
import org.fairsim.utils.Tool;

//import org.jtransforms.fft.*;

class JTransformsConnector extends Transforms {

	FloatFFT_1D fft1ds;
	FloatFFT_2D fft2ds;
	FloatFFT_3D fft3ds;

	/** Create a 1d transform for vectors length n */
	JTransformsConnector( int n ) {
		fft1ds = new  FloatFFT_1D(n);
	}
	
	/** Create a 2d transform for vectors size w x h */
	JTransformsConnector( int w, int h ) {
		fft2ds = new  FloatFFT_2D(w,h);
	}
	
	/** Create a 3d transform for vectors size w x h x d */
	JTransformsConnector( int w, int h, int d) {
		Tool.trace(String.format("Created FFT size %d x %d x %d",w,h,d));
		fft3ds = new  FloatFFT_3D(w,h,d);
	}


	// ==========================================================================	
	
 	/** Computes a 1-dim Fourier Transform  */
	@Override
    	protected void fft_1d_trans_c2c(final float [] x, boolean inverse) {
	    if (!inverse)
	    fft1ds.complexForward( x );
	    else
	    fft1ds.complexInverse( x , true );
    	}


	/** Computes 2 2-dim Fourier Transform by row-then-column ordered
	 *  mapping to 1-dim FFTs. */
	@Override
    	protected void fft_2d_trans_c2c(final float [] x, boolean inverse) {
	    if (!inverse)
	    fft2ds.complexForward( x );
	    else
	    fft2ds.complexInverse( x , true );
	}
	
	/** Computes 3-dim Fourier Transfor */
	@Override
    	protected void fft_3d_trans_c2c(final float [] x, boolean inverse) {
	    if (!inverse)
	    fft3ds.complexForward( x );
	    else
	    fft3ds.complexInverse( x , true );
	}


}
