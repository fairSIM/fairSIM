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

import org.fairsim.utils.Tool;
import org.fairsim.linalg.*;

/** Helper functions (mostly static)
 *  around the SIM algorithm / reconstruction */
public class SimUtils {


    /** Places a 2d freq space vector into a vector of double size.
     *  @param inV Input vector
     *  @param outV Output vector, of doubled size
     *  */
    static public void placeFreq( Vec2d.Cplx inV, Vec2d.Cplx outV ) {
	outV.pasteFreq(inV);
    }

    /** Moves freq space data to kx, ky with subpixel precision, by phase
     *  multiplication in real space. Basically, just ifft, multiply phases, fft...
     *  @param inV Vector to move
     *  @param kx x-coord to move to
     *  @param ky y-coord to move to
     *  */
    static public void fourierShift( Vec2d.Cplx inV,
	final double kx, final double ky ) {

	// tranform to real space
	Transforms.fft2d( inV , true );
	Transforms.timesShiftVector( inV, kx, -ky );
	Transforms.fft2d( inV , false );
    }

    /** Moves freq space data to kx, ky with subpixel precision, by phase
     *  multiplication in real space. 
     *  In standard mode, input vector is copied into output, then moved
     *  by Fourier shift on the output vector.
     *  In fast mode, the subpixel move is carried out on the input vector,
     *  result than is copied to the correct (multiple integer) position in
     *  output vector.
     *
     *  @param inV Input vector, w x h (vector will be changed in fast mode!)
     *  @param outV Output vector, 2*w x 2*h
     *  @param kx x-coord to move to
     *  @param ky y-coord to move to
     *  */
    static public void pasteAndFourierShift( Vec2d.Cplx inV, Vec2d.Cplx outV,
	final double kx, final double ky, boolean doFastShift ) {

	if (!doFastShift) {
	    // standard, slow shift (two FFTs on 2w,2h )
	    outV.pasteFreq( inV, 0, 0);
	    outV.fft2d( true );
	    outV.fourierShift( kx, -ky );
	    outV.fft2d( false );
	} else {

	    // do the subpixel part on input
	    double xFrac = kx - Math.floor(kx);
	    double yFrac = -ky - Math.floor(-ky);
	    inV.fft2d( true );
	    inV.fourierShift( xFrac, yFrac);
	    inV.fft2d( false );
	    
	    // do the integer shift in paste
	    int xInt = (int)Math.floor(  kx );
	    int yInt = (int)Math.floor( -ky );
	    outV.pasteFreq( inV, xInt, yInt );

	    Tool.trace(String.format(" %7.4f %7.4f %7.4f %7.4f" ,
		xFrac, yFrac, (double)xInt, (double)yInt));
	}
    }


    /** Return the power spectrum (as a new vector) of 'in' */
    public static Vec2d.Real pwSpec(Vec2d.Cplx in , boolean swap) {
	Vec2d.Real pw = Vec2d.createReal(in);
	Transforms.computePowerSpectrum( in, pw, swap);
	return pw;
    }
    
    /** Return the power spectrum (as a new vector) of 'in' */
    public static Vec2d.Real pwSpec(Vec3d.Cplx in,  boolean swap) {
	Vec2d.Cplx in2d = Vec2d.createCplx( in.vectorWidth(), in.vectorHeight());
	in2d.project( in );
	Vec2d.Real pw   = Vec2d.createReal(in2d);
	Transforms.computePowerSpectrum( in2d, pw, swap);
	return pw;
    }


    /** Return the power spectrum (as a new vector) of 'in' */
    public static Vec2d.Real pwSpec(Vec2d.Cplx in ) {
	return pwSpec(in, true );
    }
    
    /** Return the power spectrum (as a new vector) of 'in' */
    public static Vec2d.Real pwSpec(Vec3d.Cplx in ) {
	return pwSpec(in, true );
    }


    
    /** Create an image by FFTin back 'in' to spatial */
    public static Vec2d.Real spatial(Vec2d.Cplx in ) {
	Vec2d.Real pw = Vec2d.createReal(in);
	Vec2d.Cplx tmp = in.duplicate();
	Transforms.fft2d( tmp, true );
	pw.copy( tmp );
	return pw;
    }
    
    /** Create an image by FFTin back 'in' to spatial */
    public static Vec2d.Real spatial(Vec3d.Cplx in ) {
	Vec2d.Real pw  = Vec2d.createReal(in.vectorWidth(), in.vectorHeight());
	Vec2d.Cplx tmp = Vec2d.createCplx( pw );
	tmp.project( in );
	Transforms.fft2d( tmp, true );
	pw.copy( tmp );
	return pw;
    }
    
    
    
