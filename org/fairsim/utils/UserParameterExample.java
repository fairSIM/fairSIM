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

import org.fairsim.utils.UserParameter;
import org.fairsim.utils.UserParameterParser;

public class UserParameterExample {
   
    @UserParameter public static int imageSize = 256;

    @UserParameter("Pixel size in um")
    public static double pxlSize = 0.08;
    
    @UserParameter(desc="really needs to be set by the user", mandatory=true)
    public static String outputFileName;

    @UserParameter(desc="scientific notation", scientific=true)
    public static double science = 0.000049233; 

    @UserParameter(desc="more decimal places", decimals=6)
    public static double precision = 1.234567; 
    

    // command line
    public static void main(String [] args) {
	
	UserParameterParser.defaultParserStatic( args, UserParameterExample.class, true);

	// from here on, all fields annotated 'UserParameter' have been updated if
	// the user has provided new parameter, mandatory ones have been checked,
	// a summary is printed, and the program will only reached this point if 
	// no errors have occurred 

    }

}
