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


/** Default implementation of {@link Vec.Cplx}, 
 *  backed by a float [].
 *  By implementing readyBuffer, syncBuffer, performance-critial
 *  functions can be moved to the accelarator step-by-step. */
public abstract class AbstractVectorCplx implements Vec.Cplx {
    
    final protected int elemCount;
    final protected float [] data;

    /** called before reading for the buffer. Typically,
     * copies data from the accelarator. */
    public abstract void readyBuffer() ;
    
    /** called after writing to the buffer. Typically,
     * copies data back to the accelarator. */
    public abstract void syncBuffer() ;

    /** Return a deep copy/duplicate of this vector. Implementations
     * should typically return their specific type here. */
    public abstract Vec.Cplx duplicate();

    /** Return a new, real-valued vector (typically corresponding
     *  to this type, for Cplx<->Real conversion) */
    protected abstract Vec.Real createAsReal(int s);


    /** Create empty vector */
    protected AbstractVectorCplx(int n) {
	elemCount = n;
	data = new float[ 2*n ];
    }
    
    /** create a vector backed by the data in 'd' */
    protected AbstractVectorCplx(float [] d) {
	if ( d.length % 2 == 1 )
	    throw new RuntimeException("Input array len not even, should be for cplx");
	elemCount = d.length/2;
	data = d;
    }

    // --- creating new vectors ---
    
    /** Return a copy of the real elements in this vector.
     * This uses the default VectorFactory set in Vec. */
    @Override
    public Vec.Real duplicateReal() {
	Vec.Real ret = createAsReal( elemCount );
	ret.copy( this, false );
	return ret;
    }
    
    /** Return a copy of the imaginary elements in this vector.
     * This uses the default VectorFactory set in Vec. */
    @Override
    public Vec.Real duplicateImag() {
	Vec.Real ret = createAsReal( elemCount );
	ret.copy( this, true );
	return ret;
    }

    /** Return a copy, containing the magnitudes of elements in this vector */
    @Override
    public Vec.Real duplicateMagnitude() {
	Vec.Real ret = createAsReal( elemCount );
	ret.copyMagnitude( this );
	return ret;
    }
    
    /** Return a copy, containing the phases of elements in this vector */
    @Override
    public Vec.Real duplicatePhase() {
	Vec.Real ret = createAsReal( elemCount );
	ret.copyPhase( this );
	return ret;
    }
    
    /** Return the n'th element, after syncing buffer.
     *  Implementing classes should probably override this
     *  with a more efficient implementation. */
    @Override
    public Cplx.Float get( int n ) {
        this.readyBuffer();
        return new org.fairsim.linalg.Cplx.Float( data[2*n], data[2*n+1] );
    } 

    /** Set the n'th element, with synced buffer.
     *  Implementing classes should probably override this
     *  with a more efficient implementation. */
    @Override
    public void set( int n, Cplx.Float v ) {
	this.readyBuffer();
	data[2*n+0] = v.re;
	data[2*n+1] = v.im;
	this.syncBuffer();
    } 

    /** Set the n'th element, with synced buffer.
     *  Implementing classes should probably override this
     *  with a more efficient implementation. */
    @Override
    public void set( int n, Cplx.Double v ) {
	this.readyBuffer();
	data[2*n+0] = (float)v.re;
	data[2*n+1] = (float)v.im;
	this.syncBuffer();
    } 
   


    /** Copy the content of 'in' into this vector */
    @Override
    public void copy(Vec.Cplx in) {
	this.readyBuffer();
	Vec.failSize( this, in);
	float [] id = in.vectorData();
	System.arraycopy( id, 0 , data, 0, elemCount*2);
	this.syncBuffer();
    }
    
    /** Copy the content of 'in' into this vector */
    public void copy(Vec.Real in) {
	Vec.failSize( this, in);
	this.readyBuffer();
	float [] id = in.vectorData();
	for (int i=0;i<elemCount; i++ ) {
	    data[i*2] = id[i];
	    data[i*2+1] = 0;
	}
	this.syncBuffer();
    }
    
    // --- arith. functions ---
    
    /** Set all elements to zero */
    public void zero() {
	java.util.Arrays.fill( data, 0, elemCount*2, 0 );
	this.syncBuffer();
    }

    /** Add all input vectors to this vector */
    public void add( Vec.Cplx ... in ) {
	Vec.failSize(this, in);
	this.readyBuffer();
	for (int i=0;i<in.length;i++) {
	    float [] id = in[i].vectorData();
	    for (int j=0;j<elemCount*2;j++)
		data[j] += id[j];
	}
	this.syncBuffer();
    }
    
    /** Computes this += a * x */
    public void axpy( float a , Vec.Cplx x ) {
	Vec.failSize(this, x);
	this.readyBuffer();
	float [] id = x.vectorData();
	
	for (int j=0;j<elemCount*2;j++)
	    data[j] += a * id[j];
	this.syncBuffer();
    }

    /** Computes this += a * x */
    public void axpy( org.fairsim.linalg.Cplx.Float a , Vec.Cplx x ) {
	Vec.failSize(this, x);
	this.readyBuffer();
	float [] id = x.vectorData();
	
	for (int i=0;i<elemCount;i++) {
	    data[Rl(i)] += multReal( a.re, a.im, id[Rl(i)], id[Ig(i)] )  ;
	    data[Ig(i)] += multImag( a.re, a.im, id[Rl(i)], id[Ig(i)] )  ;
	}
	this.syncBuffer();
    }

