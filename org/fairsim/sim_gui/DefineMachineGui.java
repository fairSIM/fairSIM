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
import org.fairsim.utils.ImageDisplay;
import org.fairsim.utils.Tool;
import org.fairsim.utils.Conf;
import org.fairsim.misc.OtfFileConverter;

import java.awt.Frame;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import java.util.List;
import java.util.ArrayList;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JFileChooser;
//import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.JDialog;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.text.JTextComponent;
import javax.swing.JEditorPane;
import javax.swing.JTextField;
import javax.swing.JOptionPane;
import javax.swing.JList;
import java.awt.Font;

import java.net.URL;
import java.io.File;


import javax.swing.ImageIcon;
import javax.swing.Box;
import javax.swing.BoxLayout;
import java.awt.GridLayout;
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.BorderFactory;

// only for testing, remove..
import org.fairsim.fiji.DisplayWrapper;

/** A one-frame GUI for quick, largely
 *  automated reconstruction */
public class DefineMachineGui {

    final JFrame baseframe;
    final private JPanel mainPanel;
    final JTextField confNameField;
    final private JTabbedPane tabsPane;

    ArrayList<ChannelTab> channels = new ArrayList<ChannelTab>();

    String confName = null;


    /** Display a small frame to setup the DefineMachineGui */
    public static void setupDefineMachineGui() {
	
	final JFrame ourFrame = new JFrame("setup Define SIM");
	JPanel ourPanel = new JPanel();

	ourPanel.setLayout( new BoxLayout( ourPanel, BoxLayout.PAGE_AXIS));

	final Tiles.LComboBox<Integer> nrChannels = 
	    new Tiles.LComboBox<Integer>( "channels", null, false, 
		1, 2, 3, 4, 5 );

	final Tiles.LComboBox<Integer> nrBands = 
	    new Tiles.LComboBox<Integer>( "bands", null, false, 
		2, 3 );
	nrBands.setSelectedIndex(1);

	final Tiles.LComboBox<Integer> nrAngles = 
	    new Tiles.LComboBox<Integer>( "angles", null, false, 
		2, 3, 4, 5 );
	nrAngles.setSelectedIndex(1);

	final Tiles.LComboBox<Integer> nrPhases = 
	    new Tiles.LComboBox<Integer>( "phases", null, false, 
		5,6, 7, 8, 9 );

	JPanel buttonPanel = new JPanel();
	JButton okButton = new JButton("setup");
	JButton cancelButton  = new JButton("cancel");

	buttonPanel.add( okButton );
	buttonPanel.add( cancelButton );

	ourPanel.add( Box.createRigidArea( new Dimension(0,10)));
	ourPanel.add( nrChannels );
	ourPanel.add( Box.createRigidArea( new Dimension(0,10)));
	ourPanel.add( nrBands );
	ourPanel.add( Box.createRigidArea( new Dimension(0,10)));
	ourPanel.add( nrAngles );
	ourPanel.add( Box.createRigidArea( new Dimension(0,10)));
	ourPanel.add( nrPhases );
	ourPanel.add( Box.createRigidArea( new Dimension(0,10)));
	ourPanel.add(buttonPanel);


	okButton.addActionListener( new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {

		    // generate the config
		    Conf newConfig = generateBlankConf( 
			nrChannels.getSelectedItem(),
			nrBands.getSelectedItem(),
			nrAngles.getSelectedItem(),
			nrPhases.getSelectedItem());
		    try {
			new DefineMachineGui( newConfig.r(), true );
		    } catch ( Conf.EntryNotFoundException ex ) {
			Tool.trace("this should not happen:\n"+ex);
			
		    }
		    ourFrame.dispose();
	    
		}
	    });
	
