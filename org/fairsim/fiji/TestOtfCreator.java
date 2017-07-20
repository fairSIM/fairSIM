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

import org.fairsim.linalg.*;
import org.fairsim.fiji.ImageVector;
import org.fairsim.fiji.DisplayWrapper;
import org.fairsim.utils.Tool;
import org.fairsim.utils.Conf;
import org.fairsim.utils.ImageDisplay;
import org.fairsim.sim_algorithm.*;

import ij.plugin.PlugIn;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ij.process.FloatProcessor;

import ij.gui.GenericDialog;
import ij.gui.OvalRoi;
import ij.gui.Overlay;

/** Extracts OTFs from bead measurements */
public class TestOtfCreator implements PlugIn {

    /** Global variables */
    int nrBands  = 3;		    // #bands (2 - two-beam, 3 - three-beam, ...)
    int nrDirs   = 3;		    // #angles or pattern orientations
    int nrPhases = 5;		    // #phases (at least 2*bands -1 )

    double emWavelen = 560;	    // emission wavelength		    
    double otfNA     = 1.4;	    // NA of objective

    
    

    /** Called by Fiji to start the plugin. 
     *	Uses the currently selected image, does some basic checks
     *	concerning image size.
     * */
    public void run(String arg) {
	// currently selected stack, some basic checks
	ImageStack inSt = ij.WindowManager.getCurrentImage().getStack();
	final int w=inSt.getWidth(), h=inSt.getHeight();
	if (w!=h) {
	    IJ.showMessage("Image not square (w!=h)");
	    return;
	}
	if (inSt.getSize() != nrPhases*nrDirs ) {
	    IJ.showMessage("Stack length != phases*angles: "+inSt.getSize() );
	    return;
	}
	
    }
   




    /** Start from the command line to run the plugin */
    public static void main( String [] arg ) {

	if (arg.length<1) {
	    System.out.println("TIFF-file");
	    return;
	}
	
	boolean set=false;
  
	new ij.ImageJ( ij.ImageJ.EMBEDDED );


	//SimpleMT.useParallel( false );
	ImagePlus ip = IJ.openImage(arg[0]);
	ip.show();

	// for now, assume 3 angles, 5 phases
	final int nrAngles = 1;
	final int nrPhases = 5;


	ImageStack is = ip.getStack() ;
	final int depth = is.getSize()/nrAngles/nrPhases;	
	
	Vec3d.Cplx [][] imgs = new Vec3d.Cplx[nrAngles][nrPhases];


	// copy slices
	for (int a=0; a<nrAngles; a++) {
	    for (int p=0; p<nrPhases; p++) {
		
		imgs[a][p] = Vec3d.createCplx( is.getWidth(), is.getHeight(), depth );
		
		for (int z=0; z< depth; z++) {
	   
		    int pos = p + z*nrPhases + a*nrPhases*depth + 1;

		    ImageProcessor curimg = is.getProcessor(pos);
		    ImageVector iv = ImageVector.copy( curimg );
		    imgs[a][p].setSlice( z, iv );
		}
	    }
	}

	// start the OtfCreation
	OtfCreator otfCr = new OtfCreator();	
	Vec2d.Cplx otf = otfCr.createOtf( imgs );
	


    }


  





}


