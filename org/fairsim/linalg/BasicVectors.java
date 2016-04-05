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
import org.fairsim.utils.Tool;

/** Basic, pure java implementation of the Vector interfaces
 * ({@link Vec}, {@link Vec2d}) */
class BasicVector implements VectorFactory {
    
    static BasicVector instance = null;
    private BasicVector() {};

    /** Get the factory (singleton) for BasicVectors */
    public static BasicVector getFactory() {
	if (instance == null)
	    instance = new BasicVector();
	return instance;
    }

    // ------ Vector factory ------

    @Override
    public BReal createReal(int n) {
	return new BReal(n);
    }
    
    @Override
    public BCplx createCplx(int n) {
	return new BCplx(n);
    }

    @Override
    public BReal2D createReal2D(int w, int h) {
	return new BReal2D(w,h);
    }
    
    @Override
    public BCplx2D createCplx2D(int w, int h) {
	return new BCplx2D(w,h);
    }
    
    @Override
    public BReal3D createReal3D(int w, int h, int d) {
	return new BReal3D(w,h,d);
    }
    
    @Override
    public BCplx3D createCplx3D(int w, int h, int d) {
	return new BCplx3D(w,h,d);
    }

    @Override
    public void syncConcurrent() {};

   
    /** Minimal / basic vector implementation */
    class BReal extends AbstractVectorReal {

	BReal(int l) {
	    super(l);
	}

	@Override
	public BReal duplicate() {
	    BReal ret = new BReal(elemCount);
	    ret.copy(this);
	    return ret;
	}
   
	@Override
	public void syncBuffer() {};
	@Override
	public void readyBuffer() {};
	@Override
	public void makeCoherent() {};

    }
    
    /** Minimal / basic vector implementation */
    class BCplx extends AbstractVectorCplx {

	BCplx(int l) {
	    super(l);
	}

	@Override
	public BCplx duplicate() {
	    BCplx ret = new BCplx(elemCount);
	    ret.copy(this);
	    return ret;
	}
 
	@Override
	public BReal createAsReal(int n) {
	    return new BReal(n);
	}


	@Override
	public void syncBuffer() {};
	@Override
	public void readyBuffer() {};
	@Override
	public void makeCoherent() {};


    }


    /** Minimal 2d vector implementation */
    class BReal2D extends BReal implements Vec2d.Real {
	final int width, height;
	
	BReal2D(int w, int h) {
	    super(w*h);
	    width=w; height=h;
	}

	@Override
	public int vectorWidth() { return width; }
	@Override
	public int vectorHeight() { return height; }

	@Override
	public BReal2D duplicate() {
	    BReal2D ret = new BReal2D(width, height);
	    ret.copy( this );
	    return ret;
	}
	@Override
	public void paste(Vec2d.Real in, int x, int y, boolean zero) {
	    Vec2d.paste( in, this, 0,0, in.vectorWidth(), in.vectorHeight(), x, y, zero);
	}

	@Override
	public float get(int x, int y) {
	    return data[ x + y*width ];
	}
	@Override
	public void set(int x, int y, float a) {
	    data[ x + y*width ] = a;
	}

	@Override
	public void project(Vec3d.Real inV, int start, int end) {
	    
	    final float [] out = this.vectorData();
	    final float [] in  =  inV.vectorData();
	    
	    if (inV.vectorWidth() != width || inV.vectorHeight() != height)
		throw new RuntimeException("Wrong vector size when projecting");
	    
	    if (start<0 || end >= inV.vectorDepth() || start > end )
		throw new RuntimeException("z-index out of vector depth bounds");


	    // TODO: This could be more efficient in the loop??
	    this.zero();

	    for (int z=start; z<=end; z++)
	    for (int y=0; y<height; y++)
	    for (int x=0; x<width; x++) {
		out[y * width + x ] += in[z*width*height + y*width + x];
	    }
	
	}
	
	@Override
	public void project(Vec3d.Real inV) {
	    project( inV, 0, inV.vectorDepth()-1);
	}

	@Override
	public void slice(Vec3d.Real inV, int n) {
	    project( inV, n, n );
	}

    }


    /** Minimal 2d vector implementation */
    class BCplx2D extends BCplx implements Vec2d.Cplx {
	final int width, height;
	
	BCplx2D(int w, int h) {
	    super(w*h);
	    width=w; height=h;
	}
	
	@Override
	public BCplx2D duplicate() {
	    BCplx2D ret = new BCplx2D(width, height);
	    ret.copy( this );
	    return ret;
	}
	
	@Override
	public int vectorWidth() { return width; }
	@Override
	public int vectorHeight() { return height; }