    /** Set every element to 1/element */
    public void addConst(Cplx.Float a) {
	this.readyBuffer();
	for (int i=0; i<elemCount; i++) {
	    data[ 2*i   ] +=  a.re;
	    data[ 2*i+1 ] +=  a.im;
	}
	this.syncBuffer();
    }

    /** Set every element to 1/element */
    public void reciproc() {
	this.readyBuffer();
	for (int i=0; i<elemCount; i++) {
	    float s = data[2*i]*data[2*i] + data[2*i+1]*data[2*i+1];
	    data[ 2*i   ] =  data[2*i  ] / s;
	    data[ 2*i+1 ] = -data[2*i+1] / s;
	}
	this.syncBuffer();
    }


    /** Multiply by scalar, ie this *= a */
    public void scal( float a ) {
	this.readyBuffer();
	for (int j=0;j<elemCount*2;j++)
	    data[j] *= a;
	this.syncBuffer();
    }
    
    /** Scale by 'in', ie this *= in */
    public void scal(org.fairsim.linalg.Cplx.Float in) {
	float []  y = data;
	this.readyBuffer();
	for (int i=0;i<elemCount;i++) {
	    float y1R = y[Rl(i)], y1I = y[Ig(i)];
	    y[Rl(i)] = multReal( in.re, in.im, y1R, y1I )  ;
	    y[Ig(i)] = multImag( in.re, in.im, y1R, y1I )  ;
	}
	this.syncBuffer();
    }


    /** Return the squared norm <this, this> */
    public double norm2() {
	this.readyBuffer();
	double ret=0;
	for (int i=0;i<elemCount*2;i++) 
	    ret+= data[i] * data[i];
	return ret;
    }

    /** Complex conjugate every element of this vector */
    public void conj() {
	this.readyBuffer();
	for (int i=0;i<elemCount;i++)
	    data[2*i+1]*=-1;
	this.syncBuffer();
    }

    /** Compute the dot product < this^, y > */
    public org.fairsim.linalg.Cplx.Double dot(Vec.Cplx yIn) {
	Vec.failSize( this, yIn );
	this.readyBuffer();
	float [] x = this.data, y = yIn.vectorData();
	double resre=0, resim=0;

	for (int i=0;i<elemCount;i++) {
		resre += multReal( x[ Rl(i) ], -x[ Ig(i) ], y[ Rl(i) ], y[ Ig(i) ] );
		resim += multImag( x[ Rl(i) ], -x[ Ig(i) ], y[ Rl(i) ], y[ Ig(i) ] );
	}

	return new org.fairsim.linalg.Cplx.Double( resre, resim);
    }


    /** Compute element-wise multiplication this = this.*in. 
     *  Same as times(in, false), which gets called by this function. */
    public final void times(Vec.Cplx in) {
	times(in, false);
    }
    
    /** Compute element-wise multiplication this = this.*conj(in). 
     *  Same as times(in, true), which gets called by this function. */
    public final void timesConj(Vec.Cplx in) {
	times(in, true);
    }


    /** Compute element-wise multiplication this = this.*in, 
     *  conjugated 'in' if 'conj' is true */
    public void times(Vec.Cplx in, final boolean conj) {
	
	Vec.failSize( this, in );
	this.readyBuffer();
	float [] x = in.vectorData(), y = data;
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
	this.syncBuffer();
    }
    
    /** Compute element-wise multiplication this = this.*in */ 
    public void times(Vec.Real in) {
	Vec.failSize( this, in );
	this.readyBuffer();

	float [] x = in.vectorData(), y = data;
	for (int i=0;i<elemCount;i++) {
	    y[Rl(i)] *= x[i]; 
	    y[Ig(i)] *= x[i]; 
	}
	this.syncBuffer();
    }


    /** Return the sum of all vector elements */
    public org.fairsim.linalg.Cplx.Double sumElements( ) {
	double re=0,im=0;
	this.readyBuffer();

	for (int i=0;i<elemCount; i++) {
	    re += data[2*i+0];	
	    im += data[2*i+1];	
	}
	return new org.fairsim.linalg.Cplx.Double(re,im);
    }
    
    /** Compute this += x^2 */
    public void addSqr( Vec.Cplx xIn) {
	Vec.failSize( xIn, this);
	this.readyBuffer();

	float [] x = xIn.vectorData(), y = data;
	for (int i=0;i<elemCount;i++) 
	    y[Rl(i)] += x[Rl(i)]*x[Rl(i)]+x[Ig(i)]*x[Ig(i)] ;

	this.syncBuffer();
    }

    /** Access to the internal vector array */
    public float [] vectorData () {
	this.readyBuffer();
	return data;
    }

    /** Vector size */
    public int vectorSize() {
	return elemCount;
    }


    public String first10Elem() {
	this.readyBuffer();
	String ret="";
	for (int i=0; i<10; i++) {
	    ret+=String.format( "[ %8.5e , %8.5e ] ",data[2*i], data[2*i+1]);
	}
	return ret;
    }


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
