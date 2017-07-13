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

import org.fairsim.utils.ImageStackOutput;
import org.fairsim.utils.Tool;

import org.fairsim.linalg.Vec2d;
import org.fairsim.linalg.Vec3d;


public class DisplayWrapper5D implements ImageStackOutput {


    final int width,height,depth,channels,timepoints;

    DisplayWrapper5D( int w, int h, int d, int c, int t, String title ) {
	
	if ( w<1 || h <1 || d<1 || c<1 || t < 1 ) {
	    throw new IndexOutOfBoundsException("size should not be < 1");
	}

	width  = w;
	height = h;
	depth  = d;
	channels = c;
	timepoints = t;
    }



    @Override
    public void setImage( Vec2d.Real img, int z, int c, int t, String title ) {

    }
    
    @Override
    public void setImage( Vec3d.Real img, int c, int t , String title ) {

    }


    @Override
    public void update() {

    }

}