	@Override
	public Cplx.Float get(int x, int y) {
	    return Cplx.Float.at( data, x + y*width );
	}
	@Override
	public void set(int x, int y, Cplx.Float a) {
	    data[ (x + y*width)*2+0 ] = a.re;
	    data[ (x + y*width)*2+1 ] = a.im;
	}
	@Override
	public void set(int x, int y, Cplx.Double a) {
	    data[ (x + y*width)*2+0 ] = (float)a.re;
	    data[ (x + y*width)*2+1 ] = (float)a.im;
	}
	
	@Override
	public void paste(Vec2d.Cplx in, int x, int y, boolean zero) {
	    Vec2d.paste( in, this, 0,0, in.vectorWidth(), in.vectorHeight(), x, y, zero);
	}

    
	@Override
	public void pasteFreq( Vec2d.Cplx inV) {
	    pasteFreq(inV, 0, 0);
	}
	
	@Override
	public void pasteFreq( Vec2d.Cplx inV, int xOffset, int yOffset ) {
	
	    final int wi = inV.vectorWidth();
	    final int hi = inV.vectorHeight();
	    final int wo = this.vectorWidth();
	    final int ho = this.vectorHeight();
	
	    final float [] out = this.vectorData();
	    final float [] in  =  inV.vectorData();
	    
	    //while (xOffset<0) xOffset += wo;
	    //while (yOffset<0) yOffset += ho;

	    this.zero();
	
	    // loop output
	    for (int y= 0;y<hi;y++)
	    for (int x= 0;x<wi;x++) {
		int xo = (x<wi/2)?(x):(x+wo/2);
		int yo = (y<hi/2)?(y):(y+ho/2);
		xo = (xo+xOffset+wo) % wo;
		yo = (yo+yOffset+ho) % ho;
		out[ (xo + (wo*yo))*2+0 ] = in[ (x + wi*y)*2+0];
		out[ (xo + (wo*yo))*2+1 ] = in[ (x + wi*y)*2+1];
	    }
	
	}


	@Override
	public void fft2d(boolean inverse) {
	    Transforms.runfft( this, inverse );
	}
    	
	@Override
	public void fourierShift( final double kx, final double ky ) {

	    final boolean fast = true; // TODO: make this user-settable??
	    
	    final float [] val = vectorData();
	    final int N = Vec2d.checkSquare( this );

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
	
	}
    
	@Override
	public void project(Vec3d.Cplx inV, int start, int end) {
	    
	    final float [] out = this.vectorData();
	    final float [] in  =  inV.vectorData();
	    
	    if (inV.vectorWidth() != width || inV.vectorHeight() != height)
		throw new RuntimeException("Wrong vector size when projecting");
	    
	    if (start<0 || end >= inV.vectorDepth() || start > end )
		throw new RuntimeException("z-index out of vector depth bounds");


	    // TODO: This could be more efficient in the loop??
	    this.zero();

	    for (int z=start; z<=end; z++)
	    for (int y=0; y<height; y++)
	    for (int x=0; x<width; x++) {
		out[(y * width + x)*2+0 ] += in[(z*width*height + y*width + x)*2+0];
		out[(y * width + x)*2+1 ] += in[(z*width*height + y*width + x)*2+0];
	    }
	
	}
	
	@Override
	public void project(Vec3d.Cplx inV) {
	    project( inV, 0, inV.vectorDepth()-1);
	}

	@Override
	public void slice(Vec3d.Cplx inV, int n) {
	    project( inV, n, n );
	}
	

    }



    /** Minimal 2d vector implementation */
    class BReal3D extends BReal implements Vec3d.Real {
	final int width, height, depth;
	
	BReal3D(int w, int h, int d) {
	    super(w*h*d);
	    width=w; height=h; depth=d;
	}

	@Override
	public int vectorWidth() { return width; }
	@Override
	public int vectorHeight() { return height; }
	@Override
	public int vectorDepth() { return depth; }

	@Override
	public BReal3D duplicate() {
	    BReal3D ret = new BReal3D(width, height, depth);
	    ret.copy( this );
	    return ret;
	}

	@Override
	public float get(int x, int y, int z) {
	    return data[ x + y*width + z*width*height];
	}

	@Override
	public void set(int x, int y, int z, float a) {
	    data[ x + y*width + z*width*height ] = a;
	}

	@Override
	public void setSlice( int z, Vec2d.Real vec ) {
	    // TODO: Actually implement this
	    throw new RuntimeException("Not jet implemented");
	}

    }


    /** Minimal 2d vector implementation */
    class BCplx3D extends BCplx implements Vec3d.Cplx {
	final int width, height, depth;
	
	BCplx3D(int w, int h, int d) {
	    super(w*h*d);
	    width=w; height=h; depth=d;
	}
	
	@Override
	public BCplx3D duplicate() {
	    BCplx3D ret = new BCplx3D(width, height, depth);
	    ret.copy( this );
	    return ret;
	}
	
	@Override
	public int vectorWidth() { return width; }
	@Override
	public int vectorHeight() { return height; }
	@Override
	public int vectorDepth() { return depth; }

