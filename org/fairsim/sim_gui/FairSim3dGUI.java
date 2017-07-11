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

package org.fairsim.sim_gui;


import org.fairsim.sim_algorithm.SimParam;
import org.fairsim.sim_algorithm.OtfProvider;
import org.fairsim.utils.ImageSelector;
import org.fairsim.utils.ImageDisplay;
import org.fairsim.utils.Tool;
import org.fairsim.utils.Conf;

import java.awt.Frame;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JFileChooser;
//import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.JDialog;
import javax.swing.JButton;
import javax.swing.JLabel;

import java.net.URL;

import javax.swing.ImageIcon;
import javax.swing.Box;
import javax.swing.BoxLayout;
import java.awt.GridLayout;
import java.awt.Dimension;
import javax.swing.BorderFactory;

// only for testing, remove..
import org.fairsim.fiji.DisplayWrapper;

/** A one-frame GUI for quick, largely
 *  automated reconstruction */
public class FairSim3dGUI {

    final JFrame baseframe = new JFrame("fairSIM 3D GUI");
    final private JPanel mainPanel = new JPanel();

    final DefineMaschineGui dmg;

    final int imgPerZ;



    public FairSim3dGUI( Conf.Folder cfg, ImageSelector.ImageInfo imgs ) 
	throws Conf.EntryNotFoundException, Conf.SomeIOException {

	baseframe.setLocation(100,100);

	dmg = new DefineMaschineGui( cfg, false );
	Tool.trace("loaded maschine definition: \""+ dmg.confName +"\"");

	imgPerZ = dmg.channels.get(0).sp.getImgPerZ();

	if ( imgs.nrSlices % imgPerZ != 0 ) {
	    Tool.error(" Image length not a multiple of raw images per z-plane",true);
	    return;
	    // TODO: throw a proper exception here
	}

	// 1 -- position selector
	JPanel posSelector = new JPanel();
	posSelector.setBorder(BorderFactory.createTitledBorder("Name and wavelegth") );


	Tiles.LNSpinner zBottom = new Tiles.LNSpinner("z bottom", 1, 1, imgs.nrSlices/imgPerZ, 1);
	Tiles.LNSpinner zTop = new Tiles.LNSpinner("z top", imgs.nrSlices/imgPerZ, 0, imgs.nrSlices/imgPerZ, 1);
	
	Tiles.LNSpinner tStart = new Tiles.LNSpinner("t start", 1, 1, imgs.nrTimepoints, 1);
	Tiles.LNSpinner tEnd = new Tiles.LNSpinner("t end", imgs.nrTimepoints, 1, imgs.nrTimepoints, 1);

	posSelector.add( zBottom );
	posSelector.add( zTop );
	posSelector.add( tStart );
	posSelector.add( tEnd );


	// 2 -- channel-specific settings





	mainPanel.setLayout( new BoxLayout( mainPanel, BoxLayout.PAGE_AXIS));
	mainPanel.add( posSelector );


	baseframe.add( mainPanel );
	baseframe.pack();
	baseframe.setVisible(true);




    }


    class ChannelPanel {

	JPanel ourPanel = new JPanel();


    }




    // test function
    public static void main( String [] arg )
	throws Conf.EntryNotFoundException, Conf.SomeIOException {
	    Tool.error("For testing, run org.fairsim.fiji.FairSim_ImageJplugin_3d", false);
    }



}