    /** Create an image by FFTin back 'in' to spatial,
     *  clipping and scaling it afterwards */
    static Vec2d.Real spatial(Vec2d.Cplx in, SimParam.CLIPSCALE clipScale ) {
	Vec2d.Real pw = spatial(in);
	if (clipScale==SimParam.CLIPSCALE.CLIP)
	    clipAndScale( pw, true, false ); 
	if (clipScale==SimParam.CLIPSCALE.BOTH)
	    clipAndScale( pw, true, true ); 
	return pw;
    }


    /** Fades borders (sizes px) of the input to zero.
     *  Done by multiplying sin^2(x) mapped [0..px] to [pi/2..0].
     *  Good step before zero-padding data for high-res FFT. 
     *	@param img Vector to fade borders
     *	@param px  Size of faded region
     *  */
    public static void fadeBorderCos( Vec2d.Real img, int px ) {
	int w=img.vectorWidth(), h=img.vectorHeight();
	float [] dat = img.vectorData();
	final double fac = 1./px * Math.PI/2.;

	// top
	for (int y=0; y<px; y++)
	    for (int x=0; x<w ; x++)
		dat[x + y * w] *= Math.pow( Math.sin( y * fac ) , 2 );
	// bottom
	for (int y=h-px; y<h; y++)
	    for (int x=0; x<w ; x++)
		dat[x + y * w] *= Math.pow( Math.sin( (h-y-1) * fac ) , 2 );
	// left
	for (int y=0; y<h; y++)
	    for (int x=0; x<px ; x++)
		dat[x + y * w] *= Math.pow( Math.sin( x * fac ) , 2 );
	// right
	for (int y=0; y<h; y++)
	    for (int x=w-px; x<w ; x++)
		dat[x + y * w] *= Math.pow( Math.sin( (w-x-1) * fac ) , 2 );
	
	img.syncBuffer();
    }


    /** Multiplies / overlays a region in the input image 'cntrl' with
     *  a sin pattern defined by kx, ky, pha. This function
     *  can be used to visualize the fitted pattern. 
     *	@param cntrl Vector to output to / modify
     *	@param kx x-coordinate of shift vector
     *	@param ky y-coordinate of shift vector
     *	@param pha Phase of pattern
     *  */
    public static void visualizePattern( Vec2d.Real cntrl, 
	    final double kx, final double ky, final double pha ) {
	    
	    if (cntrl.vectorWidth()!=cntrl.vectorHeight()) throw new
		RuntimeException("Please use a square image, w==h");
	    final int len = cntrl.vectorWidth();
	    float [] dat = cntrl.vectorData();

	    for (int y=len/2-50;y<len/2+150; y++)
	    for (int x=len/2-50;x<len/2+150; x++) {
		double v = Math.cos( 2*Math.PI* (-kx*x + ky*y)/(len) + pha );
		dat[ x + y*len ] = dat[ x + y*len]*(float)(v+2)*0.6f ;
	    }

	    cntrl.syncBuffer();
    }


    /** Subtracts a constant value (background) from each
     *  pixel.
     *  @param vec Image to work on
     *  @param bgr Value to subtract
     *  @return Percentage of clipped (lower-than-zero) values */
    public static double subtractBackground( Vec.Real vec,
	final double bgr ) {
	    
	int zCount=0;
	float [] dat = vec.vectorData();

	for (int i=0; i<dat.length; i++) {
	    dat[i]-=(float)bgr;
	    if (dat[i] <0) {
		dat[i]=0;
		zCount++;
	    }
	}
	
	vec.syncBuffer();

	return ((double)zCount) / dat.length;

    }


    /** Zero-clippes and scales an image.
     *  @param vec Image to work on
     *  @param clip Set values lower than 0 to 0
     *  @param scale Scale 0...max to 0..255 */
    public static void clipAndScale( Vec.Real vec, boolean clip, boolean scale ) {
	
	float [] dat = vec.vectorData();
	float max = 0;
	
	if ( scale ) {
	    // find maximum
	    for (int i=0; i<dat.length; i++) 
		if (dat[i]>max) max = dat[i];
	    // scale
	    for (int i=0; i<dat.length; i++) 
		dat[i] *= 255.f/max;
	}	

	if ( clip ) {
	    for (int i=0; i<dat.length; i++) 
		if (dat[i]<0) dat[i]=0;
	}

	vec.syncBuffer();
    }



}
