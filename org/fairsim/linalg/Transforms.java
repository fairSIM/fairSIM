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

import java.util.Map;
import java.util.TreeMap;

/**
 * Provides FFTs for vector elements.
 */
public abstract class Transforms {


    /** Two-dimensional FFT of the input vector. */
    static public void fft2d( Vec2d.Cplx in, boolean inverse ) {
	in.fft2d(inverse);
    }
    
    /** Three-dimensional FFT of the input vector. */
    static public void fft3d( Vec3d.Cplx in, boolean inverse ) {
	in.fft3d(inverse);
    }





    static public void runfft( Vec2d.Cplx in, boolean inverse ) {
	// get parameters
	final int w = in.vectorWidth();
	final int h = in.vectorHeight();
	// see if we have an instance already, otherwise make one
	FftProvider.Instance ffti = getOrCreateInstance(new FFTkey(w,h));
	float [] dat = in.vectorData();
	ffti.fftTransform( dat , inverse );
	in.syncBuffer();
    }

    static public void runfft3d( Vec3d.Cplx in, boolean inverse ) {
	// get parameters
	final int w = in.vectorWidth();
	final int h = in.vectorHeight();
	final int d = in.vectorDepth();
	// see if we have an instance already, otherwise make one
	FftProvider.Instance ffti = getOrCreateInstance(new FFTkey(w,h,d));
	float [] dat = in.vectorData();
	ffti.fftTransform( dat , inverse );
	in.syncBuffer();
    }



    /** One-dimensional FFT of the complex input vector. */
    static public void fft1d( Vec.Cplx in, boolean inverse ) {
	final int len = in.vectorSize();
	FftProvider.Instance ffti = getOrCreateInstance( new FFTkey(len));
	float [] dat = in.vectorData();
	ffti.fftTransform( dat, inverse );
	in.syncBuffer();
    }

    /** One-dimensional FFT of a standard float array. This assumes
     *  standard packing ( a[i*2] = real[i], a[i*2+1] =imag[i] ) of
     *  the input array */
    static public void fft1d( float [] in, boolean inverse ) {
	final int len = in.length/2;
	FftProvider.Instance ffti = getOrCreateInstance( new FFTkey(len));
	ffti.fftTransform( in, inverse );
    }

    // -----------------------------------------------------------
    // Instance management


    /** key to store instances */
    private static class FFTkey implements  Comparable<FFTkey> { 
	final int d,x,y,z ; 
	FFTkey( int xi ) {
	    d=1; x=xi; y=-1; z=-1;
	}
	FFTkey( int xi, int yi ) { 
	    d=2; x=xi; y=yi; z=-1;
	}
	FFTkey( int xi, int yi, int zi ) { 
	    d=3; x=xi; y=yi; z=zi;
	}
	@Override
	public int compareTo(FFTkey t) {
	    if (d != t.d) return (t.d -d );
	    if (x != t.x) return (t.x -x );
	    if (y != t.y) return (t.y -y );
	    if (z != t.z) return (t.z -z );
	    return 0;
	}
    }
    
    /** FFT instances */
    static private Map<FFTkey, FftProvider.Instance> instances; 
    
    /** static initialization of the instances list. */
    static {
	if (instances==null) {
	    instances = new TreeMap<FFTkey, FftProvider.Instance>();
	}
    }



    /** returns an instance, creates one if none exists */
    static protected FftProvider.Instance getOrCreateInstance(final FFTkey k) {
	FftProvider.Instance ffti = instances.get(k);
	if (ffti!=null) return ffti;
	//Tool.trace("FFT: creating new instance");
	if (k.d==1)
	    ffti = FftProvider.get1Dfft(k.x);
	if (k.d==2)
	    ffti = FftProvider.get2Dfft(k.x,k.y);
	if (k.d==3)
	    ffti = FftProvider.get3Dfft(k.x,k.y,k.z);
	if (ffti==null) 
	    throw new RuntimeException("Unsupported dimensions");
	instances.put( k , ffti );
	return ffti;
    }


    // ---------------------------------------------------------
    //
    // abtract functions for the actual transformations
    //
    // ---------------------------------------------------------

    /** 
     *  1D Fourier tranformation complexFloat2complexFloat.
     *  This has to be implemented in a subclass. For convenience,
     *  all necessary parameters are passed.
     */
    protected abstract void fft_1d_trans_c2c(  float [] x, boolean inverse );
    /** 
     *  2D Fourier tranformation complexFloat2complexFloat.
     *  This has to be implemented in a subclass. For convenience,
     *  all necessary parameters are passed.
     */
    protected abstract void fft_2d_trans_c2c(  float [] x, boolean inverse );

    /** 
     *  3D Fourier tranformation complexFloat2complexFloat.
     *  This has to be implemented in a subclass. For convenience,
     *  all necessary parameters are passed.
     */
    protected abstract void fft_3d_trans_c2c(  float [] x, boolean inverse );



    /** checks if input is 2^n */
    static public boolean powerOf2(int l) {
	    int i=2;
	    while(i<l) i *= 2;
	    return (i==l);
    }
    
