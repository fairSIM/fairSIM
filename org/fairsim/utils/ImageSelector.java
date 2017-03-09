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

package org.fairsim.utils;


import org.fairsim.linalg.Vec2d;

/** Access to list of open images */
public interface ImageSelector {

    /** Information returned about an open image */
    public class ImageInfo {
	/** Image width and height (pxl) */
	public final int width, height;
	/** Depth / length of stack */
	public final int depth;
	/** Size of pxl in microns (or -1 if not available) */
	public final double micronsPerPxl;
	/** "Name" of the image (or null if not available) */
	public final String name;
	/** ID, free to be set by implementors of ImageInfo */
	public int id;
		
	public ImageInfo( int width, int height, int depth,
	    double micronsPerPxl, String name, int id ) {
	    this.width		= width;
	    this.height		= height;
	    this.depth		= depth;
	    this.micronsPerPxl	= micronsPerPxl;
	    this.name		= name;
	    this.id		= id;
	}

	@Override
	public String toString() { return name; }

	/** Compares images on size and id only.
	 *  ImageInfo's are defined to be the same for
	 *  width, height, depth and id being equal, while
	 *  name and micronsPerPixel may differ */
	@Override
	public boolean equals( Object e ) {
	    // other types (and null): false
	    if (!(e instanceof ImageInfo ))
		return false;
	    // compare w,h,d,id
	    ImageInfo other = (ImageInfo)e;

	    if (    this.width  == other.width
		&&  this.height == other.height
		&&  this.depth	== other.depth
		&&  this.id	== other.id )
	    return true;
	    return false;
	}

    }


    /* see fiji/ImageOpener, currently, the callback-idea
     * to update the list seems to cause the annoying
     * deadlocks in the code... reverting to manual
     * updates of the image list */
    /*
    // Nofication that available images changed 
    public interface Callback {
	// gets called on change 
	public void call();
    }
    // register a callback 
    public void addCallback( Callback b );
    // de-register a callback 
    public void removeCallback( Callback b );
    */

    /** Returns the number of open images */
    public int getOpenImageCount();    
    
    /** Get list of open images */
    public ImageInfo [] getOpenImages();

    /** Returns the image referred to by an ImageInfo, at position pos
     * (or null if not found) */
    public Vec2d.Real getImage( ImageInfo which, int pos );
    
    /** Returns the image stack referred to by an ImageInfo, 
     * (or null if not found) */
    public Vec2d.Real [] getImages( ImageInfo which );


    /** Dummy implementation for testing (GUI) */
    public class Dummy implements ImageSelector {
	final int n;
	public Dummy(int i) { n=i; }
	final String [] names = { "Test Image 0", "Test Image 1",
	    "Some other image (w/o calibration)", 
	    "Very long image name just to test if GUI is wide enough",
	    "Image with 45 planes"};

	
	@Override
	public int getOpenImageCount() { return n; }
	@Override
	public ImageInfo [] getOpenImages() {
	    ImageInfo [] ret = new ImageInfo[ n ];
	    for (int i=0; i<n; i++)
		ret[i] = new ImageInfo(512, 512, idxToZ(i),
		    ((i!=2)?(0.082):(-1)),
		    names[i%names.length], i );
	    return ret;
	}
	@Override
	public Vec2d.Real getImage(ImageInfo w, int pos) {
	    return Vec2d.createReal( 512, 512 );
	}
	@Override
	public Vec2d.Real [] getImages(ImageInfo w) {
	    return Vec2d.createArrayReal( idxToZ(w.id) , 512, 512 );
	}

    
	private int idxToZ(int i) {
	    return (i!=1)?(15*(i+1)):(9*(i+1));
	}

    }




}
