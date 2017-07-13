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
public interface ImageStackOutput {

    /** Set the i'th image to display the content of v.
     *  @param v  Vector content to display (implementation must copy content)
     *  @param z  z-Position of image to set
     *  @param ch channel-Position of image to set
     *  @param t  time-Position of image to set
     *	@param label Label of image (may be 'null')
     *	*/
    public abstract void setImage( Vec2d.Real v, int z, int ch, int t, String label );

    /** Set the i'th image to display the content of v.
     *  @param v  Vector content to display (implementation must copy content)
     *  @param ch channel-Position of image to set
     *  @param t  time-Position of image to set
     *	@param label Label of image (may be 'null')
     *	*/
    public abstract void setImage( Vec3d.Real v, int ch, int t, String label );

    /** Called when images have been updated.
     *  Here e.g. disk-writes or refreshing windows should happen. */
    public void update();

}