    // ---------------------------------------------------------
    //
    // Power spectrum calculations
    //
    // ---------------------------------------------------------

    /** Computes a power spectrum, assuming the input vector is an FFT spectrum.
     *	Note: The spectrum will be clipped and quadrant-swapped for display purposes. */
    static public void computePowerSpectrum( Vec2d.Cplx inV, Vec2d.Real outV) {
	    computePowerSpectrum( inV, outV, true );
    }
    
    /** Computes a power spectrum, assuming the input vector is an FFT spectrum.
     *  Note: The spectrum is clipped to log(max)-log(min)<30, and can be quadrand-swapped. */
    static public void computePowerSpectrum( Vec2d.Cplx inV, Vec2d.Real outV, 
	boolean swap_quad ) {


	// TODO: maybe speed this up to higher efficience
	final int w= inV.vectorWidth();
	final int h= inV.vectorHeight();
	float [] in  =  inV.vectorData();
	float [] out  = outV.vectorData();

	if (( w != outV.vectorWidth()  ) || ( h != outV.vectorHeight() ))
	    throw new RuntimeException("Vector size mismatch");



	// calculate min and max for scaling
	float min = Float.MAX_VALUE;
	float max = Float.MIN_VALUE;
	for ( float i : in ) {
	    if (i<min) min=i;
	    if (i>max) max=i;
	}
	
	// reduce the range 
	max = (float)Math.log(max);
	min = (float)Math.log(min);
	if (Float.isNaN(min) || max-min>30)
	    min = max-30;

	
	for (int y=0;y<h;y++)
	for (int x=0;x<w;x++) {
	    float r = (float)Math.sqrt(
		Math.pow( in[2*(y*w+x)+0] , 2)+Math.pow( in[2*(y*w+x)+1] , 2));
	    r = (float)(((Math.log(r) - min)/(max-min)));
	    if (Float.isNaN(r) || r<0) r=0f;

	    if (swap_quad) {
		int xo = (x<w/2)?(x+w/2):(x-w/2);
		int yo = (y<h/2)?(y+h/2):(y-h/2);
		out[    yo*w    + xo ] = r;
	    } else {
		out[    y*w    + x ] = r;
	    }
	}
    
	outV.syncBuffer( );

    }

    /** Swap quadrands. */
    static public void swapQuadrant(Vec2d.Cplx in) {
	final int w = in.vectorWidth();
	final int h = in.vectorHeight();
	for (int y=0;y<h/2;y++)
	for (int x=0;x<w/2;x++) {
	    // 1 <-> 3
	    Cplx.Float tmp1 = in.get(x,y);
	    Cplx.Float tmp3 = in.get(x+w/2,y+h/2);
	    in.set(x,y,tmp3);
	    in.set(x+w/2,y+h/2,tmp1);
	    // 2 <-> 3
	    Cplx.Float tmp2 = in.get(x,y+h/2);
	    Cplx.Float tmp4 = in.get(x+w/2,y);
	    in.set(x,y+h/2,tmp4);
	    in.set(x+w/2,y,tmp2);
	}
    }
    
    /** Swap quadrands. */
    static public void swapQuadrant(Vec2d.Real in) {
	final int w = in.vectorWidth();
	final int h = in.vectorHeight();
	for (int y=0;y<h/2;y++)
	for (int x=0;x<w/2;x++) {
	    // 1 <-> 3
	    float tmp1 = in.get(x,y);
	    float tmp3 = in.get(x+w/2,y+h/2);
	    in.set(x,y,tmp3);
	    in.set(x+w/2,y+h/2,tmp1);
	    // 2 <-> 3
	    float tmp2 = in.get(x,y+h/2);
	    float tmp4 = in.get(x+w/2,y);
	    in.set(x,y+h/2,tmp4);
	    in.set(x+w/2,y,tmp2);
	}
    }
    
    /** Swap quadrands. */
    static public void swapQuadrant(Vec3d.Cplx in) {
	final int w = in.vectorWidth();
	final int h = in.vectorHeight();
	final int d = in.vectorHeight();
	for (int z0=0;z0<d/2;z0++)
	for (int y0=0;y0<h/2;y0++)
	for (int x0=0;x0<w/2;x0++) {
	    int x1=x0+w/2, y1=y0+h/2, z1=z0+d/2;
	    Cplx.Float tmpA, tmpB;
	    // 000 <> 111
	    tmpA = in.get(x0,y0,z0);
	    tmpB = in.get(x1,y1,z1);
	    in.set(x0,y0,z0,tmpB);
	    in.set(x1,y1,z1,tmpA);
	    // 010 <> 101
	    tmpA = in.get(x0,y1,z0);
	    tmpB = in.get(x1,y0,z1);
	    in.set(x0,y1,z0,tmpB);
	    in.set(x1,y0,z1,tmpA);
	    // 100 <> 011
	    tmpA = in.get(x1,y0,z0);
	    tmpB = in.get(x0,y1,z1);
	    in.set(x1,y0,z0,tmpA);
	    in.set(x0,y1,z1,tmpB);
	    // 001 <> 110
	    tmpA = in.get(x0,y0,z1);
	    tmpB = in.get(x1,y1,z0);
	    in.set(x0,y0,z1,tmpB);
	    in.set(x1,y1,z0,tmpA);
	}
    }
    
