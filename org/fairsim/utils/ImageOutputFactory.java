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



/** Creation of ImageDisplays */
public interface ImageOutputFactory {
	/** Create a new single-slices ImageDiplay.
	 *  @param w Width of the display
	 *  @param h Height of the display 
	 *  @param t Title of the display */
	public ImageDisplay create(int w, int h, String t);
	
	/** Create a new 5D ImageStackDisplay 
	 *  @param w Width of the display
	 *  @param h Height of the display 
	 *  @param d Depth (z) of the display
	 *  @param c Nr of color channels of the display 
	 *  @param t Number of timePoints
	 *  @param s Titl of the display */
	public ImageStackOutput create(int w, int h, int d, int c, int t, String s); 

}