	cancelButton.addActionListener( new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {
		    ourFrame.dispose();
		}
	    });



	ourFrame.add( ourPanel );
	ourFrame.pack();
	ourFrame.setLocation(100,100);
	ourFrame.setVisible(true);

    }

    /** Display a file chooser to start from a saved config */
    public static DefineMachineGui fromFileChooser( final Frame baseframe ) 
	throws Conf.SomeIOException, Conf.EntryNotFoundException {

	JFileChooser fc = new JFileChooser();
	int returnVal = fc.showOpenDialog(baseframe);

        if (returnVal == JFileChooser.APPROVE_OPTION) {
            String fname = fc.getSelectedFile().getAbsolutePath();
	    Conf cfg = Conf.loadFile( fname );

	    return new DefineMachineGui( cfg.cd("fairsim-3d"), true);
        }
	
	return null;
    }



    /** Generate a blank config file */
    public static Conf generateBlankConf( int nrChannels, int nrBands, int nrAngles, int nrPhases ) {

	Conf cfg = new Conf("fairsim-3d");

	cfg.r().newInt("nr-channels").setVal( nrChannels );

	// loop channels
	for ( int ch = 0; ch < nrChannels; ch ++ ) {

	    Conf.Folder chFldr = cfg.r().mk(String.format("channel-%02d", ch));
	    chFldr.newInt("excitation-wavelength").setVal(300);
	    chFldr.newStr("channel-name").val("no name set yet");

	    SimParam sp = SimParam.create3d( nrBands, nrAngles, nrPhases );
	    sp.saveConfig( chFldr );
	
	    chFldr.newInt("nr-OTFs").setVal( 0 );

	}

	return cfg;
    }
   


    // create and pack the control interface
    public DefineMachineGui( Conf.Folder fld, boolean becomeVisible ) 
	throws Conf.EntryNotFoundException {
	baseframe = new JFrame("Define SIM microscope");
	mainPanel = new JPanel();
	mainPanel.setLayout( new BoxLayout( mainPanel, BoxLayout.PAGE_AXIS));
	
	tabsPane = new JTabbedPane();


	// set the logo
	URL logo = getClass().getResource("/org/fairsim/resources/fairSimLogo.png");
	if ( logo != null) {
	    baseframe.setIconImage(new ImageIcon( logo ).getImage());
	}


	// add a 'maschine name' panel
	JPanel namePanel = new JPanel();
	//namePanel.setBorder(BorderFactory.createTitledBorder("Name and wavelegth") );
	JLabel confNameLabel = new JLabel("Config name:");


	if (fld.contains("config-name")) {
	    confName = fld.getStr("config-name").val();
	} else {
	    confName= "not set";
	}
	
	confNameField = new JTextField(confName,20);
    
	confNameField.addActionListener( new ActionListener() {
	    @Override
	    public void actionPerformed( ActionEvent e ) {
		confName = confNameField.getText();
		Tool.trace("updated config name to: "+confName);
	    }
	});

	namePanel.add( confNameLabel );
	namePanel.add( confNameField );

	// loop the channels, add a tab for each channel
	int nrChannels = fld.getInt("nr-channels").val();

	for ( int ch=0; ch<nrChannels; ch++) {

	    Conf.Folder chFldr = fld.cd(String.format("channel-%02d", ch));
	    ChannelTab chTab = new ChannelTab( chFldr );

	    String chName = String.format("unknown ch %d", ch);
	    if ( chFldr.contains("channel-name") ) {
		chName = chFldr.getStr("channel-name").val();
	    }
	    tabsPane.add( chName, chTab.getPanel() );
	    channels.add( chTab );

	}


	JPanel buttonPanel = new JPanel();
	JButton saveConfigButton = new JButton("save config");
	buttonPanel.add( saveConfigButton);

	saveConfigButton.addActionListener( new ActionListener() {
	    public void actionPerformed( ActionEvent e ) {
		saveConfig();
	    }
	});


	mainPanel.add( namePanel );
	mainPanel.add( tabsPane );
	mainPanel.add( buttonPanel );

	baseframe.add( mainPanel );
	baseframe.pack();
	baseframe.setLocation( 100,100);
	baseframe.setVisible( becomeVisible );

    }

    // save the current config
    void saveConfig() {

	JFileChooser saveFileChooser = new JFileChooser();
	saveFileChooser.setDialogType( JFileChooser.SAVE_DIALOG );
	int ret = saveFileChooser.showSaveDialog( baseframe );

	if ( ret != JFileChooser.APPROVE_OPTION ) {
	    return;
	}


	// setup new conf object to store
	Conf cfg = new Conf("fairsim-3d");	
	Conf.Folder fld = cfg.r();

	fld.newInt("nr-channels").setVal( channels.size() );
	fld.newStr("config-name").val( confName );

	// loop through all channels
	int i=0;
	for ( ChannelTab ct : channels ) {
	    ct.saveTo( fld.mk(String.format("channel-%02d", i++) ));
	}
	
	// store the full config
	try {
	    cfg.saveFile( saveFileChooser.getSelectedFile() );	
	} catch (Conf.SomeIOException ex) {
	    JOptionPane.showMessageDialog(baseframe, 
	    ex.toString(), "Problem saving", JOptionPane.ERROR_MESSAGE);
	}

    }




    // display each channel    
    class ChannelTab {
	
	JPanel ourPanel = new JPanel();

	JPanel getPanel() { return ourPanel; }

	final Conf.Folder ourFolder;
	final Tiles.LNSpinner [][] modSpinners;
	    
	final JTextField simNameField = new JTextField("",20);
	final Tiles.LNSpinner wavelengthSpinner ;

	final JTextComponent simParamAsText = new JEditorPane();
	SimParam sp;
	
	Tiles.TGuiList<OtfProvider3D> otfList = new Tiles.TGuiList<OtfProvider3D>();

	// initialize the tab for this channel from config
	ChannelTab( Conf.Folder chFld ) 
	    throws Conf.EntryNotFoundException {

	    
	    ourFolder = chFld;
  
	    // setting name and wavelength
	    JPanel generalPanel = new JPanel();
	    generalPanel.setBorder(BorderFactory.createTitledBorder("Name and wavelegth") );

	    JLabel simNameLabel = new JLabel("ch-name:");
	    simNameField.setText(chFld.getStr("channel-name").val());

	    simNameField.addActionListener( new ActionListener() {
		@Override
		public void actionPerformed( ActionEvent e ) {
		   tabsPane.setTitleAt( tabsPane.getSelectedIndex(), simNameField.getText()); 
		}
	    });


	    wavelengthSpinner = new Tiles.LNSpinner( "exitation wavelength",
		chFld.getInt("excitation-wavelength").val(), 200, 1000,1); 

	    generalPanel.add( simNameLabel );
	    generalPanel.add( simNameField );
	    generalPanel.add( wavelengthSpinner ); 


	    // displaying the SIM parameters
	    simParamAsText.setPreferredSize( new Dimension( 200,300));
	    simParamAsText.setEditable( false );
	    
	    sp = SimParam.loadConfig( ourFolder );
	    simParamAsText.setText( sp.prettyPrint(false) );
	
	    JPanel simPanel1 = new JPanel();

	    simPanel1.setBorder(BorderFactory.createTitledBorder("SIM (k0) defaults") );
	    simPanel1.setLayout( new BoxLayout( simPanel1, BoxLayout.PAGE_AXIS));
	    simPanel1.add( simParamAsText );
	
	    JButton loadSimParamButton = new JButton("load from file");

	    simPanel1.add( loadSimParamButton );

	    loadSimParamButton.addActionListener( new ActionListener () {
		@Override
		public void actionPerformed( ActionEvent e ){
		    loadSimParamFromFile();
		}
	    });
	    


	    // allow editing the mod depth values
	    modSpinners = new Tiles.LNSpinner[ sp.nrDir()][ sp.nrBand() ];

	    JPanel simPanel2 = new JPanel();
	    simPanel2.setBorder(BorderFactory.createTitledBorder("SIM modulation depth") );
	    simPanel2.setLayout( new BoxLayout( simPanel2, BoxLayout.PAGE_AXIS));
	    
	    for (int ang = 0 ; ang < sp.nrDir(); ang++) {
		
		final int fAng = ang;
		SimParam.Dir par = sp.dir( ang );
		JPanel pnl = new JPanel();

		for (int band =0 ; band < par.nrBand() ; band ++ ) {
		    
		    final int fBand = band;

		    modSpinners[ang][band] = new Tiles.LNSpinner(
			String.format( "ang: %1d, band: %1d", ang, band),
			par.getModulations()[ band ],
			0.2, 1.1, 0.01);

		    modSpinners[ang][band].addNumberListener( new Tiles.NumberListener() {
			@Override
			public void number( double n, Tiles.LNSpinner e ) {
			    getSimParam().dir( fAng ).setModulation( fBand, n );
			    simParamAsText.setText( sp.prettyPrint(false) );
			}

			});


		    pnl.add( modSpinners[ang][band] );


		}
		
		simPanel2.add( pnl );



	    }

	    // have a list of OTFs
	    JPanel otfPanel = new JPanel();
	    otfPanel.setLayout( new BoxLayout( otfPanel, BoxLayout.LINE_AXIS ));
	    otfPanel.setBorder(BorderFactory.createTitledBorder("OTFs for channel") );


	    JPanel listPanel = new JPanel();
	    otfList.setPrototypeCellValue( new OtfProvider3D());
	    otfList.setVisibleRowCount(5);
	    otfList.setLayoutOrientation( JList.VERTICAL);
	    otfList.setPreferredSize( new Dimension(300,100));
	    listPanel.add( otfList );


	    // fill the OTF list
	    for ( int i = 0 ; i<ourFolder.getInt("nr-OTFs").val() ; i++) {
		OtfProvider3D newOtf = OtfProvider3D.loadFromConfig( 
		    ourFolder.cd(String.format("otf-%02d",i)));
		otfList.addElement( newOtf );
	    }


	    // create buttons to interact with OTFs
	    JPanel buttonPanel = new JPanel();
	    
	    JButton loadOtfButton = new JButton("add fairSIM OTF");
	    loadOtfButton.addActionListener( new ActionListener() {
		@Override
		public void actionPerformed( ActionEvent e ) {
		    loadOtfFromFile();
		};
	    });
	    
	    JButton convertOtfButton = new JButton("convert OTF");
	    convertOtfButton.addActionListener( new ActionListener() {
		@Override
		public void actionPerformed( ActionEvent e ) {
	    
		    JFileChooser fileChooser = new JFileChooser();

		    int returnVal  = fileChooser.showOpenDialog( baseframe );
		    if ( returnVal == JFileChooser.APPROVE_OPTION ) {
			OtfProvider3D otf = convertOtfFromFile( fileChooser.getSelectedFile(), baseframe );
			if (otf!=null) {
			    otfList.addElement( otf );    
			}
		    }
		}
	    });

	    JButton editOtfButton = new JButton("edit OTF metadata");
	    editOtfButton.addActionListener( new ActionListener(){
		@Override
		public void actionPerformed( ActionEvent e ) {
		    OtfProvider3D otf = otfList.getSelectedElement();
		    if (otf!=null) {
			showOtfParamEditWindow( otf, baseframe );
		    }
		}
	    });

	    buttonPanel.setLayout( new BoxLayout( buttonPanel, BoxLayout.PAGE_AXIS));
	    buttonPanel.add( loadOtfButton );
	    buttonPanel.add( Box.createRigidArea( new Dimension(0,3)));
	    buttonPanel.add( convertOtfButton );
	    buttonPanel.add( Box.createRigidArea( new Dimension(0,3)));
	    buttonPanel.add( editOtfButton );

	    otfPanel.add( buttonPanel);
	    otfPanel.add( listPanel);
	    

	    // add everything to our panel
	    ourPanel.setLayout( new BoxLayout( ourPanel, BoxLayout.PAGE_AXIS));
	    ourPanel.add( generalPanel );
	    ourPanel.add( simPanel1 );
	    ourPanel.add( simPanel2 );
	    ourPanel.add( otfPanel );


	}


	// return the currently active SIM parameters
	SimParam getSimParam() {
	    return sp;
	}
	
	// load a SIM parameter set from file
	void loadSimParamFromFile() {
	    JFileChooser fileChooser = new JFileChooser();

	    // load the file
	    int returnVal  = fileChooser.showOpenDialog( baseframe );
	    if ( returnVal == JFileChooser.APPROVE_OPTION ) {
	
		SimParam spNew = null;

		try {
		    Conf simParamConf = Conf.loadFile( fileChooser.getSelectedFile() );
		    spNew = SimParam.loadConfig( simParamConf.r() );
		    
		    if ( spNew.nrDir() != sp.nrDir() || sp.nrBand() != spNew.nrBand() ) {
			JOptionPane.showMessageDialog(baseframe, 
			"Parameters do not match", "Problem", JOptionPane.ERROR_MESSAGE);
			return;
		    }
		} catch ( Conf.SomeIOException e ) {
			JOptionPane.showMessageDialog(baseframe,
			"File seems to not contain a SIM parameter set:\n"+e.toString(), 
			"Problem", JOptionPane.ERROR_MESSAGE);
			Tool.trace( "SIM param read error:\n"+e.toString() );
			return; 
		} catch ( Conf.EntryNotFoundException e ) {
			JOptionPane.showMessageDialog(baseframe,
			"File seems to miss entries:\n"+e.toString(), 
			"Problem", JOptionPane.ERROR_MESSAGE);
			Tool.trace( "SIM param read error:\n"+e.toString() );
			return;
		}
	    
		// update our parameters
		if (spNew != null) {
		    sp=spNew;
		}

	    }

	    // update the modulation spinners
	    for (int ang = 0 ; ang < sp.nrDir(); ang++) {
		for (int band =0 ; band < sp.dir(ang).nrBand() ; band ++ ) {
		    modSpinners[ang][band].spr.setValue( sp.dir(ang).getModulations()[band] );
		}
	    }

	    // update the text
	    simParamAsText.setText( sp.prettyPrint(false) );

	}

	// load a 3D OTF from file
	void loadOtfFromFile() {
	    
	    JFileChooser fileChooser = new JFileChooser();

	    int returnVal  = fileChooser.showOpenDialog( baseframe );
	    if ( returnVal == JFileChooser.APPROVE_OPTION ) {

		// read in the OTF from file
		OtfProvider3D otfNew = null;
		try {
		    Conf otfConf = Conf.loadFile( fileChooser.getSelectedFile() );
		    otfNew = OtfProvider3D.loadFromConfig( otfConf.cd("fairsim").cd("otf3d") );
		    
		} catch ( Conf.SomeIOException e ) {
			JOptionPane.showMessageDialog(baseframe,
			"File seems to not contain a OTF in fairSIM format:\n"+e.toString(), 
			"Problem", JOptionPane.ERROR_MESSAGE);
			Tool.trace( "OTF read error:\n"+e.toString() );
			return; 
		} catch ( Conf.EntryNotFoundException e ) {
			JOptionPane.showMessageDialog(baseframe,
			"OTF File seems to miss entries:\n"+e.toString(), 
			"Problem", JOptionPane.ERROR_MESSAGE);
			Tool.trace( "OTF read error:\n"+e.toString() );
			return;
		}
	    
		// add it to the OTF list
		if (otfNew != null ) {
		    // ask some parameters
		    if ( showOtfParamEditWindow( otfNew, baseframe ) ) {	
			otfList.addElement( otfNew ) ;
		    }
		}

	    }

	}

	// save everything in this channel to config
	void saveTo( Conf.Folder chFldr ) {
	    
	    // save our metadata
	    chFldr.newStr("channel-name").val( simNameField.getText() );
	    chFldr.newInt("excitation-wavelength").setVal( (int)wavelengthSpinner.getVal() );
	    
	    // save the SIM parameters
	    sp.saveConfig( chFldr );

	    // save the OTFs
	    chFldr.newInt("nr-OTFs").setVal( otfList.getListLength() );

	    int i=0;
	    for ( OtfProvider3D otf : otfList.getAllElements() ) {
		otf.saveConfig( chFldr.mk(String.format("otf-%02d", i++)));
	    }
	}


	@Override
	public String toString() {
	    return simNameField.getText(); 
	}

    }


    /** shows dialog to edit properties of an OTF */
    public static boolean showOtfParamEditWindow( OtfProvider3D otf, Component base ) {

	JPanel ourPanel = new JPanel();

	// name
	JPanel p1 = new JPanel();
	JLabel nameLabel = new JLabel("name:");
	JTextField otfNameField = new JTextField(otf.getName(),20);
	p1.add( nameLabel);
	p1.add( otfNameField);

	// meta
	JPanel p2 = new JPanel();
	JLabel metaLabel = new JLabel("meta:");
	JTextField otfMetaField = new JTextField(otf.getMeta(),40);
	p2.add( metaLabel);
	p2.add( otfMetaField);

	// NA
	Tiles.LNSpinner naSpinner = new Tiles.LNSpinner("NA",
	    otf.getNA(), 0.3, 2, 0.05 );
	
	// wavelength
	Tiles.LNSpinner emSpinner = new Tiles.LNSpinner("em. wavelength",
	    otf.getLambda(), 250, 1500, 1 );
	
	
	// all metadata (display only)
	JTextComponent otfParamAsText = new JEditorPane();
	otfParamAsText.setFont( new Font( Font.MONOSPACED, Font.PLAIN, 11 ));
	otfParamAsText.setText(otf.getOtfInfoString());


	// create panel and show dialog
	ourPanel.add(p1);
	ourPanel.add(p2);
	ourPanel.add(naSpinner);
	ourPanel.add(emSpinner);
	ourPanel.add(otfParamAsText);
	ourPanel.setLayout( new BoxLayout(ourPanel, BoxLayout.PAGE_AXIS));

	int retVal = 
	    JOptionPane.showConfirmDialog( base, ourPanel, "Edit OTF metadata", JOptionPane.OK_CANCEL_OPTION );

	if (retVal != JOptionPane.OK_OPTION) {
	    return false;
	}

	// read out values and write to the OTF
	otf.setName( otfNameField.getText());
	otf.setMeta( otfMetaField.getText());
	otf.setNA( naSpinner.getVal());
	otf.setLambda( emSpinner.getVal());


	return true;
    }


    /** Connects to the OTF importer. TODO: This could be more modular. */
    public static OtfProvider3D convertOtfFromFile( File fObj, Component base ) {
    
	// figure out if the converter is present
	try {
	    Class.forName("de.bio_photonics.omxtools.OTFConverter");
	} catch ( ClassNotFoundException ex) {
	    JOptionPane.showMessageDialog(base,
	    "Please ensure SRSIM-Tools are installed / in the classpath",
	    "SRSIM-Tools not found", JOptionPane.ERROR_MESSAGE);
	    
	    return null;
	}

	// run the OTF converter
	OtfFileConverter otfConv = null;

	try {
	    otfConv = new OtfFileConverter( fObj );	
	} catch (java.io.IOException ex ) {
	    JOptionPane.showMessageDialog(base,
	    ex.toString(),
	    "IO error reading OTF", JOptionPane.ERROR_MESSAGE);
	    return null;
	}
    
	// setup a new OTF
	OtfProvider3D otf = OtfProvider3D.createFromData(
	    otfConv.getNrBands(),
	    otfConv.getBandsData(),
	    otfConv.getCyclesPerMicronLateral(),
	    otfConv.getCyclesPerMicronAxial(), 
	    otfConv.getSamplesLateral(),
	    otfConv.getSamplesAxial() );


	// show OTF editor 
	if ( showOtfParamEditWindow( otf, base ) ) {
	    return otf;
	} else {
	    return null;
	}
    }
	

    /** For testing, start with or without config file as argument */
    public static void main( String [] arg ) 
	throws Conf.SomeIOException, Conf.EntryNotFoundException {
	
	if (arg.length==0) {
	    setupDefineMachineGui();
	} else {
	    Conf cfg = Conf.loadFile( arg[0]);
	    new DefineMachineGui( cfg.cd("fairsim-3d"), true );
	}
    }

}