    /** Swap quadrands. */
    static public void swapQuadrant(Vec3d.Real in) {
	final int w = in.vectorWidth();
	final int h = in.vectorHeight();
	final int d = in.vectorHeight();
	for (int z0=0;z0<d/2;z0++)
	for (int y0=0;y0<h/2;y0++)
	for (int x0=0;x0<w/2;x0++) {
	    int x1=x0+w/2, y1=y0+h/2, z1=z0+d/2;
	    float tmpA, tmpB;
	    // 000 <> 111
	    tmpA = in.get(x0,y0,z0);
	    tmpB = in.get(x1,y1,z1);
	    in.set(x0,y0,z0,tmpB);
	    in.set(x1,y1,z1,tmpA);
	    // 010 <> 101
	    tmpA = in.get(x0,y1,z0);
	    tmpB = in.get(x1,y0,z1);
	    in.set(x0,y1,z0,tmpB);
	    in.set(x1,y0,z1,tmpA);
	    // 100 <> 011
	    tmpA = in.get(x1,y0,z0);
	    tmpB = in.get(x0,y1,z1);
	    in.set(x1,y0,z0,tmpA);
	    in.set(x0,y1,z1,tmpB);
	    // 001 <> 110
	    tmpA = in.get(x0,y0,z1);
	    tmpB = in.get(x1,y1,z0);
	    in.set(x0,y0,z1,tmpB);
	    in.set(x1,y1,z0,tmpA);
	}
    }
    
    
    
    /* Return a vector containing phases for a Fourier shift theorems shift to kx,ky.
     *  @param N Width and heigt of vector
     *  @param kx x-coordinate of shift
     *  @param ky y-coordinate of shift
     *  @param fast Use faster, but less precise sin/cos (see {@link MTool#fsin}) */
    /*
    static public Vec2d.Cplx createShiftVector( 
	final int N, final double kx, final double ky, final boolean fast ) {
	Vec2d.Cplx shft = Vec2d.createCplx(N,N);
	final float [] val = shft.vectorData();
	
	// run outer loop in parallel
	new SimpleMT.PFor(0,N) {
	    public void at(int y) {
		for (int x=0; x<N; x++) {
		    float phaVal = (float)(2*Math.PI*(kx*x+ky*y)/N);
		    if (fast) {
			val[ (y*N+x)*2+0 ] = (float)MTool.fcos( phaVal );
			val[ (y*N+x)*2+1 ] = (float)MTool.fsin( phaVal );
		    } else {
			val[ (y*N+x)*2+0 ] = (float)Math.cos( phaVal );
			val[ (y*N+x)*2+1 ] = (float)Math.sin( phaVal );
		    }
		}
	    }
	};

	shft.syncBuffer();

	return shft;
    } */

    /* See {@link #createShiftVector}, with fast='false' */
    /*
    static public Vec2d.Cplx createShiftVector(int N, double kx, double ky ) {
	return createShiftVector(N, kx, ky, false);
    } */

    /** Multiply a vector with Fourier shift theorem phases.
     *  Vector has to be of square size (w==h).
     *  @param kx x-coordinate of shift
     *  @param ky y-coordinate of shift
     *  @param fast Use faster, but less precise sin/cos (see {@link MTool#fsin}) */
    @Deprecated
    static public void timesShiftVector( final Vec2d.Cplx vec,
	final double kx, final double ky, final boolean fast ) {
	vec.fourierShift( kx, ky );
    }


    static public void runTimesShiftVector( final Vec2d.Cplx vec,
	final double kx, final double ky, final boolean fast ) {
	final float [] val = vec.vectorData();
	final int N = Vec2d.checkSquare( vec );
	
	// run outer loop in parallel
	new SimpleMT.PFor(0,N) {
	    public void at(int y) {
		for (int x=0; x<N; x++) {
		    float phaVal = (float)(2*Math.PI*(kx*x+ky*y)/N);
		    float si,co;
		    if (fast) {
			co = (float)MTool.fcos( phaVal );
			si = (float)MTool.fsin( phaVal );
		    } else {
			co = (float)Math.cos( phaVal );
			si = (float)Math.sin( phaVal );
		    }
		    // get
		    float re = val[ (y*N+x)*2+0 ] ;
		    float im = val[ (y*N+x)*2+1 ] ;
		    // set
		    val[ (y*N+x)*2+0 ] = Cplx.multReal( re, im, co, si );
		    val[ (y*N+x)*2+1 ] = Cplx.multImag( re, im, co, si );
		}
	    }
	};

	vec.syncBuffer();
    }

    /** See {@link #timesShiftVector}, with fast='false' */
    static public void timesShiftVector( final Vec2d.Cplx vec, double kx,  double ky ) {
	timesShiftVector( vec, kx, ky, false );
    }



	
}
