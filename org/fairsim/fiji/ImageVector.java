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

package org.fairsim.fiji;

import org.fairsim.linalg.Vec2d;
import org.fairsim.linalg.Vec3d;
import org.fairsim.linalg.AbstractVectorReal;

import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

/**
 * An extension of {@link org.fairsim.linalg.Vec2d.Real},
 * backed by an ImageJ FloatProcessor.
 */
public class ImageVector extends AbstractVectorReal implements Vec2d.Real {

	private final FloatProcessor fp;
	private final int width, height;

	@Override
	public void syncBuffer() {};
	@Override
	public void readyBuffer() {};
	@Override
	public void makeCoherent() {};
	
	
	@Override
	public ImageVector duplicate() {
	    ImageVector ret  = ImageVector.create(width, height);
	    ret.copy(this);
	    return ret;
	
	}

	@Override
	public int vectorWidth() { return width; }
	@Override
	public int vectorHeight() { return height; }

	@Override
	public void  paste( Vec2d.Real in , int x, int y, boolean zero) {
	    Vec2d.paste( in, this, 0, 0, in.vectorWidth(), in.vectorHeight(), x,y, zero);
	}

	@Override
	public void set(int x, int y, float a) {
	    data[x+y*width] = a;
	}

	@Override
	public float get(int x, int y) {
	    return data[x+y*width];
	}

	/** Internal constructor, use factory methods instead */
	private ImageVector( int w, int h, FloatProcessor f ) {
	    super( (float [])f.getPixels() ); 
	    fp=f; width=w;  height=h;
	}

	/** Create a new ImageVector size w x h */
	public static ImageVector create(int w, int h) {
	    FloatProcessor fp = new FloatProcessor( w,h );
	    return new ImageVector( w, h, fp);
	}

	/** Wrap an existing FloatProcessor into a vector object */
	public static ImageVector wrap( FloatProcessor in ) {
	    return new ImageVector( in.getWidth(), in.getHeight(), in); 
	}


	/** Create an ImageVector, initialized to size and image in 'in' */
	public static ImageVector copy( ImageProcessor in ) {
	    int w = in.getWidth(), h = in.getHeight();
	    ImageVector ret = create(w,h);
	    for (int y=0; y<h; y++)
		for (int x=0; x<w; x++)
		    ret.set( x, y, in.getf(x,y));
	    return ret;
	}

	/** Return the FloatProcessor linked to this vector */
	public FloatProcessor img() {
	    return fp;
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
