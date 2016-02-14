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
along with ESI.  If not, see <http://www.gnu.org/licenses/>
*/

package org.fairsim.linalg;


/** Minimal implementation of some linear algebra. Did some rewrites,
 * trying to come up with a consistant syntax. Now it is like this:
 * For all operations, the input arguments are NEVER written to,
 * only read from, i.e. <br>
 * a.sub( b ) will only change a, not b.
 *
 * */
public final class Vec {

    // ================================================================
    
    /** private constructor, so this class only has static methods */
    private Vec() { throw new AssertionError(); }

    /** Vector factory to use */
    static VectorFactory vf = BasicVector.getFactory();

    /** Set the factory used for obtaining vectors through
     * the static 'create' functions. */
    public static void setVectorFactory( VectorFactory invf ) {
	if (vf==null)
	    throw new java.lang.NullPointerException("vf was null, not set");
	vf = invf;
    }

    /** Creates a real-valued vector of size n. */ 
    public static Real createReal( int n ) {
	return vf.createReal(n);
    } 

    /** Creates a complex-valued vector of size n. */
    public static Cplx createCplx( int n ) {
	return vf.createCplx(n);
    } 

    /** Creates a k-element array of real-valued vector of size n. */
    public static Real [] createArrayReal( int k, int n ) {
	Real [] ret = new Real[k];
	for (int i=0; i<k; i++)
	    ret[i] = vf.createReal(n);
	return ret;
    } 

    /** Creates a k-element array of complex-valued vector of size n. */ 
    public static Cplx [] createArrayCplx( int k, int n ) {
	Cplx [] ret = new Cplx[k];
	for (int i=0; i<k; i++)
	    ret[i] = vf.createCplx(n);
	return ret;
    } 

    /** Finish whatever parallel / concurrent process is running (for timing CUDA, etc.).
     *	This calls {VectorFactory#syncConcurrent} of the current vector factory. */
    public static void syncConcurrent()  {
	vf.syncConcurrent();
    }

    // ================================================================

    /** Throw runtime exception if not all vectors are of same size. */
    public static int failSize( Vec.Real v0, Vec.Real ... v ) {
	if (v.length<1) 
	    return v0.vectorSize();
	
	int len = v0.vectorSize();
	for (int i=0;i<v.length;i++)
	    if (v[i].vectorSize() != len)
		throw new RuntimeException("Vector len mismatch"
		    +i+" "+len+" "+v[i].vectorSize());
	return len;
    }

    /** Throw runtime exception if not all vectors are of same size. */
    public static int failSize( Vec.Cplx v0, Vec.Cplx ... v ) {
	if (v.length<1) 
	    return v0.vectorSize();
	
	int len = v0.vectorSize();
	for (int i=0;i<v.length;i++)
	    if (v[i].vectorSize() != len)
		throw new RuntimeException("Vector len mismatch"
		    +i+" "+len+" "+v[i].vectorSize());
	return len;
    }
    
    /** Throw runtime exception if not all vectors are of same size. */
    public static int failSize( Vec.Cplx v0, Vec.Real ... v ) {
	if (v.length<1) 
	    return v0.vectorSize();
	
	int len = v0.vectorSize();
	for (int i=0;i<v.length;i++)
	    if (v[i].vectorSize() != len)
		throw new RuntimeException("Vector len mismatch"
		    +i+" "+len+" "+v[i].vectorSize());
	return len;
    }
    
    /** Throw runtime exception if not all vectors are of same size. */
    public static int failSize( Vec.Real v0, Vec.Cplx ... v ) {
	if (v.length<1) 
	    return v0.vectorSize();
	
	int len = v0.vectorSize();
	for (int i=0;i<v.length;i++)
	    if (v[i].vectorSize() != len)
		throw new RuntimeException("Vector len mismatch"
		    +i+" "+len+" "+v[i].vectorSize());
	return len;
    }


    // ================================================================

    /** A real-valued vector, base-type float */
    public interface Real {

	/** Return a copy/duplicate of this vector */
	public abstract Real duplicate();

	/** Number of elements in the vector */
	public int vectorSize();

	/** Access to the internal vector array. 
	 *  If the vector is backed by external memory (GPU, etc.) it
	 *  will be synced before the buffer is returned. If the buffer
	 *  is written to, call {@link #syncBuffer} after the write. */
	public float [] vectorData ();

	/** Sync content from the last reference returned by
	 *  {@link #vectorData}, invalidating the reference.
	 *  After a sync call, the buffer has to be re-obtained through
	 *  vectorData. */
	public void syncBuffer();


	/** Make all buffers (CPU, GPU, ...) coherent. This call ONLY influence
	 *  WHEN buffers are syncronized (for timing, etc.), the program will run
	 *  correctly without explicitly calling makeCoherent. */
	public void makeCoherent();

	// --- copy functions ---

	/** Copy the content of 'in' into this vector */
	public void copy(Real in);

	/** Copy the real/imag values of 'in' into this vector */
	public void copy(Cplx in, boolean imag);

