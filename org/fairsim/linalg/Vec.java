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
 
    /** Creates a real-valued vector of size n. */ 
    public static Real createReal( int n ) {
	return new Real(n);
    }

    /** Creates a complex-valued vector of size n. */
    public static Cplx createCplx( int n ) {
	return new Cplx(n);
    }

    /** Creates a k-element array of real-valued vector of size n. */
    public static Real [] createArrayReal( int k, int n ) {
	Real [] ret = new Real[k];
	for (int i=0; i<k; i++)
	    ret[i] = new Real(n);
	return ret;
    }

    /** Creates a k-element array of complex-valued vector of size n. */ 
    public static Cplx [] createArrayCplx( int k, int n ) {
	Cplx [] ret = new Cplx[k];
	for (int i=0; i<k; i++)
	    ret[i] = new Cplx(n);
	return ret;
    }

    /** Throw runtime exception if not all vectors are of same size. */
    public static void failSize( Real v0, Real ... v ) {
	if (v.length<1) return;
	int len = v0.elemCount;
	for (int i=0;i<v.length;i++)
	    if (v[i].elemCount != len)
		throw new RuntimeException("Vector len mismatch"
		    +i+" "+len+" "+v[i].elemCount);
    }

    /** Throw runtime exception if not all vectors are of same size. */
    public static void failSize( Cplx v0, Cplx ... v ) {
	if (v.length<1) return;
	int len = v0.elemCount;
	for (int i=0;i<v.length;i++)
	    if (v[i].elemCount != len)
		throw new RuntimeException("Vector len mismatch"
		    +i+" "+len+" "+v[i].elemCount);
    }
    
    /** Throw runtime exception if not all vectors are of same size. */
    public static void failSize( Cplx v0, Real ... v ) {
	if (v.length<1) return;
	int len = v0.elemCount;
	for (int i=0;i<v.length;i++)
	    if (v[i].elemCount != len)
		throw new RuntimeException("Vector len mismatch"
		    +i+" "+len+" "+v[i].elemCount);
    }



    // ================================================================

    /** A real-valued vector, base-type float */
    public static class Real {
	final protected int elemCount;
	final protected float [] data;
	
	/** Creates empty vector */
	Real(int n) {
	    elemCount = n;
	    data = new float[ n ];
	}

	/** create a vector backed by the data in 'd' */
	protected Real(float [] d) {
	    elemCount = d.length;
	    data = d;
	}



	/** Return the n'th element */
	float get( int n ) {
	    return data[n];
	}
	
	/** Set the n'th element */
	void set( int n, float v ) {
	    data[n] = v;
	}
   
	/** Return a copy/duplicate of this vector */
	public Real duplicate() {
	    Real ret = new Real( elemCount );
	    System.arraycopy( data, 0 , ret.data, 0, elemCount);
	    return ret;
	}

	// --- copy functions ---

	/** Copy the content of 'in' into this vector */
	public void copy(Real in) {
	    Vec.failSize( this, in);
	    System.arraycopy( in.data, 0 , data, 0, elemCount);
	}
	
	/** Copy the real/imag values of 'in' into this vector */
	public void copy(Cplx in, boolean imag) {
	    Vec.failSize( in, this);
	    for (int i=0;i<elemCount;i++)
		data[i] = in.data[2*i + ((imag)?(1):(0)) ];
	}
	
	/** Copy the real values of 'in' into this vector */
	public void copy(Cplx in) {
	    copy(in, false);
	}


	/** Copy the magnitudes of elements in 'in' into this vector */
	public void copyMagnitude(Cplx in) {
	    Vec.failSize( in, this);
	    for (int i=0;i<elemCount;i++)
		data[i] = (float)MTool.fhypot(in.data[2*i], in.data[2*i+1]);
	}
	
	/** Copy the phases of elements in 'in' into this vector */
	public void copyPhase(Cplx in) {
	    Vec.failSize( in, this);
	    for (int i=0;i<elemCount;i++)
		data[i] = (float)Math.atan2(in.data[2*i+1], in.data[2*i]);
	}
	
	


	
	/** Set all elements to zero */
	public void zero() {
	    java.util.Arrays.fill( data, 0, elemCount, 0 );
	}

	/** Add all input vectors to this vector */
	public void add( Real ... in ) {
	    failSize(this, in);
	    for (int i=0;i<in.length;i++)
		for (int j=0;j<elemCount;j++)
		    data[j] += in[i].data[j];
	}
	
	/** Computes this += a * x */
	public void axpy( float a , Vec.Real x ) {
	    failSize(this, x);
	    for (int j=0;j<elemCount;j++)
		data[j] += a * x.data[j];
	}
    
	/** Multiply by scalar, ie this *= a */
	public void scal( float a ) {
	    for (int j=0;j<elemCount;j++)
		data[j] *= a;
	}
    
	/** Return the dot product < this, x > */
	public double dot(Real x) {
	    failSize( this, x);
	    double ret=0;
	    for (int i=0;i<elemCount;i++) 
		ret+= x.data[i] * data[i];
	    return ret;
	}

	/** Return the squared norm <this, this> */
	public double norm2() {
	    double ret=0;
	    for (int i=0;i<elemCount;i++) 
		ret+= data[i] * data[i];
	    return ret;
	}
	
	/** Compute the elemnt-wise multiplication this = this.*x */
	public void times(Vec.Real x) {
	    failSize(this, x);
	    for (int i=0;i<elemCount;i++) 
		data[i] = data[i] * x.data[i]; 
	}

	/** Return the sum of all vector elements */
	public double sumElements() {
	    double ret=0;
	    for (int i=0;i<elemCount; i++) {
		ret += data[i];	
	    }
	    return ret;
	}
	
	/** Normalize the vector to 0..1 */
	public void normalize(){

	    // calculate min and max for scaling
	    float min = Float.MAX_VALUE;
	    float max = Float.MIN_VALUE;
	    for ( float i : data ) {
		if (i<min) min=i;
		if (i>max) max=i;
	    }
	    for (int i=0;i<data.length;i++)
		data[i]=(data[i]-min)/(max-min);
	}
	
	/** Normalize the vector to vmin..vmax */
	public void normalize(float vmin, float vmax){

	    // calculate min and max for scaling
	    float min = Float.MAX_VALUE;
	    float max = Float.MIN_VALUE;
	    for ( float i : data ) {
		if (i<min) min=i;
		if (i>max) max=i;
	    }
	    for (int i=0;i<data.length;i++)
		data[i]=vmin+ (((data[i]-min)/(max-min))*(vmax-vmin));
	}

	/** Set every element to 1/element */
	public void reciproc() {
	    for (int i=0; i<elemCount; i++)
		data[i] = 1.f/data[i];
	}


	/** Compute y = y + x^2 */
	public void addSqr( Vec.Real xIn ) {
	    failSize(this, xIn);
	    float [] x = xIn.data, y = data;
	    for (int i=0;i<elemCount;i++) 
		y[i] += x[i]*x[i] ; 
	}


	/** Access to the internal vector array */
	public float [] vectorData () {
	    return data;
	}

	/** Vector size */
	public int vectorSize() {
	    return elemCount;
	}


    }


    
    // ================================================================


    /** A complex-valued vector, base-type float */
    public static class Cplx {
	final protected int elemCount;
	final protected float [] data;
	
	/** Create empty vector */
	Cplx(int n) {
	    elemCount = n;
	    data = new float[ 2*n ];
	}
	
	/** create a vector backed by the data in 'd' */
	protected Cplx(float [] d) {
	    if ( d.length % 2 == 1 )
		throw new RuntimeException("Input array len not even, should be for cplx");
	    elemCount = d.length/2;
	    data = d;
	}

	/** Return the n'th element */
	org.fairsim.linalg.Cplx.Float get( int n ) {
	    return new org.fairsim.linalg.Cplx.Float( data[2*n], data[2*n+1] );
	}
	
	/** Set the n'th element */
	void set( int n, org.fairsim.linalg.Cplx.Float v ) {
	    data[2*n+0] = v.re;
	    data[2*n+1] = v.im;
	}

	/** Set the n'th element */
	void set( int n, org.fairsim.linalg.Cplx.Double v ) {
	    data[2*n+0] = (float)v.re;
	    data[2*n+1] = (float)v.im;
	}
   
	// --- creating new vectors ---

	/** Return a copy/duplicate of this vector */
	public Cplx duplicate() {
	    Cplx ret = new Cplx( elemCount );
	    System.arraycopy( data, 0 , ret.data, 0, elemCount*2);
	    return ret;
	}
	
	/** Return a copy of the real elements in this vector */
	public Real duplicateReal() {
	    Real ret = new Real( elemCount );
	    ret.copy( this, false );
	    return ret;
	}
	
	/** Return a copy of the imaginary elements in this vector */
	public Real duplicateImag() {
	    Real ret = new Real( elemCount );
	    ret.copy( this, true );
	    return ret;
	}

	/** Return a copy, containing the magnitudes of elements in this vector */
	public Real duplicateMagnitude() {
	    Real ret = new Real( elemCount );
	    ret.copyMagnitude( this );
	    return ret;
	}
	
	/** Return a copy, containing the phases of elements in this vector */
	public Real duplicatePhase() {
	    Real ret = new Real( elemCount );
	    ret.copyPhase( this );
	    return ret;
	}
	
	// --- arith. functions ---

	/** Copy the content of 'in' into this vector */
	public void copy(Cplx in) {
	    Vec.failSize( this, in);
	    System.arraycopy( in.data, 0 , data, 0, elemCount*2);
	}
	
	/** Copy the content of 'in' into this vector */
	public void copy(Real in) {
	    Vec.failSize( this, in);
	    for (int i=0;i<elemCount; i++ ) {
		data[i*2] = in.data[i];
		data[i*2+1] = 0;
	    }
	}
	
	/** Set all elements to zero */
	public void zero() {
	    java.util.Arrays.fill( data, 0, elemCount*2, 0 );
	}

	/** Add all input vectors to this vector */
	public void add( Cplx ... in ) {
	    failSize(this, in);
	    for (int i=0;i<in.length;i++)
		for (int j=0;j<elemCount*2;j++)
		    data[j] += in[i].data[j];
	}
	
	/** Computes this += a * x */
	public void axpy( float a , Vec.Cplx x ) {
	    failSize(this, x);
	    for (int j=0;j<elemCount*2;j++)
		data[j] += a * x.data[j];
	}

	/** Computes this += a * x */
	public void axpy( org.fairsim.linalg.Cplx.Float a , Vec.Cplx x ) {
	    failSize(this, x);
	    for (int i=0;i<elemCount;i++) {
		data[Rl(i)] += multReal( a.re, a.im, x.data[Rl(i)], x.data[Ig(i)] )  ;
		data[Ig(i)] += multImag( a.re, a.im, x.data[Rl(i)], x.data[Ig(i)] )  ;
	    }
	}

	/** Set every element to 1/element */
	public void reciproc() {
	    for (int i=0; i<elemCount; i++) {
		float s = data[2*i]*data[2*i] + data[2*i+1]*data[2*i+1];
		data[ 2*i   ] =  data[2*i  ] / s;
		data[ 2*i+1 ] = -data[2*i+1] / s;
	    }
	}

    
	/** Multiply by scalar, ie this *= a */
	public void scal( float a ) {
	    for (int j=0;j<elemCount*2;j++)
		data[j] *= a;
	}
	
	/** Scale by 'in', ie this *= in */
	public void scal(org.fairsim.linalg.Cplx.Float in) {
	    float []  y = data;
	    for (int i=0;i<elemCount;i++) {
		float y1R = y[Rl(i)], y1I = y[Ig(i)];
		y[Rl(i)] = multReal( in.re, in.im, y1R, y1I )  ;
		y[Ig(i)] = multImag( in.re, in.im, y1R, y1I )  ;
	    }
	}


	/** Return the squared norm <this, this> */
	public double norm2() {
	    double ret=0;
	    for (int i=0;i<elemCount*2;i++) 
		ret+= data[i] * data[i];
	    return ret;
	}

	/** Complex conjugate every element of this vector */
	public void conj() {
	    for (int i=0;i<elemCount;i++)
		data[2*i+1]*=-1;
	}

	/** Compute the dot product < this^, y > */
	public org.fairsim.linalg.Cplx.Double dot(Vec.Cplx yIn) {
	    failSize( this, yIn );
	    float [] x = this.data, y = yIn.data;
	    double resre=0, resim=0;

	    for (int i=0;i<elemCount;i++) {
		    resre += multReal( x[ Rl(i) ], -x[ Ig(i) ], y[ Rl(i) ], y[ Ig(i) ] );
		    resim += multImag( x[ Rl(i) ], -x[ Ig(i) ], y[ Rl(i) ], y[ Ig(i) ] );
	    }
	    return new org.fairsim.linalg.Cplx.Double( resre, resim);
	}


	/** Compute element-wise multiplication this = this.*in. */
	public void times(Cplx in) {
	    times(in, false);
	}
	
	/** Compute element-wise multiplication this = this.*conj(in) */
	public void timesConj(Cplx in) {
	    times(in, true);
	}


	/** Compute element-wise multiplication this = this.*in, 
	 *  conjugated 'in' if 'conj' is true */
	public void times(Cplx in, final boolean conj) {
	    failSize( this, in );
	    float [] x = in.data, y = data;
	    for (int i=0;i<elemCount;i++) {
		float y1R = y[Rl(i)], y1I = y[Ig(i)];
		if (!conj) {
		    y[Rl(i)] = multReal( x[Rl(i)], x[Ig(i)], y1R, y1I )  ;
		    y[Ig(i)] = multImag( x[Rl(i)], x[Ig(i)], y1R, y1I )  ;
		} else {
		    y[Rl(i)] = multReal( x[Rl(i)], -x[Ig(i)], y1R, y1I )  ;
		    y[Ig(i)] = multImag( x[Rl(i)], -x[Ig(i)], y1R, y1I )  ;
		}
	    }
	}
	
	/** Compute element-wise multiplication this = this.*in */ 
	public void times(Real in) {
	    failSize( this, in );
	    float [] x = in.data, y = data;
	    for (int i=0;i<elemCount;i++) {
	        y[Rl(i)] *= x[i]; 
	        y[Ig(i)] *= x[i]; 
	    }
	}


	/** Return the sum of all vector elements */
	public org.fairsim.linalg.Cplx.Double sumElements( ) {
	    double re=0,im=0;
	    for (int i=0;i<elemCount; i++) {
		re += data[2*i+0];	
		im += data[2*i+1];	
	    }
	    return new org.fairsim.linalg.Cplx.Double(re,im);
	}
	
	/** Compute this += x^2 */
	public void addSqr( Vec.Cplx xIn) {
	    failSize( xIn, this);
	    float [] x = xIn.data, y = data;
	    for (int i=0;i<elemCount;i++) 
		y[Rl(i)] += x[Rl(i)]*x[Rl(i)]+x[Ig(i)]*x[Ig(i)] ;
	}

	/** Access to the internal vector array */
	public float [] vectorData () {
	    return data;
	}

	/** Vector size */
	public int vectorSize() {
	    return elemCount;
	}

    }

    
    // ================================================================



    /** Compute the point-wise average of vectors in an array */
    /*
    public static Real average( Real ... inV) {
	avrvar( inV, avr, null);
    }
    */





    /** Compute the point-wise average and variance of vectors in an array. */
    /*
    static Real avrvar( boolean getVariance , Real ... inV ) {

	final int len = inV[0].elemCount;
	float count = inV.length;
	
	// avr 
	for ( Real s : inV ) {
	    for ( int i=0;i<len; i++) 
		out[i] += s.data[i] / inV.length;
	    count++;
	}
	
	// avr
	for ( int i=0;i<len; i++) 
	    avr[i] = out[i]/count;
	
	// var
	if ( getVariance == false) return new Real [] { avr };

	Real varVec = new Real( len );
	float [] var = varVec.data;
	for ( int i=0;i<len; i++) 
	    var[i] = 0;

	for ( Real s : inV ) 
	    for ( int i=0;i<len; i++) 
		var[i] += s.data[i]-avr[i];

	for ( int i=0;i<len; i++) 
	    var[i]/=(count-1);

    }
    */
    
    




    // ===================================================================
    

    /** real part of complex mult */
    static float multReal( float Xr, float Xi, float Yr, float Yi ) {
	    return ((Xr * Yr ) - (Xi*Yi));
    }
    /** imag part of complex mult */
    static float multImag( float Xr, float Xi, float Yr, float Yi ) {
	    return ((Xi * Yr ) + (Xr*Yi));
    }

    /** real part at a given index */
    private static int Rl(int i) { return ((i*2)+0); }
    /** imag part at a given index */
    private static int Ig(int i) { return ((i*2)+1); }

}
    
