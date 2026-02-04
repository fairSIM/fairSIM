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
import org.fairsim.utils.ImageOutputFactory;

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
import javax.swing.SwingWorker;

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


/** A one-frame GUI for quick, largely
 *  automated reconstruction */
public class FairSim3dGUI {

    // presets for the Wiener value spinner (only min/max, default values, see below!)
    final double wienerLowest = 0.0001, wienerHighest = 0.2, wienerSteps = 0.00025;

    // presets for the Apo filter spinners (only min/max, default values, see below!)
    final double apoLateralLowest = 1.0, apoLateralHighest = 3.0, apoLateralSteps = 0.05;
    final double apoAxialLowest = 1.0, apoAxialHighest = 3.0, apoAxialSteps = 0.05;
    final double apoBendLowest = 0.1, apoBendHighest = 1.2, apoBendSteps = 0.05;

    final JFrame baseframe = new JFrame("fairSIM 3D GUI");
    final private JPanel mainPanel = new JPanel();
	
	final JButton start3dReconButton = new JButton("run!");
	//final JButton cancel3dReconButton = new JButton("cancel");

    final DefineMachineGui dmg;

    final int imgPerZ;

    final ImageSelector.ImageInfo ourRawImages ;
    final ImageSelector imgSrc ;
    final JCheckBox propToOtherCh = new JCheckBox("propagate changes to other channels");
    
    List<ChannelPanel> channelPanelList = new ArrayList<ChannelPanel>();

    final ImageOutputFactory imgFactory ;

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


