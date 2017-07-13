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
import org.fairsim.sim_algorithm.OtfProvider3D;
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
import javax.swing.JTabbedPane;

import java.net.URL;

import javax.swing.ImageIcon;
import javax.swing.Box;
import javax.swing.BoxLayout;
import java.awt.GridLayout;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;

import java.util.List;
import java.util.ArrayList;

// only for testing, remove..
import org.fairsim.fiji.DisplayWrapper;

/** A one-frame GUI for quick, largely
 *  automated reconstruction */
public class FairSim3dGUI {

    final JFrame baseframe = new JFrame("fairSIM 3D GUI");
    final private JPanel mainPanel = new JPanel();

    final DefineMaschineGui dmg;

    final int imgPerZ;

    final ImageSelector.ImageInfo ourRawImages ;
    final JCheckBox propToOtherCh = new JCheckBox("propagate changes to other channels");
    
    List<ChannelPanel> channelPanelList = new ArrayList<ChannelPanel>();

    enum FITTYPES {
	FULL("full parameter search"),
	REFINE("refine k0 and phase"),
	PHASEONLY("fit only phase"),
	RECONSTONLY("use preset values");

	final String ourName;
	
	FITTYPES(String name) {
	    ourName=name;
	}

	@Override 
	public String toString() {
	    return ourName;
	}


    }



    public FairSim3dGUI( Conf.Folder cfg, ImageSelector.ImageInfo imgs ) 
	throws Conf.EntryNotFoundException, Conf.SomeIOException {

	baseframe.setLocation(100,100);

	dmg = new DefineMaschineGui( cfg, false );
	Tool.trace("loaded maschine definition: \""+ dmg.confName +"\"");

	imgPerZ = dmg.channels.get(0).sp.getImgPerZ();
	ourRawImages = imgs;

	if ( imgs.nrSlices % imgPerZ != 0 ) {
	    Tool.error(" Image length not a multiple of raw images per z-plane",true);
	    return;
	    // TODO: throw a proper exception here
	}

	
	// Channel-specific settings
	for ( int ch =0 ; ch<imgs.nrChannels; ch++) {
	    ChannelPanel chPnl = new ChannelPanel(ch);
	    channelPanelList.add( chPnl );
	    mainPanel.add( chPnl.ourPanel );
	}


	// 3D and 2D reconstruction tabs
	
	// ------ 3D --------



	// 3D control
	JPanel recon3dPanel = new JPanel();
	JPanel posSelector = new JPanel();
	posSelector.setBorder(BorderFactory.createTitledBorder("Stack size") );


	// position in stack
	Tiles.LNSpinner zBottom = new Tiles.LNSpinner("z bottom", 1, 1, imgs.nrSlices/imgPerZ, 1);
	Tiles.LNSpinner zTop = new Tiles.LNSpinner("z top", imgs.nrSlices/imgPerZ, 0, imgs.nrSlices/imgPerZ, 1);
	
	Tiles.LNSpinner tStart = new Tiles.LNSpinner("t start", 1, 1, imgs.nrTimepoints, 1);
	Tiles.LNSpinner tEnd = new Tiles.LNSpinner("t end", imgs.nrTimepoints, 1, imgs.nrTimepoints, 1);

	posSelector.add( zBottom );
	posSelector.add( zTop );
	posSelector.add( tStart );
	posSelector.add( tEnd );


	JPanel buttonPanel = new JPanel();
	JButton start3dReconButton = new JButton("run!");

	buttonPanel.add( start3dReconButton );

	recon3dPanel.add( posSelector );
	recon3dPanel.add( buttonPanel );

	// ------ 2D --------

	JPanel recon2dPanel = new JPanel();
	recon2dPanel.add( new JLabel("not implemented here yet"));

	// ------ ----- -----
	
	JTabbedPane reconTabs = new JTabbedPane();
	reconTabs.add( recon3dPanel, "full 3D");
	reconTabs.add( recon2dPanel, "2D / single slice");
	mainPanel.add( reconTabs );

	mainPanel.setLayout( new BoxLayout( mainPanel, BoxLayout.PAGE_AXIS));
	baseframe.add( mainPanel );
	baseframe.pack();
	baseframe.setVisible(true);




    }

    /** subclass for channel-specific settings */
    class ChannelPanel {

	JPanel ourPanel = new JPanel();
	    
