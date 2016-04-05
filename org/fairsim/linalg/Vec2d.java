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

/** Methods and interfaces for 2-dimensional extension of Vec. Data in
 *  the vector is supposed to be in standard C ordering. */
public final class Vec2d {

    /** Vec2d is static only */
    private Vec2d() { 
	throw new AssertionError();	
    };
    
    /** Return a 2-dimensional vector sized w x h, real-valued */
    public static Real createReal( int w, int h) {
	return Vec.vf.createReal2D(w,h);
    }
 
    /** Return a 2-dimensional vector sized w x h, complex-valued  */
    public static Cplx createCplx( int w, int h) {
	return Vec.vf.createCplx2D(w,h);
    }
    
    /** Return a 2-dimensional vector, width and height of 'size'
     *  scaled by 'scale' */
    public static Real createReal( Size size, int scale ) {
	return createReal( scale*size.vectorWidth(), scale*size.vectorHeight() );
    }

    /** Return a 2-dimensional complex-valued vector, width and height of 'size'
     *  scaled by 'scale' */
    public static Cplx createCplx( Size size, int scale ) {
	return createCplx( scale*size.vectorWidth(), scale*size.vectorHeight() );
    }
    
    /** Return a 2-dimensional vector, width and height of 'size' */
    public static Real createReal( Size size ) {
	return createReal(size, 1);
    }

    /** Return a 2-dimensional complex-valued vector, width and height of 'size' */
    public static Cplx createCplx( Size size ) {
	return createCplx(size, 1);
    }
 
 
    /** Return a n-element array of 2-dimensional vector,
     *  sized w x h with real-valued elements */
    public static Real [] createArrayReal(int n, int w, int h) {
	Real [] ret = new Real[n];
	for (int i=0; i<n; i++)
	    ret[i] = createReal(w,h);
	return ret;
    }
    
    /** Return a n-element array of 2-dimensional vector,
     *  sized w x h with complex-valued elements */
    public static Cplx [] createArrayCplx(int n, int w, int h) {
	Cplx [] ret = new Cplx[n];
	for (int i=0; i<n; i++)
	    ret[i] = createCplx(w,h);
	return ret;
    }

    /** Throws a runtime exception if not all vectors are 
     *	not of same width and height. */
    public static void failSize( Size ... v ) {
	if (v.length<2) return;
	int w = v[0].vectorWidth(), h=v[0].vectorHeight();
	for (int i=1;i<v.length;i++)
	    if ((v[i].vectorWidth() != w) || (v[i].vectorHeight() != h))
		throw new RuntimeException("Vector len mismatch"
		    +i+" "+w+" "+h+" "+v[i].vectorWidth()+" "+v[i].vectorHeight());
    }
    
    /** Throws a runtime exception if not all vectors are 
     *  of the same, square size (N = width = height). Returns the size N.*/
    public static int checkSquare( Size ... v ) {
	if (v[0].vectorWidth()!=v[0].vectorHeight())
	    throw new RuntimeException("Vector len mismatch "
	        +v[0].vectorWidth()+" "+v[0].vectorHeight());
	failSize(v);
	return v[0].vectorWidth();
    }

    /** Object with width and height */
    public interface Size {
	/** Width */
	public int vectorWidth();
	/** Height */
	public int vectorHeight();
    }

    // =======================================================
    
    /**  Real-valued, 2-dimensional vector, float base-type */
    public interface Real extends Vec.Real, Size { 

	/** Get element x,y */
	public float get(int x, int y);
	/** Set element x,y */
	public void set(int x, int y, float v);
	
	/** Return a duplicate / clone of the input vector */
	public Real duplicate() ;
	
	/** Copy the vector x into a region of this vector.
	 *  @param ulx x-coordinate of upper left corner where to paste  
	 *  @param uly y-coordinate of upper left corner where to paste  
	 *  @param zero Zero the output vector before paste */
	public void paste( Vec2d.Real xIn, int ulx, int uly, boolean zero );

	/** width of the vector */
	public int vectorWidth();

	/** height of the vector */
	public int vectorHeight();
    
	/** project (sum along z, from start to end) a Vec3d into this vector */
	public void project( Vec3d.Real in, int start, int end );
	
	/** project (sum along z) a Vec3d into this vector */
	public void project( Vec3d.Real in );
	
	/** get slice nr n */
	public void slice( Vec3d.Real in, int n );
    
    }
    
    // =======================================================
    
    /** Complex-valued, 2-dimensional vector, float base-type */
    public interface Cplx extends Vec.Cplx, Size { 

	
	/** Get element x,y */
	public org.fairsim.linalg.Cplx.Float get(int x, int y);
	/** Set element x,y */
	public void set(int x, int y, org.fairsim.linalg.Cplx.Float v);
	/** Set element x,y */
	public void set(int x, int y, org.fairsim.linalg.Cplx.Double v);
	
	/** Return a duplicate / clone of the input vector */
	public abstract Cplx duplicate() ; 

	/** Compute the in-place fft */
	public void fft2d( boolean inverse );

	/** Copy the vector x into a region of this vector.
	 *  Short-hand for paste( xIn, yOut, 0, 0, xWidth(), xHeight(), ulx, uly, zero )
	 *  @param ulx x-coordinate of upper left corner where to paste  
	 *  @param uly y-coordinate of upper left corner where to paste  
	 *  @param zero Zero the output vector before paste */
	public void paste( Vec2d.Cplx xIn, int ulx, int uly, boolean zero );

