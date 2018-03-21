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
import org.fairsim.utils.ImageStackOutput;
import org.fairsim.utils.Tool;
import org.fairsim.utils.Conf;
import org.fairsim.linalg.Vec2d;
import org.fairsim.linalg.Vec3d;
import org.fairsim.sim_algorithm.SimAlgorithm3D;

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

    final DefineMachineGui dmg;

    final int imgPerZ;

    final ImageSelector.ImageInfo ourRawImages ;
    final ImageSelector imgSrc ;
    final JCheckBox propToOtherCh = new JCheckBox("propagate changes to other channels");
    
    List<ChannelPanel> channelPanelList = new ArrayList<ChannelPanel>();

    enum FITTYPES {
	FULL("full parameter search",3),
	REFINE("refine k0 and phase",2),
	PHASEONLY("fit only phase",1),
	RECONSTONLY("use preset values",0);

	final String ourName;
	final int val;

	FITTYPES(String name, int v) {
	    ourName=name;
	    val=v;
	}

	public int getVal() {
	    return val;
	}

	@Override 
	public String toString() {
	    return ourName;
	}


    }

    final Tiles.LNSpinner zBottom;
    final Tiles.LNSpinner zTop; 
    final Tiles.LNSpinner tStart; 
    final Tiles.LNSpinner tEnd; 


    public FairSim3dGUI( DefineMachineGui dmgIn, ImageSelector.ImageInfo imgs, ImageSelector imgSrc ) 
	{ //throws Conf.EntryNotFoundException, Conf.SomeIOException {

	baseframe.setLocation(100,100);
	this.imgSrc = imgSrc;

	this.dmg = dmgIn;
	Tool.trace("loaded machine definition: \""+ dmg.confName +"\"");

	imgPerZ = dmg.channels.get(0).sp.getImgPerZ();
	ourRawImages = imgs;

	if ( imgs.nrSlices % imgPerZ != 0 ) {
	    Tool.error(" Image length not a multiple of raw images per z-plane",true);
	    throw new RuntimeException("wrong image passed");
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
	zBottom = new Tiles.LNSpinner("z bottom", 1, 1, imgs.nrSlices/imgPerZ, 1);
	zTop = new Tiles.LNSpinner("z top", imgs.nrSlices/imgPerZ, 0, imgs.nrSlices/imgPerZ, 1);

	// TODO: once they are actually used in the reconstruction, re-enable them in the GUI
	zBottom.setEnabled(false);
	zTop.setEnabled(false);


	tStart = new Tiles.LNSpinner("t start", 1, 1, imgs.nrTimepoints, 1);
	tEnd = new Tiles.LNSpinner("t end", imgs.nrTimepoints, 1, imgs.nrTimepoints, 1);

	posSelector.add( zBottom );
	posSelector.add( zTop );
	posSelector.add( tStart );
	posSelector.add( tEnd );


	JPanel buttonPanel = new JPanel();
	JButton start3dReconButton = new JButton("run!");

	start3dReconButton.addActionListener( new ActionListener () {
	    @Override
	    public void actionPerformed(ActionEvent e){
		runReconstruction();
	    }
	});

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
	final JCheckBox			  fastFitCheckbox;
	final Tiles.LComboBox< DefineMachineGui.ChannelTab > channelSelector;
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
	    DefineMachineGui.ChannelTab [] channelsAvailable = 
		new DefineMachineGui.ChannelTab[ dmg.channels.size() ];

	    for (int i=0; i<channelsAvailable.length; i++)
		channelsAvailable[i] = dmg.channels.get( i );

	    channelSelector = new Tiles.LComboBox< DefineMachineGui.ChannelTab >(
		    "Channel set", channelsAvailable );

	    if ( dmg.channels.size() > chNr )
		channelSelector.setSelectedIndex( chNr );

	    // Box tot select the OTF
	    otfSelector = new Tiles.LComboBox< OtfProvider3D >( "OTF", 
		    channelSelector.getSelectedItem().otfList.getArray());

	    // reset the OTF list if the channel is changed
	    channelSelector.addSelectListener( 
		new Tiles.SelectListener< DefineMachineGui.ChannelTab >() {
		@Override
		public void selected( DefineMachineGui.ChannelTab elem, int idx ){
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
	    
	    // select if the fast peak fit (2D proj.) is used
	    fastFitCheckbox = new JCheckBox("use fast peak fit");
	    fastFitCheckbox.setSelected(true);
	    fitPanel.add( fastFitCheckbox );

	    // wiener parameter
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
				c.fastFitCheckbox.setEnabled( !state );
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
	  

		// progate change in fastFitEnabled
		fastFitCheckbox.addActionListener( new ActionListener() {
		    @Override
		    public void actionPerformed( ActionEvent e ) {
			
			boolean state = fastFitCheckbox.isSelected();
			
			if ( propToOtherCh.isSelected() ) {
			    for ( ChannelPanel c : channelPanelList ) {
				if ( c != channelPanelList.get(0) ) {
				   c.fastFitCheckbox.setSelected( state );
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


    void runReconstruction() {
	
	// figure out how many channels to reconstruct
	List<ChannelPanel> channelMap = new ArrayList<ChannelPanel>();
	for ( ChannelPanel a : channelPanelList ) {
	    if ( a.channelEnabled.isSelected() ) {
		channelMap.add( a );
	    }
	}

	int numChannels = channelMap.size();
	//int numZSlices  = (int)(zTop.getVal() - zBottom.getVal());
	int numZSlices  = ourRawImages.nrSlices / imgPerZ;
	int numTimesteps  = (int)(-tStart.getVal() + tEnd.getVal()+1);
	
	// TODO: implement the z-top, z-bottom values here

	Tool.trace("Reconstructing: c "+numChannels+" t "+numTimesteps );

	//extract image name with file extention removed
	int lastIndxDot = ourRawImages.name.lastIndexOf('.');
	String title;
	if (lastIndxDot != -1) {
	    title = ourRawImages.name.substring(0, lastIndxDot)+"_fsim";
	} else {
	    title = ourRawImages.name+"_fsim";
	}
	Tool.trace("Image title: " + title );
	
	ImageStackOutput iso = new org.fairsim.fiji.DisplayWrapper5D( 
	    ourRawImages.width*2, ourRawImages.height*2,
	    numZSlices, numChannels , numTimesteps, title);

	double [] emWavelengths = new double[ numChannels ];

	// loop the channels
	for (int ch = 0; ch<numChannels; ch++) {
	    ChannelPanel channel = channelMap.get( ch );
		
	    SimParam sp = channel.channelSelector.getSelectedItem().sp;

	    sp.setPxlSize3d( ourRawImages.width, numZSlices,
		ourRawImages.micronsPerPxl, ourRawImages.micronsPerSlice ); 

	    sp.otf3d( channel.otfSelector.getSelectedItem() );
	    sp.otf3d().setPixelSize(  1/ourRawImages.micronsPerPxl/ourRawImages.width, 
		1/ourRawImages.micronsPerSlice/numZSlices );
	
	    Tool.trace(String.format(
		    "Img dimension: lateral %7.3f nm/pxl axlia %7.3f nm/pxl, emission %4.0f nm",
		    ourRawImages.micronsPerPxl*1000, ourRawImages.micronsPerSlice*1000, 
		    sp.otf3d().getLambda() ));

	    emWavelengths[ch] = sp.otf3d().getLambda();

	    // loop the timepoints TODO: this is not thread-save (user might change value)
	    for (int t=(int)tStart.getVal()-1; t<(int)tEnd.getVal(); t++) {

		// generate the input vector
		Vec2d.Real [] inputImgs = imgSrc.getImages( ourRawImages, channel.chNr, t ); 	

		// run the reconstruction
		Vec3d.Cplx result = SimAlgorithm3D.runReconstruction(
			inputImgs, 
			channel.otfSelector.getSelectedItem(),
			channel.channelSelector.getSelectedItem().sp,
			-2, channel.wienerParam.getVal(),
			channel.fitTypeList.getSelectedItem().getVal(),
			channel.fastFitCheckbox.isSelected()
			);

		result.fft3d(true);

		// copy the result to output
		Vec2d.Real  res = Vec2d.createReal(  ourRawImages.width*2, ourRawImages.height*2 );
		for (int z=0; z<numZSlices; z++) {
			res.slice( result, z );
			iso.setImage( res, z, ch, t, "" );
		}

	    }
	}
	
	// set the pixel size and wavelength
	iso.setPixelSize( ourRawImages.micronsPerPxl/2, ourRawImages.micronsPerSlice);
	iso.setWavelengths( emWavelengths );

	iso.update();


    }



    // test function
    public static void main( String [] arg )
	throws Conf.EntryNotFoundException, Conf.SomeIOException {
	    Tool.error("For testing, run org.fairsim.fiji.FairSim_ImageJplugin_3d", false);
    }



}