	final JCheckBox channelEnabled;
	final Tiles.LComboBox< FITTYPES > fitTypeList;
	final Tiles.LComboBox< DefineMaschineGui.ChannelTab > channelSelector;
	final Tiles.LComboBox< OtfProvider3D > otfSelector;
	final Tiles.LNSpinner wienerParam;

	final int chNr ;

	ChannelPanel(int ch) {
	    chNr = ch;
	    ourPanel.setBorder( BorderFactory.createTitledBorder(
		String.format("Channel %d",chNr+1)));
	    ourPanel.setLayout( new BoxLayout( ourPanel, BoxLayout.PAGE_AXIS));

	    // Select if channel is reconstructed
	    channelEnabled = new JCheckBox("reconstruct this channel");
	    channelEnabled.setSelected(true);
	    ourPanel.add( (new JPanel()).add( channelEnabled ) );

	    // Box to select the channel
	    DefineMaschineGui.ChannelTab [] channelsAvailable = 
		new DefineMaschineGui.ChannelTab[ dmg.channels.size() ];

	    for (int i=0; i<channelsAvailable.length; i++)
		channelsAvailable[i] = dmg.channels.get( i );

	    channelSelector = new Tiles.LComboBox< DefineMaschineGui.ChannelTab >(
		    "Channel set", channelsAvailable );

	    if ( dmg.channels.size() > chNr )
		channelSelector.setSelectedIndex( chNr );

	    // Box tot select the OTF
	    otfSelector = new Tiles.LComboBox< OtfProvider3D >( "OTF", 
		    channelSelector.getSelectedItem().otfList.getArray());

	    // reset the OTF list if the channel is changed
	    channelSelector.addSelectListener( 
		new Tiles.SelectListener< DefineMaschineGui.ChannelTab >() {
		@Override
		public void selected( DefineMaschineGui.ChannelTab elem, int idx ){
		    otfSelector.newElements( elem.otfList.getArray() ); 
		}
	    });
	    JPanel selectPanel = new JPanel();
	    selectPanel.add( channelSelector );
	    selectPanel.add( otfSelector );

	    // settings for the parameter fit
	    JPanel fitPanel = new JPanel();
	
	    fitTypeList = new Tiles.LComboBox<FITTYPES>("SIM param fit", 
		FITTYPES.values());

	    fitTypeList.setSelectedIndex(1);

	    fitPanel.add( fitTypeList );

	    wienerParam = new Tiles.LNSpinner("Wiener filter",
		0.005, 0.001, 0.2, 0.002 );

	    fitPanel.add( wienerParam );
		
	    ourPanel.add( selectPanel );
	    ourPanel.add( fitPanel );

	    // propagate changes to other channels
	    if ( chNr == 0 && ourRawImages.nrChannels > 1 ) {
		ourPanel.add( propToOtherCh );
		
		// enable / disable the components
		propToOtherCh.addActionListener( new ActionListener() {
		    @Override
		    public void actionPerformed( ActionEvent e ) {
			
			boolean state = propToOtherCh.isSelected();
			for ( ChannelPanel c : channelPanelList ) {
			    if ( c != channelPanelList.get(0) ) {
				c.fitTypeList.setEnabled( !state );
				c.wienerParam.setEnabled( !state );
			    }
			}
		    }
		});


		// propagate change in fit type
		fitTypeList.addSelectListener( new Tiles.SelectListener<FITTYPES>() {
		    @Override
		    public void selected( FITTYPES e, int i ) {
			if ( propToOtherCh.isSelected() ) {
			    for ( ChannelPanel c : channelPanelList ) {
				if ( c != channelPanelList.get(0) ) {
				   c.fitTypeList.setSelectedIndex( i );
				}
			    }
			}
		    }
		});
	   
		// propagate change in Wiener filter parameter
		wienerParam.addNumberListener( new Tiles.NumberListener() {
		    @Override
		    public void number( double nbr, Tiles.LNSpinner e) {
			if ( propToOtherCh.isSelected() ) {
			    for ( ChannelPanel c : channelPanelList ) {
				if ( c != channelPanelList.get(0) ) {
				   c.wienerParam.setVal( nbr );
				}
			    }
			}

		    }
		});

	    
	    }




	}

	

    }




    // test function
    public static void main( String [] arg )
	throws Conf.EntryNotFoundException, Conf.SomeIOException {
	    Tool.error("For testing, run org.fairsim.fiji.FairSim_ImageJplugin_3d", false);
    }



}



