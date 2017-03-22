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

import org.fairsim.linalg.Vec;
import org.fairsim.linalg.Vec2d;
import org.fairsim.utils.Tool;

/** Implementation of 2D Richardson-Lucy deconvolution steps */
public class RLDeconvolution {

    /** Run Richardson-Lucy deconvolution steps on img. 
     * @param img   Input image, will be modified and contain result
     * @param otf   The optical transfer function to use
     * @param steps Maximum number of iteration steps
    */
    public static void deconvolve( Vec2d.Cplx img, Vec2d.Cplx otf, int steps) {
	deconvolve( img, otf, steps, false);
    }

    /** Run Richardson-Lucy deconvolution steps on img. 
     * @param img   Input image, will be modified and contain result
     * @param otf   The optical transfer function to use
     * @param steps Maximum number of iteration steps
     * @param inputIsInFreqSpace If input is already in freq. space, will set output to same space
     * */
    public static void deconvolve( Vec2d.Cplx img, Vec2d.Cplx otf, int steps, 
	final boolean inputIsInFreqSpace ) {


	// Richardson-Lucy: iterate each step j:
	// (https://en.wikipedia.org/wiki/Richardson%E2%80%93Lucy_deconvolution)
	// 
	// u_{j+1} = u_j x [ ( d /  ( u_j * psf ))  * psf' ]
	// 
	// where '*' : convolution, 'x' point-wise product
	// u_j: est. deconvolved image, d: observed image, 
	// psf: point-spread function, psf' : flipped point-spread function
	// stating guess u_0 = d;

	Vec2d.Cplx deconvImg	= Vec2d.createCplx( img );
	Vec2d.Cplx nextImg	= Vec2d.createCplx( img );
	Vec2d.Cplx errEst	= Vec2d.createCplx( img );
	
	if (inputIsInFreqSpace)
	    img.fft2d(true);

	deconvImg.copy( img );	       // starting guess = observed image
	nextImg.copy( deconvImg );     // last u_j = starting guess

	for (int i=0; i<steps; i++) {

	    // 1: compute u_j * psf
	    nextImg.fft2d(false);
	    nextImg.times( otf );
	    nextImg.fft2d(true);

	    // 2: compute d / [1] = d / ( u_j * psf )
	    nextImg.reciproc();
	    nextImg.times( img );

	    // 3: compute [2]*psf = (d / ( u_j * psf )) * psf'
	    nextImg.fft2d(false);
	    nextImg.times( otf );
	    nextImg.fft2d(true);

	    // 4: compute u_j * [3] 
	    nextImg.times( deconvImg );

	    // 5: now nextImg contains the next guess (u_{j+1})
	    // 5a: compute how much the iteration changed u_j -> u_{j+1}
	    errEst.copy( nextImg );
	    errEst.scal( -1 );
	    errEst.add( deconvImg );
	    Tool.trace("RL-iteration "+i+": "+Math.sqrt(errEst.norm2())/errEst.vectorSize());

	    // 5b: set the new image as the current deconv. image
	    deconvImg.copy( nextImg );

	}

	// copy back the result
	img.copy( deconvImg );
	if (inputIsInFreqSpace)
	    img.fft2d(false);

    }




}


