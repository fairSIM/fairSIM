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
import org.fairsim.linalg.Vec3d;

/** Interface defining mechanisms to 
 *  display images to the user. */
public interface ImageDisplay {

    /** Set the i'th image to display the content of v.
     *  @param v Vector content to display (implementation must copy content)
     *  @param i Position of image to set
     *	@param label Label of image (may be 'null')
     *	@param m Markers to draw on the image (see {@link ImageDisplay.Marker}
     *	*/
    public abstract void setImage( Vec2d.Real v, int i, String label, Marker ... m); 

    /** Add an image to the end of the stack. 
     *  @param v Vector content to display (implementation must copy content)
     *	@param label Label of image (may be 'null')
     *	@param m Markers to draw on the image (see {@link ImageDisplay.Marker}
     *	*/
    public abstract void addImage( Vec2d.Real v, String label, Marker ... m );
    
    /** TODO: comment this */
    public void addImage( Vec3d.Real v, boolean project, String label, ImageDisplay.Marker ... m) ;

    /** Set that the display should be visible. Implementations (e.g.
     *  output to disk) may ignore this flag. */
    public void display();

    /** Called to tell a display it is not longer needed. Thus, it should close
     * and can drop all references, data etc. */
    public void drop();

    /** Returns the number of images that have been added
     *  to the display. */
    public int getCount();

    /** Register a listener */
    public void addListener( Notify n ); 
	    
    /** De-register a listener */
    public void removeListener( Notify n ); 

    /** Get image width */
    public int width();

    /** Get image height */
    public int height();

    // ------------------------------------------------------------------------


    /** Marker to draw onto an image */
    final class Marker {
	/** Coordinates x-pos, y-pos, width, height */
	public final double x,y,w,h;
	/** Draw an elipse instead of a rectangle */
	public final boolean drawElipse;
	
	/** Create a square marker
	 * @param x x-position center
	 * @param y y-position center
	 * @param w witdh 
	 * @param h height 
	 * @param elipse If true, draw a elipse instead of a rectangle */
	public Marker(double x, double y, double w, double h, boolean elipse) {
	    this.x=x; this.y=y; this.w=w; this.h=h; this.drawElipse = elipse;
	}
    }
    
    /** Position change in an ImageDisplay */
    public interface Notify {
	/** Displayed image changed position */
	public void newPosition( ImageDisplay e, int p );
    }


    /** Creation of ImageDisplays */
    public interface Factory {
	/** Create a new ImageDiplay.
	 *  @param w Width of the display
	 *  @param h Height of the display 
	 *  @param t Title of the display */
	public ImageDisplay create(int w, int h, String t);

    }


}

