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
public final class Vec3d {

    /** Vec3d is static only */
    private Vec3d() { 
	throw new AssertionError();	
    };
    
    /** Return a 3-dimensional vector sized w x h x d, real-valued */
    public static Real createReal( int w, int h, int d) {
	return Vec.vf.createReal3D(w,h,d);
    }
 
    /** Return a 3-dimensional vector sized w x h x z, complex-valued  */
    public static Cplx createCplx( int w, int h, int d) {
	return Vec.vf.createCplx3D(w,h,d);
    }
    
    
    /** Return a 3-dimensional vector, width and height of 'size' */
    public static Real createReal( Size size ) {
	return createReal(size.vectorWidth(), 
	    size.vectorHeight(), size.vectorDepth());
    }

    /** Return a 3-dimensional complex-valued vector, width and height of 'size' */
    public static Cplx createCplx( Size size ) {
	return createCplx(size.vectorWidth(), 
	    size.vectorHeight(), size.vectorDepth());
    }
 
 
    /** Return a n-element array of 3-dimensional vector,
     *  sized w x h x z, with real-valued elements */
    public static Real [] createArrayReal(int n, int w, int h, int d) {
	Real [] ret = new Real[n];
	for (int i=0; i<n; i++)
	    ret[i] = createReal(w,h,d);
	return ret;
    }
    
    /** Return a n-element array of 3-dimensional vector,
     *  sized w x h x z, with complex-valued elements */
    public static Cplx [] createArrayCplx(int n, int w, int h, int d) {
	Cplx [] ret = new Cplx[n];
	for (int i=0; i<n; i++)
	    ret[i] = createCplx(w,h,d);
	return ret;
    }

    /** Throws a runtime exception if not all vectors are 
     *	not of same width and height. */
    public static void failSize( Size ... v ) {
	if (v.length<2) return;
	int w = v[0].vectorWidth(), h=v[0].vectorHeight(), d=v[0].vectorDepth();
	for (int i=1;i<v.length;i++)
	    if ((v[i].vectorWidth()!=w) || (v[i].vectorHeight()!=h) || (v[i].vectorDepth()!=d))
		throw new RuntimeException("Vector len mismatch"
		    +i+" "+w+" "+h+" "+d+" "+" vs. "+
		    v[i].vectorWidth()+" "+v[i].vectorHeight()+" "+v[i].vectorDepth());
    }
    
    /** Object with width, height and depth */
    public interface Size {
	/** Width */
	public int vectorWidth();
	/** Height */
	public int vectorHeight();
	/** Depth */
	public int vectorDepth();
    }

    
    // =======================================================
    
    /**  Real-valued, 3-dimensional vector, float base-type */
    public interface Real extends Vec.Real, Size { 

	/** Get element at x,y,z */
	public float get(int x, int y, int z);

	/** Set element at x,y,z */
	public void set(int x, int y, int z, float v);
    
	/** Return a duplicte / clone of this vector */
	public Real duplicate();

	/** Set the z'th 
	 *  x-y-slice of the vector to the provided input */
	public void setSlice( int z, Vec2d.Real dat );

    }
    
    // =======================================================
    
    /** Complex-valued, 2-dimensional vector, float base-type */
    public interface Cplx extends Vec.Cplx, Size { 

	/** Get element at x,y,z */
	public org.fairsim.linalg.Cplx.Float get(int x, int y, int z);

	/** Set element at x,y,z */
	public void set(int x, int y, int z, 
	    org.fairsim.linalg.Cplx.Float v);
	
	/** Set element at x,y,z */
	public void set(int x, int y, int z, 
	    org.fairsim.linalg.Cplx.Double v);
    
	/** Return a duplicte / clone of this vector */
	public Cplx duplicate();

	/** Set the z'th 
	 *  x-y-slice of the vector to the provided input */
	public void setSlice( int z, Vec2d.Cplx dat );
	
	/** Set the z'th 
	 *  x-y-slice of the vector to the provided input */
	public void setSlice( int z, Vec2d.Real dat );

	/** Compute the in-place fft */
	public void fft3d( boolean inverse );

	/** TODO: comment, see Vec2d */
	public void pasteFreq( Cplx i );

	/** TODO: comment */
	void fourierShift( double kx, double ky, double kz);

    }
   
}

