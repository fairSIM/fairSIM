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


/** Default implementation of {@link Vec.Real}, 
 *  backed by a float [].
 *  By implementing readyBuffer, syncBuffer, performance-critial
 *  functions can be moved to the accelarator step-by-step. */
public abstract class AbstractVectorReal implements  Vec.Real {

    /** Elements in buffer */
    final protected int elemCount;
    
    /** CPU / JAVA side data buffer */
    final protected float [] data;
    
    /** Creates empty vector */
    protected AbstractVectorReal(int n) {
	elemCount = n;
	data = new float[ n ];
    }

    /** create a vector backed by the data in 'd' */
    protected AbstractVectorReal(float [] d) {
	elemCount = d.length;
	data = d;
    }

    /** Called before reading from the 'data' buffer.
     *  Typical use it copying data from the accelarator
     *  if the buffer it out of date.*/
    public abstract void readyBuffer();

    /** Called after writing to the 'data' buffer.
     *  Typical use is updating the accelarators copy */
    public abstract void syncBuffer();
    
    /** Return a copy/duplicate of this vector */
    @Override
    public abstract Vec.Real duplicate();

    /** Return the number of elements in this vector */
    @Override
    public final int vectorSize() {
	return elemCount;
    }

    
    /** Return the n'th element, after syncing buffer.
     *  Implementing classes should probably override this
     *  with a more efficient implementation. */
    public float get( int n ) 
    {
	this.readyBuffer();
	return data[n];
    } 

    /** Set the n'th element, with synced buffer.
     *  Implementing classes should probably override this
     *  with a more efficient implementation. */
    public void set( int n, float x ) 
    {
	this.readyBuffer();
	data[n] = x ;
	this.syncBuffer();
    } 


    // --- copy functions ---

    /** Copy the content of 'in' into this vector */
    @Override
    public void copy(Vec.Real in) {
	Vec.failSize( this, in);
	float [] id = in.vectorData();
	System.arraycopy( id, 0 , data, 0, elemCount);
	this.syncBuffer();
    }
    
    /** Copy the real/imag values of 'in' into this vector */
    @Override
    public void copy(Vec.Cplx in, boolean imag) {
	Vec.failSize( in, this);
	float [] id = in.vectorData();
	
	for (int i=0;i<elemCount;i++)
	    data[i] = id[2*i + ((imag)?(1):(0)) ];
	this.syncBuffer();
    }
    
    /** Copy the real values of 'in' into this vector */
    @Override
    public final void copy(Vec.Cplx in) {
	copy(in, false);
    }


    /** Copy the magnitudes of elements in 'in' into this vector */
    @Override
    public void copyMagnitude(Vec.Cplx in) {
	Vec.failSize( in, this);
	float [] id = in.vectorData();
	
	for (int i=0;i<elemCount;i++)
	    data[i] = (float)MTool.fhypot(id[2*i], id[2*i+1]);
	this.syncBuffer();
    }
    
    /** Copy the phases of elements in 'in' into this vector */
    @Override
    public void copyPhase(Vec.Cplx in) {
	Vec.failSize( in, this);
	float [] id = in.vectorData();
	
	for (int i=0;i<elemCount;i++)
	    data[i] = (float)Math.atan2(id[2*i+1], id[2*i]);
	this.syncBuffer();
    }

    
    /** Set all elements to zero */
    @Override
    public void zero() {
	java.util.Arrays.fill( data, 0, elemCount, 0 );
	this.syncBuffer();
    }

    /** Add all input vectors to this vector */
    @Override
    public void add( Vec.Real ... in ) {
	Vec.failSize(this, in);
	this.readyBuffer();
	
	for (int i=0;i<in.length;i++) {
	    float [] id = in[i].vectorData();
	    for (int j=0;j<elemCount;j++)
		data[j] += id[j];
	}
	this.syncBuffer();
    }
    