	/** Copy a freq-space vector into this one.
	 * @param xIn The vector to copy into this one 
	 * @param xOff The offset in x-direction 
	 * @param yOff The offset in y-direction */
	public void pasteFreq( Vec2d.Cplx xIn , int xOff, int yOff );

	/** pasteFreq(xIn, 0, 0) */
	public void pasteFreq( Vec2d.Cplx xIn );



	/** Multiply a vector with Fourier shift theorem phases.
	 *  @param kx x-coordinate of shift
	 *  @param ky y-coordinate of shift */
	public void fourierShift( double kx, double ky);


	/** width of the vector */
	public int vectorWidth();

	/** height of the vector */
	public int vectorHeight();

	/** project (sum along z, from start to end) a Vec3d into this vector */
	public void project( Vec3d.Cplx in, int start, int end );
	
	/** project (sum along z) a Vec3d into this vector */
	public void project( Vec3d.Cplx in );
    
	/** get slice nr n */
	public void slice( Vec3d.Cplx in, int n );
    
    }
   
    // =======================================================
    
    /** Copy a region of vector xIn into this vector, generic implementation.
     *  @param xIn Input vector
     *  @param yOut Vector to paste into
     *  @param ulxin x-coordinate upper left corner of region to be copied
     *  @param ulyin y-coordinate upper left corner of region to be copied
     *  @param w width of region to be copied
     *  @param h height of region to be copied
     *  @param ulxout x-coordinate upper left corner of output position
     *  @param ulyout y-coordinate upper left corner of output position 
     *  @param zeroOutput If true, vector gets zero'd before pasting data
     *  */
    public static void paste( Vec2d.Real xIn, Vec2d.Real yOut,
	final int ulxin, final int ulyin, final int w, final int h,
	final int ulxout, final int ulyout, boolean zeroOutput ) {

	float [] in  =  xIn.vectorData();
	float [] out = yOut.vectorData();

	// Checks:
	if (ulxin<0 || ulyin<0 || w<0 || h<0 || ulyout<0 || ulxout<0 )
	    throw new RuntimeException("Coordinates not positive");
	if ( ( ulxin + w > xIn.vectorWidth() ) ||
	     ( ulyin + h > xIn.vectorHeight() ) )
	    throw new RuntimeException("Input size mismatch");
	if ( ( ulxout + w  > yOut.vectorWidth() ) ||
	     ( ulyout + h > yOut.vectorHeight() ) )
	    throw new RuntimeException("Output size mismatch");
	
	// zero
	if ( zeroOutput)
	    yOut.zero();
	//Tool.trace(""+ulyout+" "+ulxout+" "+ulyin+" "+ulxin+" "+w+" "+h);
	
	final int outWidth = yOut.vectorWidth();
	final int inWidth  = xIn.vectorWidth();

	// copy
	for (int y=0;y<h;y++)
	for (int x=0;x<w;x++)
	    out[ (y + ulyout) * outWidth + ( x + ulxout ) ] =
		in[ (y + ulyin) * inWidth + ( x + ulxin ) ];

	yOut.syncBuffer();
    }
    
    /** Copy a region of vector xIn into this vector, generic implementation.
     *  @param xIn Input vector
     *  @param yOut Vector to paste into
     *  @param ulxin x-coordinate upper left corner of region to be copied
     *  @param ulyin y-coordinate upper left corner of region to be copied
     *  @param w width of region to be copied
     *  @param h height of region to be copied
     *  @param ulxout x-coordinate upper left corner of output position
     *  @param ulyout y-coordinate upper left corner of output position 
     *  @param zeroOutput If true, vector gets zero'd before pasting data
     *  */
    public static void paste( Vec2d.Cplx xIn, Vec2d.Cplx yOut,
	final int ulxin, final int ulyin, final int w, final int h,
	final int ulxout, final int ulyout, boolean zeroOutput ) {

	float [] in  =  xIn.vectorData();
	float [] out = yOut.vectorData();

	// Checks:
	if (ulxin<0 || ulyin<0 || w<0 || h<0 || ulyout<0 || ulxout<0 )
	    throw new RuntimeException("Coordinates not positive");
	if ( ( ulxin + w > xIn.vectorWidth() ) ||
	     ( ulyin + h > xIn.vectorHeight() ) )
	    throw new RuntimeException("Input size mismatch");
	if ( ( ulxout + w  > yOut.vectorWidth() ) ||
	     ( ulyout + h > yOut.vectorHeight() ) )
	    throw new RuntimeException("Output size mismatch");
	
	// zero
	if ( zeroOutput)
	    yOut.zero();
	//Tool.trace(""+ulyout+" "+ulxout+" "+ulyin+" "+ulxin+" "+w+" "+h);
	
	final int outWidth = yOut.vectorWidth();
	final int inWidth  = xIn.vectorWidth();

	// copy
	for (int y=0;y<h;y++)
	for (int x=0;x<w;x++){
	    out[ ((y + ulyout) * outWidth + ( x + ulxout ))*2+0 ] =
		in[ ((y + ulyin) * inWidth + ( x + ulxin ))*2+0 ];
	    out[ ((y + ulyout) * outWidth + ( x + ulxout ))*2+1 ] =
		in[ ((y + ulyin) * inWidth + ( x + ulxin ))*2+1 ];

	}

	yOut.syncBuffer();
    }
    



}

