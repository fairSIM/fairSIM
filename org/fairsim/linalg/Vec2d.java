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
	return new Real(w,h);
    }
 
    /** Return a 2-dimensional vector sized w x h, complex-valued  */
    public static Cplx createCplx( int w, int h) {
	return new Cplx(w,h);
    }
    
    /** Return a 2-dimensional vector, width and height of 'size'
     *  scaled by 'scale' */
    public static Real createReal( Size size, int scale ) {
	return new Real( scale*size.vectorWidth(), scale*size.vectorHeight() );
    }

    /** Return a 2-dimensional complex-valued vector, width and height of 'size'
     *  scaled by 'scale' */
    public static Cplx createCplx( Size size, int scale ) {
	return new Cplx( scale*size.vectorWidth(), scale*size.vectorHeight() );
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
	    ret[i] = new Real(w,h);
	return ret;
    }
    
    /** Return a n-element array of 2-dimensional vector,
     *  sized w x h with complex-valued elements */
    public static Cplx [] createArrayCplx(int n, int w, int h) {
	Cplx [] ret = new Cplx[n];
	for (int i=0; i<n; i++)
	    ret[i] = new Cplx(w,h);
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
    public static class Real extends Vec.Real implements Size { 

	protected final int width, height;
	
	Real( int w, int h ) {
	    super(w*h);
	    width=w; height=h;
	}

	/** Create a real-valued vector, backed by data in 'dat' */
	protected Real( int w, int h, float [] dat ) {
	    super( dat );
	    if ( w*h != dat.length )
		throw new RuntimeException("Vector w*x size mismatch to 'dat'");
	    width=w; height=h;
	}



	/** Get element x,y */
	public float get(int x, int y) {
	    return data[x+y*width];
	}

	/** Set element x,y */
	public void set(int x, int y, float v) {
	    data[x+y*width] = v;
	}

	/** Return a duplicate / clone of the input vector */
	public Real duplicate(  ) {
	    Real ret = new Real( width, height );
	    System.arraycopy( data, 0 , ret.data, 0, elemCount);
	    return ret;
	}


	 /** Copy a region of vector xIn into this vector. 
	 *  @param ulxin x-coordinate upper left corner of region to be copied
	 *  @param ulyin y-coordinate upper left corner of region to be copied
	 *  @param w width of region to be copied
	 *  @param h height of region to be copied
	 *  @param ulxout x-coordinate upper left corner of output position
	 *  @param ulyout y-coordinate upper left corner of output position 
	 *  @param zeroOutput If true, vector gets zero'd before pasting data
	 *  */
	public void paste( Vec2d.Real xIn, 
	    final int ulxin, final int ulyin, final int w, final int h,
	    final int ulxout, final int ulyout, boolean zeroOutput ) {

	    float [] in  =  xIn.data;
	    float [] out = this.data;

	    // Checks:
	    if (ulxin<0 || ulyin<0 || w<0 || h<0 || ulyout<0 || ulxout<0 )
		throw new RuntimeException("Coordinates not positive");
	    if ( ( ulxin + w > xIn.width ) ||
		 ( ulyin + h > xIn.height ) )
		throw new RuntimeException("Input size mismatch");
	    if ( ( ulxout + w  > this.width ) ||
		 ( ulyout + h > this.height ) )
		throw new RuntimeException("Output size mismatch");
	    
	    // zero
	    if ( zeroOutput)
		zero();
	    //Tool.trace(""+ulyout+" "+ulxout+" "+ulyin+" "+ulxin+" "+w+" "+h);


	    // copy
	    for (int y=0;y<h;y++)
	    for (int x=0;x<w;x++)
		out[ (y + ulyout) * this.width + ( x + ulxout ) ] =
		    in[ (y + ulyin) * xIn.width + ( x + ulxin ) ];
	}
    
	/** Copy the vector x into a region of this vector.
	 *  Short-hand for paste( xIn, 0, 0, xWidth(), xHeight(), ulx, uly, zero )
	 *  @param ulx x-coordinate of upper left corner where to paste  
	 *  @param uly y-coordinate of upper left corner where to paste  
	 *  @param zero Zero the output vector before paste */
	public void paste( Vec2d.Real xIn, int ulx, int uly, boolean zero ) {
	    paste( xIn,  0, 0, xIn.width, xIn.height, ulx, uly, zero );
	}
 
	/** Sum of each row. Returns a vector containing the sum of each row */
	public Vec.Real sumRows() {
	    Vec.Real ret = Vec.createReal( height );
	    for (int y=0;y<height;y++) {
		for (int x=0;x<width;x++)
		    ret.data[y] += data[y*width+x];
	    }
	    return ret;
	}

	/** Sum of each col. Returns a vector containing the sum of each column. */
	public Vec.Real sumCols() {
	    Vec.Real ret = Vec.createReal( width );
	    for (int y=0;y<height;y++) {
		for (int x=0;x<width;x++)
		    ret.data[x] += data[y*width+x];
	    }
	    return ret;
	}

	/** width of the vector */
	public int vectorWidth() {
	    return width;
	}

	/** height of the vector */
	public int vectorHeight() {
	    return height;
	}


    }
    
    // =======================================================
    
    /** Complex-valued, 2-dimensional vector, float base-type */
    public static class Cplx extends Vec.Cplx implements Size { 
	protected final int width, height;	
	
	Cplx( int w, int h ) {
	    super(w*h);
	    width=w; height=h;
	}
	
	/** Create a real-valued vector, backed by data in 'dat' */
	protected Cplx( int w, int h, float [] dat ) {
	    super( dat );
	    if ( (w*h)*2 != dat.length )
		throw new RuntimeException("Vector w*x size mismatch to 'dat'");
	    width=w; height=h;
	}

	
	/** Get element x,y */
	public org.fairsim.linalg.Cplx.Float get(int x, int y) {
	    return new org.fairsim.linalg.Cplx.Float( 
		data[2*(x+y*width)], data[2*(x+y*width)+1]);
	}
	/** Set element x,y */
	public void set(int x, int y, org.fairsim.linalg.Cplx.Float v) {
	    data[2 * (x+y*width)   ] = v.re;
	    data[2 * (x+y*width) +1] = v.im;
	}
	/** Set element x,y */
	public void set(int x, int y, org.fairsim.linalg.Cplx.Double v) {
	    data[2 * (x+y*width)   ] = (float)v.re;
	    data[2 * (x+y*width) +1] = (float)v.im;
	}
	
	/** Return a duplicate / clone of the input vector */
	public Cplx duplicate(  ) {
	    Cplx ret = new Cplx( width, height );
	    System.arraycopy( data, 0 , ret.data, 0, elemCount*2);
	    return ret;
	}

	/** Copy a region of vector xIn into this vector
	 *  @param ulxin x-coordinate upper left corner of region to be copied
	 *  @param ulyin y-coordinate upper left corner of region to be copied
	 *  @param w width of region to be copied
	 *  @param h height of region to be copied
	 *  @param ulxout x-coordinate upper left corner of output position
	 *  @param ulyout y-coordinate upper left corner of output position 
	 *  @param zeroOutput If true, vector gets zero'd before pasting data
	 *  */
	public void paste( Vec2d.Cplx xIn, 
	    final int ulxin, final int ulyin, final int w, final int h,
	    final int ulxout, final int ulyout, boolean zeroOutput ) {

	    float [] in  =  xIn.data;
	    float [] out = this.data;

	    // Checks
	    if (ulxin<0 || ulyin<0 || w<0 || h<0 || ulyout<0 || ulxout<0 )
		throw new RuntimeException("Coordinates not positive");
	    if ( ( ulxin + w > xIn.width ) ||
		 ( ulyin + h > xIn.height ) )
		throw new RuntimeException("Input size mismatch");
	    if ( ( ulxout + width  > this.width ) ||
		 ( ulyout + height > this.height ) )
		throw new RuntimeException("Output size mismatch");
	    
	    // zero
	    if ( zeroOutput)
		zero( );
	    
	    // copy
	    for (int y=0;y<h;y++)
	    for (int x=0;x<w;x++)
	    for (int c=0;c<2;c++)
		out[ 2*( (y + ulyout) * this.width + ( x + ulxout ) )+c] =
		    in[ 2*( (y + ulyin) * xIn.width + ( x + ulxin ) )+c];
	}
	
	/** Copy the vector x into a region of this vector.
	 *  Short-hand for paste( xIn, yOut, 0, 0, xWidth(), xHeight(), ulx, uly, zero )
	 *  @param ulx x-coordinate of upper left corner where to paste  
	 *  @param uly y-coordinate of upper left corner where to paste  
	 *  @param zero Zero the output vector before paste */
	public void paste( Vec2d.Cplx xIn, int ulx, int uly, boolean zero ) {
	    paste( xIn, 0, 0, xIn.width, xIn.height, ulx, uly, zero );
	}

	/** width of the vector */
	public int vectorWidth() {
	    return width;
	}

	/** height of the vector */
	public int vectorHeight() {
	    return height;
	}




    }






   
}

