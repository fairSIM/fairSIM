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
	
	    final int wi = inV.vectorWidth();
	    final int hi = inV.vectorHeight();
	    final int wo = this.vectorWidth();
	    final int ho = this.vectorHeight();
	
	    final float [] out = this.vectorData();
	    final float [] in  =  inV.vectorData();
	
	    this.zero();
	
	    // loop output
	    for (int y= 0;y<hi;y++)
	    for (int x= 0;x<wi;x++) {
		int xo = (x<wi/2)?(x):(x+wo/2);
		int yo = (y<hi/2)?(y):(y+ho/2);
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
	    // TODO: copy the implementation over to here
	    Transforms.runTimesShiftVector( this, kx, ky, true);
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

	@Override
	public Vec2d.Real project() {
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
	public void setSlice( int z, Vec2d.Cplx vec ) {
	    // TODO: Actually implement this
	    throw new RuntimeException("Not jet implemented");
	}

	@Override
	public Vec2d.Cplx project() {
	    // TODO: Actually implement this
	    throw new RuntimeException("Not jet implemented");
	}

	
    }



}