	/** Copy the real values of 'in' into this vector */
	public void copy(Cplx in);

	/** Copy the magnitudes of elements in 'in' into this vector */
	public void copyMagnitude(Cplx in);

	/** Copy the phases of elements in 'in' into this vector */
	public void copyPhase(Cplx in);
	
	/** Set all elements to zero */
	public void zero();
	
	/** Add all input vectors to this vector */
	public void add( Real ... in );

	/** Computes this += a * x */
	public void axpy( float a , Vec.Real x );

	/** Multiply by scalar, ie this *= a */
	public void scal( float a );
	
	/** Return the dot product < this, x > */
	public double dot(Real x);
	
	/** Return the squared norm <this, this> */
	public double norm2();

	/** Compute the elemnt-wise multiplication this = this.*x */
	public void times(Vec.Real x);
	
	/** Return the sum of all vector elements */
	public double sumElements();

	/** Normalize the vector to 0..1 */
	public void normalize();

	/** Normalize the vector to vmin..vmax */
	public void normalize(float vmin, float vmax);
	
	/** Set every element to 1/element */
	public void reciproc();

	/** Compute y = y + x^2 */
	public void addSqr( Vec.Real xIn );
	
	/** Output the first 10 vector elements for debugging */	
	public String first10Elem() ;

    }


    
    // ================================================================


    /** A complex-valued vector, base-type float */
    public interface Cplx {

	
	/** called after writing to the buffer. Typically,
	 * copies data back to the accelarator. */
	public void syncBuffer() ;
	
	/** Make all buffers (CPU, GPU, ...) coherent. This call ONLY influence
	 *  WHEN buffers are syncronized (for timing, etc.), the program will run
	 *  correctly without explicitly calling makeCoherent. */
	public void makeCoherent();

	/** Return a deep copy/duplicate of this vector. Implementations
	 * should typically return their specific type here. */
	public Cplx duplicate();

	/** Return the n'th element */
	/*
	org.fairsim.linalg.Cplx.Float get( int n ); {
	    this.readyBuffer();
	    return new org.fairsim.linalg.Cplx.Float( data[2*n], data[2*n+1] );
	} */
	
	/** Set the n'th element */
	/*
	void set( int n, org.fairsim.linalg.Cplx.Float v ) {
	    this.readyBuffer();
	    data[2*n+0] = v.re;
	    data[2*n+1] = v.im;
	    this.syncBuffer();
	} */

	/** Set the n'th element */
	/*
	void set( int n, org.fairsim.linalg.Cplx.Double v ) {
	    this.readyBuffer();
	    data[2*n+0] = (float)v.re;
	    data[2*n+1] = (float)v.im;
	    this.syncBuffer();
	} */
   
	// --- creating new vectors ---

	
	/** Return a copy of the real elements in this vector */
	public Real duplicateReal();

	/** Return a copy of the imaginary elements in this vector */
	public Real duplicateImag();
	
	/** Return a copy, containing the magnitudes of elements in this vector */
	public Real duplicateMagnitude();

	/** Return a copy, containing the phases of elements in this vector */
	public Real duplicatePhase();

	// --- arith. functions ---

	/** Copy the content of 'in' into this vector */
	public void copy(Cplx in);

	/** Copy the content of 'in' into this vector */
	public void copy(Real in);

	/** Set all elements to zero */
	public void zero();
	
	/** Add all input vectors to this vector */
	public void add( Cplx ... in );

	/** Computes this += a * x */
	public void axpy( float a , Vec.Cplx x );
	
	/** Computes this += a * x */
	public void axpy( org.fairsim.linalg.Cplx.Float a , Vec.Cplx x );
	
	/** Set every element to 1/element */
	public void reciproc();
    
	/** Multiply by scalar, ie this *= a */
	public void scal( float a );

	/** Scale by 'in', ie this *= in */
	public void scal(org.fairsim.linalg.Cplx.Float in);
	
	/** Return the squared norm <this, this> */
	public double norm2();
	
	/** Complex conjugate every element of this vector */
	public void conj();
	
	/** Compute the dot product < this^, y > */
	public org.fairsim.linalg.Cplx.Double dot(Vec.Cplx yIn);

	/** Compute element-wise multiplication this = this.*in. */
	public void times(Cplx in);

	/** Compute element-wise multiplication this = this.*conj(in) */
	public void timesConj(Cplx in);

	/** Compute element-wise multiplication this = this.*in, 
	 *  conjugated 'in' if 'conj' is true */
	public void times(Cplx in, final boolean conj);

	/** Compute element-wise multiplication this = this.*in */ 
	public void times(Real in);

	/** Return the sum of all vector elements */
	public org.fairsim.linalg.Cplx.Double sumElements( );

	/** Compute this += x^2 */
	public void addSqr( Vec.Cplx xIn);
	
	/** Access to the internal vector array */
	public float [] vectorData ();

	/** Vector size */
	public int vectorSize();

	/** Output the first 10 vector elements for debugging */	
	public String first10Elem() ;

    }

}
    
