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

package org.fairsim.fiji;

import org.fairsim.linalg.Vec2d;

import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

/**
 * An extension of {@link org.fairsim.linalg.Vec2d.Real},
 * backed by an ImageJ FloatProcessor.
 */
public class ImageVector extends Vec2d.Real {

	private final FloatProcessor fp;

	/** Internal constructor, use factory methods instead */
	private ImageVector( int w, int h, FloatProcessor f ) {
	    super(w,h, (float [])f.getPixels() ); fp=f;
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

}
