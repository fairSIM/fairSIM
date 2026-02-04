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

import org.fairsim.sim_algorithm.OtfProvider3D;
import org.fairsim.utils.Conf;
import org.fairsim.linalg.Vec3d;
import org.fairsim.linalg.Transforms;

public class DisplayOtf3D {
    
    public static void main( String[] args ) 
        throws Conf.SomeIOException, Conf.EntryNotFoundException {
        
        if ( args.length < 1 ) {
            System.out.println( "Usage: DisplayOtf3D <config file>" );
            return;
        }
        
        // Load the OTF from the config file
        // and display it in a new window
    
        Conf cfg = Conf.loadFile( args[0] );
        OtfProvider3D otf = OtfProvider3D.loadFromConfig( cfg ); 
        otf.setPixelSize(1./(512*0.08), 1./(16*0.125));
        

        Vec3d.Cplx otfVec[] = Vec3d.createArrayCplx( 3,512,512,16);
        Vec3d.Real otfAbsVec[] = Vec3d.createArrayReal( 3,512,512,16);

	    new ij.ImageJ( ij.ImageJ.EMBEDDED );
        DisplayWrapper5D display = new DisplayWrapper5D( 512,512,16,1,3, "OTF test");

        for (int i=0; i<3; i++) {
            if (i<2)
            otf.writeOtfVector( otfVec[i],i, 0,0);
            else
            otf.writeOtfVector( otfVec[i],i, 140,140);
            otfAbsVec[i].copyMagnitude(otfVec[i]);
            Transforms.swapQuadrant(otfAbsVec[i]);
            display.setImage( otfAbsVec[i],  0, i, "Band "+i);
        }

        display.update();

    }
}