    /** Computes this += a * x */
    @Override
    public void axpy( float a , Vec.Real x ) {
	Vec.failSize(this, x);
	this.readyBuffer();
	float [] id = x.vectorData();
	
	for (int j=0;j<elemCount;j++)
	    data[j] += a * id[j];
	this.syncBuffer();
    }

    /** Multiply by scalar, ie this *= a */
    @Override
    public void scal( float a ) {
	this.readyBuffer();
	for (int j=0;j<elemCount;j++)
	    data[j] *= a;
	this.syncBuffer();
    }

    /** Return the dot product < this, x > */
    @Override
    public double dot(Vec.Real x) {
	Vec.failSize( this, x);
	this.readyBuffer();
	float [] id = x.vectorData();
	double ret=0;
	for (int i=0;i<elemCount;i++) 
	    ret+= id[i] * data[i];
	return ret;
    }

    /** Return the squared norm <this, this> */
    @Override
    public double norm2() {
	double ret=0;
	this.readyBuffer();
	for (int i=0;i<elemCount;i++) 
	    ret+= data[i] * data[i];
	return ret;
    }
    
    /** Compute the elemnt-wise multiplication this = this.*x */
    @Override
    public void times(Vec.Real x) {
	Vec.failSize(this, x);
	this.readyBuffer();
	float [] id = x.vectorData();
	for (int i=0;i<elemCount;i++) 
	    data[i] = data[i] * id[i];
	this.syncBuffer();
    }

    /** Return the sum of all vector elements */
    @Override
    public double sumElements() {
	double ret=0;
	this.readyBuffer();
	for (int i=0;i<elemCount; i++) {
	    ret += data[i];	
	}
	return ret;
    }
    
    /** Normalize the vector to 0..1 */
    @Override
    public void normalize(){
	this.readyBuffer();
	// calculate min and max for scaling
	float min = Float.MAX_VALUE;
	float max = Float.MIN_VALUE;
	for ( float i : data ) {
	    if (i<min) min=i;
	    if (i>max) max=i;
	}
	for (int i=0;i<data.length;i++)
	    data[i]=(data[i]-min)/(max-min);
	this.syncBuffer();
    }
    
    /** Normalize the vector to vmin..vmax */
    @Override
    public void normalize(float vmin, float vmax){
	this.readyBuffer();
	// calculate min and max for scaling
	float min = Float.MAX_VALUE;
	float max = Float.MIN_VALUE;
	for ( float i : data ) {
	    if (i<min) min=i;
	    if (i>max) max=i;
	}
	for (int i=0;i<data.length;i++)
	    data[i]=vmin+ (((data[i]-min)/(max-min))*(vmax-vmin));
	this.syncBuffer();
    }

    /** Set every element to 1/element */
    @Override
    public void reciproc() {
	this.readyBuffer();
	for (int i=0; i<elemCount; i++)
	    data[i] = 1.f/data[i];
	this.syncBuffer();
    }


    /** Compute y = y + x^2 */
    @Override
    public void addSqr( Vec.Real xIn ) {
	Vec.failSize(this, xIn);
	
	this.readyBuffer();
	float [] x = xIn.vectorData(), y = data;
	for (int i=0;i<elemCount;i++) 
	    y[i] += x[i]*x[i] ; 
	
	this.syncBuffer();
    }


    /** Access to the internal vector array (mind the
     *  'syncBuffer' calls). Issues a {@link #readyBuffer} call
     *  and then returns the vectors CPU-side data buffer. If the
     *  buffer is modified, it has to be sync'ed back by calling
     *  {@link #syncBuffer}. */
    @Override
    public float [] vectorData () {
	this.readyBuffer();
	return data;
    }


    public String first10Elem() {
	this.readyBuffer();
	String ret="";
	for (int i=0; i<10; i++) {
	    ret+=String.format( "[ %8.5e ] ",data[i]);
	}
	return ret;
    }


}


    

