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

import org.fairsim.sim_gui.PlainImageDisplay;

import ij.IJ;
import ij.plugin.PlugIn;
import ij.ImagePlus;

import javax.swing.JFrame;

/** Small Fiji plugin, running all parameter estimation and reconstruction
 *  steps. Good starting point to look at the code w/o going through all the
 *  GUI components. */
public class TestImageDisplay implements PlugIn {


    public void run(String arg) {

	ImageVector iv = ImageVector.copy( IJ.getProcessor() );

	JFrame mainFrame = new JFrame("PlainImageDisplay");
	PlainImageDisplay pd = new PlainImageDisplay( 1, iv.vectorWidth(), iv.vectorHeight() );
	
	mainFrame.add( pd.getPanel());
	mainFrame.pack();
	mainFrame.setVisible( true );
	
	pd.newImage( 0, iv);
	pd.refresh();

	
    }


    /** Start from the command line to run the plugin */
    public static void main( String [] arg ) {

	if (arg.length<1) {
	    System.out.println("TIFF-file");
	    return;
	}
	
	boolean set=false;
  
	new ij.ImageJ( ij.ImageJ.EMBEDDED );
	ImagePlus ip = IJ.openImage(arg[0]);
    
	ImageVector iv = ImageVector.copy( ip.getProcessor() );
	

	JFrame mainFrame = new JFrame("PlainImageDisplay");
	PlainImageDisplay pd = new PlainImageDisplay( 1, iv.vectorWidth(), iv.vectorHeight() );
	
	mainFrame.add( pd.getPanel());
	mainFrame.pack();
	mainFrame.setVisible( true );
	
	pd.newImage( 0, iv);
	pd.refresh();

	

    }


  





}


