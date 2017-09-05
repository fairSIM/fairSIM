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

import java.io.File;

import ij.plugin.PlugIn;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.Prefs;

public class FairSim_ImageJplugin_3d implements PlugIn {
    
    final static String keyLastMachineFile = "org.fairsim.config.lastMachineFile";

    /** Called by Fiji to start the plugin */
    public void run(String inputarg) {

	// create a new machine definition file
	if (inputarg.equals("define-machine-new")) {
	    DefineMachineGui.setupDefineMachineGui();
	    return;
	}
    
	// load a machine definition file
	if (inputarg.equals("define-machine-edit")) {
	    
	    // get the base folder
	    String path = Prefs.get( keyLastMachineFile ,"NONE");
	    File initialFolder = null;
	    if ( !path.equals("NONE")) {
		initialFolder = (new File(path)).getParentFile();
	    }
	    
	    try {
		DefineMachineGui.fromFileChooser(IJ.getInstance(), true, initialFolder);
	    } catch ( Conf.SomeIOException e) {
		Tool.error( "Could not load config\n"+e, false);
	    } catch ( Conf.EntryNotFoundException e ) {
		Tool.error( "Config seems incomplete / broken:\n"+e, false);
	    }
	    return;
	}


	// if we were not started to run a reconstruction, return now
	if ( !( inputarg.equals("reconstruct-new") || inputarg.equals("reconstruct"))) {
	    Tool.error("wrong startup command for plugin",false);
	    return;
	}
	
	// figure out if a machine definition has to be loaded
	boolean loadNewConfig = false;
	if (inputarg.equals("reconstruct-new")) {
	    loadNewConfig = true;
	    Tool.trace("Loading new machine config file");
	}


	Conf cfg = null;
	DefineMachineGui dmg = null;

	// -1-  try to load the last config file
	if (!loadNewConfig) {
	    String path = Prefs.get( keyLastMachineFile ,"NONE");

	    if (path!="NONE") {
		try { 
		    cfg = Conf.loadFile(path);	
		} catch (Conf.SomeIOException e) {
		    loadNewConfig = true;
		} 
	     } else {
		loadNewConfig = true;
	     }

	    if (cfg != null) {
		try {
		    dmg = new DefineMachineGui( cfg.r(), false );
		} catch ( Conf.EntryNotFoundException e ) {
		    loadNewConfig = true;
		}
	    }
	   
	    if (loadNewConfig) {
		Tool.trace("previous config could not be loaded");
	    } else {
		Tool.trace("loaded default config: "+path);
	    }
	}

	// -2- if needed or set, display a file chooser and load the file
	if (loadNewConfig) {
	    
	    // get the base folder
	    String path = Prefs.get( keyLastMachineFile ,"NONE");
	    File initialFolder = null;
	    if ( !path.equals("NONE")) {
		initialFolder = (new File(path)).getParentFile();
	    }
	    
	    // load the machine definition
	    dmg = null;
	    
	    try {
		dmg = DefineMachineGui.fromFileChooser(IJ.getInstance(), false, initialFolder);
	    } catch ( Conf.SomeIOException e) {
		Tool.error( "Could not load config\n"+e, false);
		return;
	    } catch ( Conf.EntryNotFoundException e ) {
		Tool.error( "Config seems incomplete / broken:\n"+e, false);
		return;
	    }

	    if (dmg != null ) {
		Prefs.set(keyLastMachineFile, dmg.getFilename());
		Tool.trace("set new default config: "+dmg.getFilename());
	    }
	}


	// -3- display the image selector window
	ImagePlus ip = IJ.getImage();
	if (ip==null) {
	    Tool.error("No image open/selected!",false);
	    return;
	}
	
	
	ImageOpener is = new ImageOpener();
	ImageSelector.ImageInfo curImg = is.getImageInfoForImagePlus(ip);
	
	if (curImg == null) {
	    Tool.error("Image not found by ImageSelector, this should not happen!",false);
	    return;
	}   
    
	new FairSim3dGUI(dmg, curImg, is);
    
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

	DefineMachineGui dmg = new DefineMachineGui( cfg, false );
	new FairSim3dGUI(dmg, is.getOpenImages()[0], is);

    }

}
	

