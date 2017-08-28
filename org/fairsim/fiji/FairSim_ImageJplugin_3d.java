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

import org.fairsim.utils.Tool;
import org.fairsim.utils.Conf;
import org.fairsim.utils.ImageSelector;


import org.fairsim.utils.ImageDisplay;

import org.fairsim.sim_gui.FairSim3dGUI;
import org.fairsim.sim_gui.DefineMachineGui;


import ij.plugin.PlugIn;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;


public class FairSim_ImageJplugin_3d implements PlugIn {
    
    /** Called by Fiji to start the plugin */
    public void run(String inputarg) {

	if (inputarg.equals("define-machine-new")) {
	    DefineMachineGui.setupDefineMachineGui();
	}
    
	if (inputarg.equals("define-machine-edit")) {
	    try {
		DefineMachineGui.fromFileChooser(IJ.getInstance());
	    } catch ( Conf.SomeIOException e) {
		Tool.error( "Could not load config\n"+e, false);
		return;
	    } catch ( Conf.EntryNotFoundException e ) {
		Tool.error( "Config seems incomplete / broken:\n"+e, false);
		return;
	    }
	}



    }

    public static void main( String [] arg ) 
	throws Conf.SomeIOException, Conf.EntryNotFoundException {
	
	ImageJ ij = new ImageJ(ImageJ.EMBEDDED);
	Tool.trace("-----");
	Tool.trace("Initializing 3D fairSIM");
	Tool.trace("-----");

	if (arg.length == 1 ) {
	   ( new FairSim_ImageJplugin_3d() ).run( arg[0] );
	   return;
	}


	if ( arg.length < 2 ) {
	    Tool.error("Usage: config-file.xml image-file.tif", true);
	    return;
	}

	Conf.Folder cfg = Conf.loadFile( arg[0]).cd("fairsim-3d");

	IJ.open( arg[1] );
	ImageSelector is = new ImageOpener();

	new FairSim3dGUI(cfg, is.getOpenImages()[0], is);

    }

}
	