    public FairSim3dGUI( DefineMachineGui dmgIn, ImageSelector.ImageInfo imgs, ImageSelector imgSrc, 
	String [] chPresets, ImageOutputFactory imgFac, 
	boolean autostart, boolean headless, String resultImageFile ) 
	{ 

	imgFactory = imgFac;
	
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
	    String chPresetString = null;
	    if ( chPresets != null && chPresets.length > ch)
		chPresetString = chPresets[ch];
	    ChannelPanel chPnl = new ChannelPanel(ch, chPresetString);
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

	// amount of visual feedback
	Tiles.LNSpinner vfbSpinner = new Tiles.LNSpinner("visual feedback level", 0, -2, 3, 1);
	posSelector.add( vfbSpinner );

	
	JPanel buttonPanel = new JPanel();

	start3dReconButton.addActionListener( new ActionListener () {
	    @Override
	    public void actionPerformed(ActionEvent e){
		startReconstructionThread( false, null, (int)vfbSpinner.getVal() );
	    }
	});

	//cancel3dReconButton.setEnabled( false );
	//buttonPanel.add( cancel3dReconButton );
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
	if (!headless) {
	    baseframe.pack();
	    baseframe.setVisible( true );
	} 

	// TODO: unnice hack to get Java to actually display the requested digits
	for ( ChannelPanel a : channelPanelList ) {
	   a.wienerParam.setVal( a.wienerParam.getVal()+1e-10);
	}


	// TODO: check if this is o.k.
	if (autostart) {
	    start3dReconButton.setEnabled(false);
	    startReconstructionThread(headless, resultImageFile, -2); // Todo: propagate visual feedback level in autostart mode
	    start3dReconButton.setEnabled(true);
	}

	if (headless) {
	    Tool.shutdown();
	    System.exit(0);
	}

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
	final Tiles.LNSpinner apoLateral, apoAxial, apoBend;
	final Tiles.LComboBox<String> apoTypeSelector;

	final int chNr ;

	ChannelPanel(int ch, String presets ) {
	    
	    chNr = ch;
	    
	    double wienerPreset = 0.005;
		double apoLateralPreset = 2.0;
		double apoAxialPreset = 2.0;
		double apoBendPreset = 0.8;
		int apoTypePreset = 1; // cosine by default

	    int    chIdx  = chNr;
	    int	   otfIdx = 0;
	    int	   fitTypePreset = 1;
	    boolean useThisChannel = true;

	    // parse some presets, allowing for easier headless operation
	    if ( presets != null ) {
		String [] tokens = presets.split("[: ]");
		
		for (int i=0; i<tokens.length; i++) {
		   
		    String tok = tokens[i].trim();

		    // fit type
		    if ( tok.startsWith("f") ) {
			fitTypePreset = Integer.parseInt( tok.substring(1));
			if (fitTypePreset < FITTYPES.values().length ){
			    Tool.trace( "ch"+(chNr+1)+": preset fit type to "+fitTypePreset+" ("
				+FITTYPES.values()[fitTypePreset]+")");
			} else {
			    Tool.error( "ch"+(chNr+1)+": fit type preset does not exist: "+fitTypePreset,false);
			    fitTypePreset = 1;
			}
		    }
		  

		    // channel preset
		    if ( tok.startsWith("p") ) {
			chIdx = Integer.parseInt( tok.substring(1));
			if ( chIdx < dmg.channels.size() ){
			    Tool.trace( "ch"+(chNr+1)+": using presets #"+chIdx+" ("+dmg.channels.get(chIdx).toString()+")");
			} else {
			    Tool.error( "ch"+(chNr+1)+": channel preset #"+chIdx+" does not exist!",false);
			    chIdx = chNr;
			}
		    }
		  
		    // OTF preset
		    if ( tok.startsWith("o") ) {
			otfIdx = Integer.parseInt( tok.substring(1));
			Tool.trace( "ch"+(chNr+1)+": selected OTF #"+otfIdx);
		    }


		    // wiener filter parameter
		    if ( tok.startsWith("w")) {
			
			double wPreset ;
			if (tok.charAt(1)=='=') {
			    wPreset = Double.parseDouble( tok.substring(2));
			} else {
			    wPreset = Double.parseDouble( tok.substring(1));
			}
	
			if ( wPreset >= wienerLowest && wPreset <= wienerHighest ) {
			    Tool.trace("ch"+(chNr+1)+": preset Wiener filter to "+wPreset);
			    wienerPreset = wPreset;
			} else {
			    Tool.error("ch"+(chNr+1)+": Wiener preset out of range "+wienerPreset, false);
			}

		    }

			// apo filter parameters
			if ( tok.startsWith("apoL") ) {
				double apoPreset =0;
				if (tok.length()>4 && tok.charAt(4)=='=') {
					apoPreset = Double.parseDouble( tok.substring(5));
				} else {
					apoPreset = Double.parseDouble( tok.substring(4));
				}
				if ( apoPreset >= apoLateralLowest && apoPreset <= apoLateralHighest ) {
					Tool.trace("ch"+(chNr+1)+": preset Apo lateral to "+ apoPreset);
					apoLateralPreset = apoPreset;
				} else {
					Tool.error("ch"+(chNr+1)+": Apo lateral preset out of range "+apoLateralPreset, false);
				}
			}	

			if ( tok.startsWith("apoA") ) {
				double apoPreset =0;
				if (tok.length()>4 && tok.charAt(4)=='=') {
					apoPreset = Double.parseDouble( tok.substring(5));
				} else {
					apoPreset = Double.parseDouble( tok.substring(4));
				}
				if ( apoPreset >= apoAxialLowest && apoPreset <= apoAxialHighest ) {
					Tool.trace("ch"+(chNr+1)+": preset Apo axial to "+ apoPreset);
					apoAxialPreset = apoPreset;
				} else {
					Tool.error("ch"+(chNr+1)+": Apo axial preset out of range "+apoAxialPreset, false);
				}
			}	

			if ( tok.startsWith("apoB") ) {
				double apoPreset =0;
				if (tok.length()>4 && tok.charAt(4)=='=') {	
					apoPreset = Double.parseDouble( tok.substring(5));
				} else {
					apoPreset = Double.parseDouble( tok.substring(4));
				}
				if ( apoPreset >= apoBendLowest && apoPreset <= apoBendHighest ) {
					Tool.trace("ch"+(chNr+1)+": preset Apo bend to "+ apoPreset);
					apoBendPreset = apoPreset;
				} else {
					Tool.error("ch"+(chNr+1)+": Apo bend preset out of range "+apoBendPreset, false);
				}
			}

			if ( tok.startsWith("apoType") ) {
				if (tok.length()>7 && tok.charAt(7)=='=') {
					String atype = tok.substring(8).toLowerCase();
					if (atype.equals("linear")) {
						apoTypePreset = 0;
						Tool.trace("ch"+(chNr+1)+": preset Apo type to linear");
					} else if (atype.equals("cosine")) {
						apoTypePreset = 1;
						Tool.trace("ch"+(chNr+1)+": preset Apo type to cosine");
					} else if (atype.equals("off")) {
						apoTypePreset = 2;
						Tool.trace("ch"+(chNr+1)+": preset Apo type to off");
					} else {
						Tool.error("ch"+(chNr+1)+": No match for Apo type preset: linear, cosine, off" , false);
					}
				} else {
					Tool.error("ch"+(chNr+1)+": Specify Apo type preset: apoType=[linear, cosine, off]" , false);
				}
			}

		    // turn off reconstruction of this channel completely
		    if (tok.equals("disable")) {
			useThisChannel = false;
			Tool.trace("ch"+(chNr+1)+": DISABLED");
		    }


		}
	    }

	    // we can check the OTF only after everything has parsed, as only now we know which channel preset to use
	    if (otfIdx != 0) {
		if (otfIdx <0 || otfIdx >= dmg.channels.get(chIdx).otfList.getListLength()) {
		    Tool.error("ch"+(chNr+1)+": selected OTF #"+otfIdx+" not available!",false);
		    otfIdx = 0;
		} 
	    }
	


	    ourPanel.setBorder( BorderFactory.createTitledBorder(
		String.format("Channel %d",chNr+1)));
	    ourPanel.setLayout( new BoxLayout( ourPanel, BoxLayout.PAGE_AXIS));

	    // Select if channel is reconstructed
	    channelEnabled = new JCheckBox("reconstruct this channel");
	    channelEnabled.setSelected(useThisChannel);
	    ourPanel.add( (new JPanel()).add( channelEnabled ) );

	    // Box to select the channel
	    DefineMachineGui.ChannelTab [] channelsAvailable = 
		new DefineMachineGui.ChannelTab[ dmg.channels.size() ];

	    for (int i=0; i<channelsAvailable.length; i++)
		channelsAvailable[i] = dmg.channels.get( i );

	    channelSelector = new Tiles.LComboBox< DefineMachineGui.ChannelTab >(
		    "Channel set", channelsAvailable );

	    if ( dmg.channels.size() > chIdx )
		channelSelector.setSelectedIndex( chIdx );

	    // Box tot select the OTF
	    otfSelector = new Tiles.LComboBox< OtfProvider3D >( "OTF", 
		    channelSelector.getSelectedItem().otfList.getArray());

	    otfSelector.setSelectedIndex( otfIdx );

	    // reset the OTF list if the channel is changed
	    channelSelector.addSelectListener( 
		new Tiles.SelectListener< DefineMachineGui.ChannelTab >() {
		@Override
		public void selected( DefineMachineGui.ChannelTab elem, int idx ){
		    otfSelector.newElements( elem.otfList.getArray() ); 
		}
	    });
	    JPanel selectPanel = new JPanel();
	    selectPanel.setBorder( BorderFactory.createTitledBorder("Channel settings") );   
		selectPanel.add( channelSelector );
	    selectPanel.add( otfSelector );

	    // settings for the parameter fit
	    JPanel fitPanel = new JPanel();
		fitPanel.setBorder( BorderFactory.createTitledBorder("Parameter fit options") );
		
	    fitTypeList = new Tiles.LComboBox<FITTYPES>("SIM param fit", 
		FITTYPES.values());

	    fitTypeList.setSelectedIndex( fitTypePreset );

	    fitPanel.add( fitTypeList );
	    
	    // select if the fast peak fit (2D proj.) is used
	    fastFitCheckbox = new JCheckBox("use fast peak fit");
	    fastFitCheckbox.setSelected(true);
	    fitPanel.add( fastFitCheckbox );

	    // filter parameter
		JPanel filterPanel = new JPanel();
		filterPanel.setBorder( BorderFactory.createTitledBorder("Filter parameters") );
	    
		wienerParam = new Tiles.LNSpinner("Wiener filter",
		wienerPreset, wienerLowest, wienerHighest, wienerSteps );
	    wienerParam.setDigits(6);

		apoLateral = new Tiles.LNSpinner("Apo lateral",
		 apoLateralPreset, apoLateralLowest, apoLateralHighest, apoLateralSteps);
		apoAxial = new Tiles.LNSpinner("Apo axial", 
		 apoAxialPreset, apoAxialLowest, apoAxialHighest, apoAxialSteps);
		apoBend = new Tiles.LNSpinner("Apo bend", 
		 apoBendPreset, apoBendLowest, apoBendHighest, apoBendSteps);

		apoTypeSelector = new Tiles.LComboBox<String>( "Apo type",
		 new String[] { "linear", "cosine", "off" } );
		apoTypeSelector.setSelectedIndex(apoTypePreset); // linear by default	

		apoLateral.setDigits(4);
		apoAxial.setDigits(4);
		apoBend.setDigits(4);

		filterPanel.add( wienerParam );
		filterPanel.add( apoLateral );
		filterPanel.add( apoAxial );
		filterPanel.add( apoBend );
		filterPanel.add( apoTypeSelector );

		// add the channel panel to the main panel
	    ourPanel.add( selectPanel );
	    ourPanel.add( fitPanel );
	    ourPanel.add( filterPanel );

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
				c.apoLateral.setEnabled( !state );
				c.apoAxial.setEnabled( !state );
				c.apoBend.setEnabled( !state );
				c.apoTypeSelector.setEnabled( !state );
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

		// propagate change in apo lateral
		apoLateral.addNumberListener( new Tiles.NumberListener() {
		    @Override
		    public void number( double nbr, Tiles.LNSpinner e) {
			if ( propToOtherCh.isSelected() ) {
			    for ( ChannelPanel c : channelPanelList ) {
				if ( c != channelPanelList.get(0) ) {
				   c.apoLateral.setVal( nbr );
				}
			    }
			}

		    }
		});
		// propagate change in apo axial
		apoAxial.addNumberListener( new Tiles.NumberListener() {
		    @Override
		    public void number( double nbr, Tiles.LNSpinner e) {
			if ( propToOtherCh.isSelected() ) {
			    for ( ChannelPanel c : channelPanelList ) {
				if ( c != channelPanelList.get(0) ) {
				   c.apoAxial.setVal( nbr );
				}
			    }
			}

		    }
		});
		// propagate change in apo bend
		apoBend.addNumberListener( new Tiles.NumberListener() {
		    @Override
		    public void number( double nbr, Tiles.LNSpinner e) {
			if ( propToOtherCh.isSelected() ) {
			    for ( ChannelPanel c : channelPanelList ) {
				if ( c != channelPanelList.get(0) ) {
				   c.apoBend.setVal( nbr );
				}
			    }
			}

		    }
		});
		// propagate change in apo type
		apoTypeSelector.addSelectListener( new Tiles.SelectListener<String>() {
		    @Override
		    public void selected( String e, int i ) {
			if ( propToOtherCh.isSelected() ) {
			    for ( ChannelPanel c : channelPanelList ) {
				if ( c != channelPanelList.get(0) ) {
				   c.apoTypeSelector.setSelectedIndex( i );	

				}
			    }
			}
		}
		});
	    } // end of if first channel

	    // add the channel panel to the main panel
	    ourPanel.setMaximumSize( new Dimension( Integer.MAX_VALUE, 100));
	    ourPanel.setMinimumSize( new Dimension( 200, 100));
	    mainPanel.add( ourPanel );
	}
	
	} // end of ChannelPanel class


	void startReconstructionThread(boolean headless, String saveFileName, int visualFeedbackLevel) {

		// in headless mode, just run directly
		if (headless) {
		    runReconstruction( headless, saveFileName, visualFeedbackLevel );
		    return;
		}

		// otherwise, in the GUI, start a Swing worker thread
		SwingWorker<Void,Void> worker = new SwingWorker<Void,Void>() {
		    @Override
		    protected Void doInBackground() throws Exception {
			runReconstruction( headless, saveFileName, visualFeedbackLevel );
			return null;
		    }

		    @Override
		    protected void done() {
			try {
				get(); // This will throw any exception from doInBackground()
				Tool.trace("Reconstruction thread completed");
			} catch (java.util.concurrent.CancellationException ex) {
				Tool.trace("Reconstruction thread cancelled");
			} catch (Exception ex) {
				// Forward the exception to your logging system
				Tool.error("Exception in reconstruction thread: " + ex.getCause(), false);
				ex.printStackTrace();
			}
			//cancel3dReconButton.setEnabled( false );
			//start3dReconButton.setEnabled( true );	
			}
		};		


		// TODO: implement cancel. This can't be easily done via exception right now,
		// have to actually build it into the reconstruction code
		/*
		// set and enable the cancel button
		cancel3dReconButton.setEnabled( true );
		start3dReconButton.setEnabled( false );
		for (ActionListener al : cancel3dReconButton.getActionListeners() ) {
		    cancel3dReconButton.removeActionListener( al );
		}
		cancel3dReconButton.addActionListener( new ActionListener() {
		    @Override
		    public void actionPerformed( ActionEvent e ) {
			worker.cancel( true );
			Tool.trace("Reconstruction cancelled by user");
		    }
		});
		*/

		worker.execute();

	}



    void runReconstruction(boolean headless, String saveFileName, int visualFeedbackLevel) {
	
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
	    title = ourRawImages.name.substring(0, lastIndxDot)+"_SIF";
	} else {
	    title = ourRawImages.name+"_SIF";
	}

	ImageStackOutput iso = imgFactory.create( 
	    ourRawImages.width*2, ourRawImages.height*2,
	    numZSlices, numChannels , numTimesteps, title, headless);

	double [] emWavelengths = new double[ numChannels ];

	// loop the channels
	for (int ch = 0; ch<numChannels; ch++) {
	    ChannelPanel channel = channelMap.get( ch );
		
	    SimParam sp = channel.channelSelector.getSelectedItem().sp.duplicate();

	    sp.setPxlSize3d( ourRawImages.width, numZSlices,
		ourRawImages.micronsPerPxl, ourRawImages.micronsPerSlice ); 

	    sp.otf3d( channel.otfSelector.getSelectedItem().duplicate() );
	    sp.otf3d().setPixelSize(  1/ourRawImages.micronsPerPxl/ourRawImages.width, 
		1/ourRawImages.micronsPerSlice/numZSlices );
	
	    Tool.trace(String.format(
		    "Img dimension: lateral %7.3f nm/pxl axlia %7.3f nm/pxl, emission %4.0f nm",
		    ourRawImages.micronsPerPxl*1000, ourRawImages.micronsPerSlice*1000, 
		    sp.otf3d().getLambda() ));

	    sp.setWienerFilter( channel.wienerParam.getVal() ); 
		sp.setApoCutoff( channel.apoLateral.getVal() );
		sp.setApoCutoffAxial( channel.apoAxial.getVal() );
		sp.setApoBend( channel.apoBend.getVal() );
		sp.setApoType( channel.apoTypeSelector.getSelectedIndex() );

		Tool.trace(String.format(
			"Using Wiener filter %7.5f, Apo lateral %7.3f, Apo axial %7.3f, Apo bend %7.3f Apo type %s",
			sp.getWienerFilter(), 
			sp.getApoCutoff(), sp.getApoCutoffAxial(), sp.getApoBend(), 
			sp.getApoTypeSting() ));	
		
	    int   ourFitLevel = channel.fitTypeList.getSelectedItem().getVal();
	    boolean doFastFit  = channel.fastFitCheckbox.isSelected();


	    emWavelengths[ch] = sp.otf3d().getLambda();

	    // loop the timepoints TODO: this is not thread-save (user might change value)
	    for (int t=(int)tStart.getVal()-1; t<(int)tEnd.getVal(); t++) {

		// generate the input vector
		Vec2d.Real [] inputImgs = imgSrc.getImages( ourRawImages, channel.chNr, t ); 	

		// run the reconstruction
		Vec3d.Cplx result = SimAlgorithm3D.runReconstruction(
			inputImgs, sp, visualFeedbackLevel, ourFitLevel, doFastFit 
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

	if (saveFileName != null) {
	    iso.saveToFile( saveFileName );
	}


    }



    // test function
    public static void main( String [] arg )
	throws Conf.EntryNotFoundException, Conf.SomeIOException {
	    Tool.error("For testing, run org.fairsim.fiji.FairSim_ImageJplugin_3d", false);
    }



}