	@Override
	public Cplx.Float get(int x, int y, int z) {
	    return Cplx.Float.at( data, x + y*width + z*width*height);
	}
	@Override
	public void set(int x, int y, int z, Cplx.Float a) {
	    data[ (x + y*width + z*width*height)*2+0 ] = a.re;
	    data[ (x + y*width + z*width*height)*2+1 ] = a.im;
	}
	@Override
	public void set(int x, int y, int z, Cplx.Double a) {
	    data[ (x + y*width + z*width*height)*2+0 ] = (float)a.re;
	    data[ (x + y*width + z*width*height)*2+1 ] = (float)a.im;
	}
	
	@Override
	public void fft3d(boolean inverse) {
	    Transforms.runfft3d( this, inverse );
	}
	
	@Override
	public void setSlice( final int z, Vec2d.Cplx vec ) {
	    if (( vec.vectorWidth() != width ) ||
		( vec.vectorHeight() != height ) ||
		z<0 || z>= depth )
		throw new RuntimeException("Index mismatch");

	    float [] in = vec.vectorData();

	    for (int y=0; y<height; y++)
	    for (int x=0; x<width; x++) {
		data[ 2*( x + width*y + z*width*height )+0 ] = in[ 2*( x + width*y )+0 ];
		data[ 2*( x + width*y + z*width*height )+1 ] = in[ 2*( x + width*y )+1 ];
	    }
	
	}
	
	@Override
	public void setSlice( int z, Vec2d.Real vec ) {
	    if (( vec.vectorWidth() != width ) ||
		( vec.vectorHeight() != height ) ||
		z<0 || z>= depth )
		throw new RuntimeException("Index mismatch");

	    float [] in = vec.vectorData();

	    for (int y=0; y<height; y++)
	    for (int x=0; x<width; x++) {
		data[ 2*( x + width*y + z*width*height )+0 ] = in[ x + width*y ];
		data[ 2*( x + width*y + z*width*height )+1 ] = 0;
	    }
	
	}

	/** Look into source code for usage */
	@Override
	public void pasteFreq( Vec3d.Cplx inV) {

	    // TODO: This currently only works for the very specific case of
	    // the output vector size being 2*w, 2*h, z (as typ. in SIM)

	    final int wi = inV.vectorWidth();
	    final int hi = inV.vectorHeight();
	    final int ti = inV.vectorDepth();
	    final int wo = this.vectorWidth();
	    final int ho = this.vectorHeight();
	    final int to = this.vectorDepth();
	
	    final float [] out = this.vectorData();
	    final float [] in  =  inV.vectorData();
	
	    this.zero();
	
	    // loop output
	    for (int z= 0;z<ti;z++)
	    for (int y= 0;y<hi;y++)
	    for (int x= 0;x<wi;x++) {
		int xo = (x<wi/2)?(x):(x+wo/2);
		int yo = (y<hi/2)?(y):(y+ho/2);
		int zo = (z<ti/2)?(z):(z+to/2);
		//out[ (xo + wo*yo + wo*ho*zo)*2+0 ] = in[ (x + wi*y + wi*hi*z)*2+0];
		//out[ (xo + wo*yo + wo*ho*zo)*2+1 ] = in[ (x + wi*y + wi*hi*z)*2+1];
		out[ (xo + wo*yo + wo*ho*z)*2+0 ] = in[ (x + wi*y + wi*hi*z)*2+0];
		out[ (xo + wo*yo + wo*ho*z)*2+1 ] = in[ (x + wi*y + wi*hi*z)*2+1];
	    }
	
	}

	@Override
	public void fourierShift(
	    final double kx, final double ky, final double kz ) {
	   
	    final boolean fast = true; //TODO: user-settable??, see above
	    final float [] val = vectorData();
	    final int w = vectorWidth();
	    final int h = vectorHeight();
	    final int d = vectorDepth();

	    // run outer loop in parallel
	    new SimpleMT.PFor(0,d) {
		public void at(int z) {
		    for (int y=0; y<h; y++)
		    for (int x=0; x<w; x++) {
			float phaVal = (float)(2*Math.PI*(
			    ((float)x*kx)/w+((float)y*ky)/h+((float)z*kz)/d));
			float si,co;
			if (fast) {
			    co = (float)MTool.fcos( phaVal );
			    si = (float)MTool.fsin( phaVal );
			} else {
			    co = (float)Math.cos( phaVal );
			    si = (float)Math.sin( phaVal );
			}
			// get
			float re = val[ (z*w*h+y*w+x)*2+0 ] ;
			float im = val[ (z*w*h+y*w+x)*2+1 ] ;
			// set
			val[ (z*w*h+y*w+x)*2+0 ] = Cplx.multReal( re, im, co, si );
			val[ (z*w*h+y*w+x)*2+1 ] = Cplx.multImag( re, im, co, si );
		    }
		}
	    };
	
	}

		
    }



}
