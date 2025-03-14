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

import java.util.List;
import java.util.ArrayList;

import javax.swing.JPanel;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;

import javax.swing.Box;
import javax.swing.SwingConstants;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;
import javax.swing.JOptionPane;
import javax.swing.JSpinner;
import javax.swing.JFileChooser;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;

import java.awt.Dimension;
import java.awt.ComponentOrientation;
import java.awt.Component;
import java.awt.Color;
import java.awt.GridLayout;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import java.io.File;


import org.fairsim.utils.Tool;
import org.fairsim.utils.Conf;

import org.fairsim.sim_algorithm.OtfProvider;
import org.fairsim.sim_algorithm.SimParam;

/**
 * GUI elements to set up OTF
 **/
public class OtfControl { 
    
    private final JPanel ourContent = new JPanel();  
    private final JFrame baseframe ;

    private JLabel	otfText	    = new JLabel("No OTF set, please load or approximate");
    private JLabel	attText	    = new JLabel("No OTF set");
    final private SimParam	sp;

    private Tiles.LComboBox<String> attSw;
    private Tiles.LNSpinner attStr, attFWHM;

	// how many concurrent filters to show for attenuation filtering
	private final static int maxAttenuationFilterCount = 3;

    public JPanel getPanel() {
	return ourContent;
    }

    // initialize GUI components
    /** Contructor, initializes image list. */
    public OtfControl(JFrame fr, SimParam inSp) {
	baseframe  = fr;
	this.sp = inSp;
    
	//ourContent.setLayout(new BoxLayout(ourContent, 
	//    BoxLayout.LINE_AXIS));
	ourContent.setLayout(new GridLayout(1,2)); 

	// ------ otf layout ------

	JPanel otfp = new JPanel();
	otfp.setBorder(BorderFactory.createTitledBorder("2 - OTF") );
	otfp.setLayout(new BoxLayout(otfp,BoxLayout.PAGE_AXIS));

	// otf label
	JPanel p0 = new JPanel();
	p0.setLayout(new BoxLayout(p0, BoxLayout.PAGE_AXIS));
	
	otfText.setHorizontalAlignment( SwingConstants.CENTER);
	otfText.setAlignmentX(Component.CENTER_ALIGNMENT);
	otfText.setForeground(Color.RED);
	p0.add(Box.createHorizontalGlue());
	p0.add( Box.createRigidArea( new Dimension(7,7) ));
	p0.add( otfText );
	p0.add( Box.createRigidArea( new Dimension(7,7) ));
	p0.add(Box.createHorizontalGlue());
	otfp.add( p0 );

	JPanel p1 = new JPanel();
	p1.setLayout(new BoxLayout(p1, BoxLayout.LINE_AXIS));
	p1.add( Box.createRigidArea( new Dimension(5,0) ));
	p1.add( Box.createHorizontalGlue() );
	
	// load button
	JButton loadButton = new JButton("Load");
	loadButton.addActionListener( new ActionListener() {
	    @Override
	    public void actionPerformed(ActionEvent e) {
		loadOtf(); 
	    }
	});
	p1.add( loadButton );
	p1.add( Box.createRigidArea( new Dimension(5,0) ));
	

	// approx buttion
	JButton approxButton = new JButton("Approximate");
	approxButton.addActionListener( new ActionListener() {
	    @Override
	    public void actionPerformed(ActionEvent e) {
		displDialog();
	    }
	});
	p1.add( approxButton );
	p1.add( Box.createRigidArea( new Dimension(5,0) ));
	p1.add( Box.createHorizontalGlue() );
	
	otfp.add(p1);
	
	ourContent.add(otfp);
	//ourContent.add(Box.createHorizontalGlue());

	// ------ attenuation ------
	JPanel attp = new JPanel();
	attp.setLayout(new BoxLayout(attp, BoxLayout.PAGE_AXIS));
	attp.setBorder(BorderFactory.createTitledBorder("4 - Attenuation (optional)") );

	// label
	JPanel p2 = new JPanel();
	
	attText.setAlignmentX(Component.CENTER_ALIGNMENT);
	attText.setForeground(Color.RED);
    
	p2.setLayout( new BoxLayout( p2, BoxLayout.PAGE_AXIS)); 
	p2.add( Box.createHorizontalGlue());
	p2.add( Box.createRigidArea( new Dimension(0,7) ));
	p2.add( attText );
	p2.add( Box.createRigidArea( new Dimension(0,7) ));
	p2.add( Box.createHorizontalGlue());
	attp.add( p2 );

	// switches
	JPanel p3 = new JPanel();

	attSw = new Tiles.LComboBox<String>("Use?", "no", "yes" );
	attSw.addSelectListener( new Tiles.SelectListener<String>() {
	    public void selected( String e, int i) {
		if ((i==0)&&( sp.otf()!=null)) {
		    sp.otf().switchAttenuation( false );	   
		    attText.setText( "OTF attenuation off");
		    attText.setForeground(Color.BLUE);

		}	
		if ((i==1)&&(sp.otf()!=null)) {
		    sp.otf().switchAttenuation( true );	    
		    attText.setText( "OTF attenuation on");
		    attText.setForeground(Color.GREEN.darker());

		}
	    }
	});

	// attenuation button
	JButton attButton = new JButton("set");
	attButton.addActionListener( new ActionListener() {
	    @Override
	    public void actionPerformed(ActionEvent e) {
		displAttDialog();
	    }
	});


	p3.add( Box.createHorizontalGlue());
	p3.add( attSw );
	p3.add( attButton );
	p3.add( Box.createHorizontalGlue());

	attp.add( p3 );

	//attp.setMinimumSize( attp.getPreferredSize());
	ourContent.add(attp);

	// if SimParam was loaded from file, there may be an OTF set
	setOtf( sp.otf());


    }




    /** Displays the OTF approx dialog */
    void displDialog() {
	
	double 	defaultNA = 1.4;
	int 	defaultWL = 525;
	OtfProvider.APPROX_TYPE defaultApproxType = OtfProvider.APPROX_TYPE.EXPONENTIAL;
	double  defaultApproxValue = 0.3;

	// Load default values from config, if available
	Conf defaultConf = Tool.getDefaultConfig();
	if (defaultConf!= null) {
		Conf.Folder df;
		try {
			df = defaultConf.r().cd("default-otf");
			defaultNA = df.getDblValue( "NA", defaultNA);
			defaultWL = df.getIntValue( "emission", defaultWL);
			String approxType = df.getStrValue( "estimation-type", "exponential");
			defaultApproxType = OtfProvider.APPROX_TYPE.fromString(approxType);
			defaultApproxValue = df.getDblValue("a-estimate", 0.3);
		} catch (Conf.EntryNotFoundException e) {
			Tool.trace("No OTF defined in default config");
		}
	} else {
		Tool.trace("No default config set, using standard values");
	}

	// NA, lambda, compensation
	final Tiles.LNSpinner naSp = new Tiles.LNSpinner("NA", defaultNA,0.5,1.7,0.01);
	final Tiles.LNSpinner ldSp = new Tiles.LNSpinner("\u03bb",  defaultWL,380,1200,5);
	ldSp.spr.setToolTipText("emission wavelength");
	naSp.spr.setToolTipText("NA objective");
	
	// compensation
	final Tiles.LNSpinner comp = new Tiles.LNSpinner("a", defaultApproxValue, 0.05,1,0.025);

	/*
	final Double [] optsValue = new Double[ 20 ];
	String [] optsLabel = new String[ optsValue.length ];

	for (int i=1; i<=6; i++)		 optsValue[i] = i * 0.025;
	for (int i=7; i<optsValue.length; i++)  optsValue[i] = 0.2 + (i-7) * 0.05;
    
	for (int i=1; i<optsValue.length; i++)	 optsLabel[i] = String.format("a = %5.3f",optsValue[i]);
	
	optsValue[0] = 1.;
	optsLabel[0] = "Ideal";
	

	final Tiles.TComboBox<String> comp = new Tiles.TComboBox<String>(optsLabel);	 // <-- java 1.7
	//final TComboBox comp = new TComboBox(opts); 
	comp.setSelectedIndex(9);
	*/

	comp.setToolTipText("<html><b>Sets deviation from ideal OTF</b><br>"+
	    "Lower valus for a's yield more medium frequency dampening (see manual)<br>"+
	    "Typical values are a=0.2..0.4, so try with default first<br>"
	);

	final Tiles.TComboBox<OtfProvider.APPROX_TYPE> compType 
		= new Tiles.TComboBox<OtfProvider.APPROX_TYPE>( OtfProvider.APPROX_TYPE.values());
		
	compType.setSelectedItem(defaultApproxType);
	compType.setToolTipText("Select the OTF compensation type");



	// build the dialog
	final JDialog otfApr = new JDialog(baseframe,
	    "OTF Approximation", true);
	
	JPanel p1 = new JPanel();
	JPanel p1extra = new JPanel();
	JPanel p2 = new JPanel();
	JPanel p3 = new JPanel();
	p3.setBorder(BorderFactory.createTitledBorder(
	    "OTF approx. parameters") );

	p3.setLayout(new BoxLayout(p3, BoxLayout.PAGE_AXIS));
	
	p1.setLayout(new BoxLayout(p1, BoxLayout.LINE_AXIS));
	p1.add( Box.createHorizontalGlue());
	p1.add( naSp );	
	p1.add( Box.createHorizontalGlue());
	p1.add( ldSp );	
	p1.add( Box.createHorizontalGlue());

	p1.add( new JLabel("Comp:"));
	p1.add( comp );
	p1.add( Box.createHorizontalGlue());
	p1.add( new JLabel("Type:"));
	p1.add( compType );
	
	final JCheckBox setNewDefaultsCB = new JCheckBox();
	setNewDefaultsCB.setText("Set as new defaults?");
	p1extra.add(setNewDefaultsCB);

	JButton ok = new JButton("Set");
	JButton cl = new JButton("Cancel");
	
	// set a new OTF from estimate HERE
	ok.addActionListener( new ActionListener() {
	    public void actionPerformed(ActionEvent e) {
		
		//double aValue = optsValue[ comp.getSelectedIndex() ];
				
		OtfProvider otf = OtfProvider.fromEstimate( 
		    naSp.getVal(), ldSp.getVal(), comp.getVal(), compType.getSelectedItem(), sp.nrBand() );
		setOtf( otf );

		if (setNewDefaultsCB.isSelected()) {

			Conf cfg = Tool.getDefaultConfig();
			if (cfg == null) {
				Tool.error("No default config available", false);				
			} else {
				otf.saveConfig( cfg.r().mk("default-otf"));
				Tool.writeDefaultConfig(cfg);
			}

		}				
		otfApr.dispose();
	    }
	});

	cl.addActionListener( new ActionListener() {
	    public void actionPerformed(ActionEvent e) {
		otfApr.dispose();
	    }
	});
	
	p2.add(ok);
	p2.add(cl);

	p3.add(p1);
	p3.add(p1extra);
	p3.add(p2);
	otfApr.add(p3);
	otfApr.pack();
	otfApr.setVisible(true);

    }

    /** Set a new otf (also updated label). */
    void setOtf( OtfProvider otf ) {
	if (otf==null) return;
	sp.otf( otf );
	// set otf
	otfText.setText( "<html>"+otf.printState(true)+"</html>");
	otfText.setForeground(Color.GREEN.darker());
	otfText.setAlignmentX(Component.CENTER_ALIGNMENT);
	//otfText.setMaximumSize( otfText.getPreferredSize() );
	// set/update attenuation
 	boolean state = otf.isAttenuate();
	attSw.box.setSelectedIndex( (state)?(1):(0));
    }

    /** display an attenuation settings dialog */
    void displAttDialog() {
	
	if ( sp.otf() == null) {
	    JOptionPane.showMessageDialog(baseframe,
		"No OTF has been set to attenuate",
		"OTF not set",
		JOptionPane.ERROR_MESSAGE );
	    return;
	}
	
	JPanel p0 = new JPanel();
	p0.setLayout( new BoxLayout( p0, BoxLayout.PAGE_AXIS ));
	p0.setBorder(BorderFactory.createTitledBorder(
	    "Attenuation parameters") );

	final Tiles.LNSpinner [][] attStr  = new Tiles.LNSpinner[ sp.nrBand() ][maxAttenuationFilterCount];
	final Tiles.LNSpinner [][] attFWHM = new Tiles.LNSpinner[ sp.nrBand() ][maxAttenuationFilterCount];
	final JCheckBox attEnableCB [][] = new JCheckBox[ sp.nrBand() ][maxAttenuationFilterCount];

	for ( int b=0; b<sp.nrBand(); b++) {
		JPanel pPerBand = new JPanel();
		pPerBand.setLayout( new BoxLayout( pPerBand, BoxLayout.PAGE_AXIS ));
		pPerBand.setBorder(BorderFactory.createTitledBorder(String.format("Band %d",b)));
		final int band = b;

		boolean bandEnabled = ((b==0) || (!sp.otf().getBandsShareAttenuation()));

		double [] attPresetStr  = sp.otf().getAttenuationStrengths(b);
		double [] attPresetFWHM = sp.otf().getAttenuationFWHMs(b);

		for ( int fc = 0; fc<maxAttenuationFilterCount; fc++) {

			final int filterCount = fc;
			
			JPanel p1 = new JPanel();
			p1.setLayout( new BoxLayout( p1, BoxLayout.LINE_AXIS ));
			
			attStr[b][fc]  = new Tiles.LNSpinner( "strength" , 
				((attPresetStr.length>fc)?(attPresetStr[fc]):(0.95)) , 0.1, 1.0, 0.0005);
			attFWHM[b][fc] = new Tiles.LNSpinner( "FWHM" , 
				((attPresetFWHM.length>fc)?(attPresetFWHM[fc]):(1.2)), 0.05, 20.0, 0.05);

			attStr[b][fc].spr.setEditor( new JSpinner.NumberEditor( attStr[b][fc].spr, "0.0000"));
			attFWHM[b][fc].spr.setEditor( new JSpinner.NumberEditor( attFWHM[b][fc].spr, "0.00"));
			
			attStr[b][fc].setToolTipText("Strength of the attenuation");
			attFWHM[b][fc].setToolTipText("FWHM of the attenuation");
			
			attStr[b][fc].setEnabled( attPresetStr.length>fc && bandEnabled);
			attFWHM[b][fc].setEnabled( attPresetStr.length>fc && bandEnabled);

			p1.add( Box.createHorizontalGlue());
			p1.add( attStr[b][fc] );
			p1.add( Box.createRigidArea(new Dimension(5,0)));
			p1.add( attFWHM[b][fc] );
			p1.add( Box.createHorizontalGlue());
			
			JPanel p2 = new JPanel();
			p2.setLayout( new BoxLayout( p2, BoxLayout.PAGE_AXIS ));
			attEnableCB[b][fc] = new JCheckBox("enable filter",(attPresetStr.length>fc));

			attEnableCB[b][fc].setEnabled( bandEnabled && fc!=0);
			attEnableCB[b][fc].setSelected( bandEnabled && attPresetStr.length>fc);
			attEnableCB[b][fc].addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					boolean state = attEnableCB[band][filterCount].isSelected();
					attStr[band][filterCount].setEnabled(state);
					attFWHM[band][filterCount].setEnabled(state);
				}
			});
			p2.add(p1);
			p2.add(attEnableCB[b][fc]);
			
			pPerBand.add(p2);
		}
		p0.add(pPerBand);
	}
	
	final JCheckBox linkBandsBox = new JCheckBox("Link bands", sp.otf().getBandsShareAttenuation());
	linkBandsBox.addActionListener( new ActionListener(){
		@Override
		public void actionPerformed( ActionEvent e) {
			boolean state = linkBandsBox.isSelected();
				for (int b=1; b<sp.nrBand(); b++) 
				for (int fc=0; fc<maxAttenuationFilterCount; fc++) {
					attEnableCB[b][fc].setEnabled( (state)?(false):((fc!=0)));
					attEnableCB[b][fc].setSelected( 
						(state)?(b==0 && (fc==0 || attEnableCB[b][fc].isSelected())):
						( fc==0 || attEnableCB[b][fc].isSelected() ));

					attStr[b][fc].setEnabled( (state)?(false):(attEnableCB[b][fc].isSelected()));
					attFWHM[b][fc].setEnabled( (state)?(false):(attEnableCB[b][fc].isSelected()));
				}
			}
		}
	);
	p0.add(linkBandsBox);

	// --------------
	// Handle presets
	// --------------
	
	JPanel presetPanel=new JPanel();
	presetPanel.setLayout( new BoxLayout( presetPanel, BoxLayout.LINE_AXIS ));
	presetPanel.setBorder(BorderFactory.createTitledBorder(
	    "Presets") );

	// read in preset list
	final Tiles.TComboBox<String> presetList = new Tiles.TComboBox<String>();
	presetList.setEditable(true);
	presetPanel.add(presetList);
		
	class PresetHandler {

		final String foldername="attenuation-presets";

		void readInPresets() {
	
			presetList.removeAllItems();

			Conf cfg = Tool.getDefaultConfig();
			if (cfg!=null && cfg.r().contains(foldername)) {
				try {
					List<Conf.Folder> lst = cfg.r().cd(foldername).subfolders();
					for (Conf.Folder f : lst ) {
						presetList.addItemTypesave(f.toString());
					}
				} catch (Conf.EntryNotFoundException e) {
					Tool.error("preset contains attenuation folder, but malformed entries", false);
				}
			}
		}

		void loadPreset(String name) {
			Conf cfg = Tool.getDefaultConfig();
			if (cfg!=null && cfg.r().contains(foldername)) {
				Conf.Folder preset;
				try {						
					preset = cfg.r().cd(foldername).cd(name);
				} catch (Conf.EntryNotFoundException e) {
					Tool.error("No preset named: "+name, false);
					return;
				}
			
			
				for (int b=0;b<sp.nrBand();b++) {
					
					linkBandsBox.setSelected(preset.getBoolValue("attenuationBandsShareFilter",true));
					boolean bandEnable = (!linkBandsBox.isSelected()) || b==0;
					
					for (int fc=0;fc<maxAttenuationFilterCount; fc++) {
						attStr[b][fc].setVal( preset.getDblValue(
							String.format("attenuationStrenght-band_%d-filter_%d",b,fc),.9));
						attFWHM[b][fc].setVal( preset.getDblValue(
							String.format("attenuationFWHM-band_%d-filter_%d",b,fc),.9));
						boolean enableFilter = preset.getBoolValue(
							String.format("attenuationFilterOn-band_%d-filter_%d",b,fc),
							 (fc==0));

						attEnableCB[b][fc].setSelected( enableFilter );
						attEnableCB[b][fc].setEnabled( fc!=0 && bandEnable );
						attStr[b][fc].setEnabled(enableFilter && bandEnable );
						attFWHM[b][fc].setEnabled(enableFilter && bandEnable );
					}
				}
	
			
			}

		} 

		void savePresets(String name) {
			Conf cfg = Tool.getDefaultConfig();
			if (cfg==null) {
				Tool.error("No default config set (check menu)", false);
				return;
			}
				
			Conf.Folder preset = cfg.r().mk(foldername).mk(name);
		
			for (int b=0;b<sp.nrBand();b++) {
				for (int fc=0;fc<maxAttenuationFilterCount; fc++) {
					preset.newDbl(String.format("attenuationStrenght-band_%d-filter_%d",b,fc)).setVal(
						attStr[b][fc].getVal()
					);
					preset.newDbl(String.format("attenuationFWHM-band_%d-filter_%d",b,fc)).setVal(
						attFWHM[b][fc].getVal()
					);
					preset.newBool(String.format("attenuationFilterOn-band_%d-filter_%d",b,fc)).setVal(
						attEnableCB[b][fc].isSelected()
					);
				}
			}
			preset.newBool("attenuationBandsShareFilter").setVal(linkBandsBox.isSelected() );
			Tool.writeDefaultConfig(cfg);
		}

		void removePreset(String name) {
			Conf cfg = Tool.getDefaultConfig();
			if (cfg==null) {
				Tool.error("No default config set (check menu)", false);
				return;
			}
			try {
				cfg.r().cd(foldername).delete(name);
			} catch (Conf.EntryNotFoundException e) {
				Tool.error("No attenuation preset in config file",false);
			}
			Tool.writeDefaultConfig(cfg);
		}


	}

	final PresetHandler psh = new PresetHandler();
	
	psh.readInPresets();

	// save button
	JButton addButton = new JButton("+");
	addButton.setToolTipText("Add new preset (enter name in box) or override selected preset");
	addButton.addActionListener( new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {
			if ( presetList.getSelectedItem() == null || 
				presetList.getSelectedItem().toString().trim().length()<3) {
					Tool.error("Please enter a name (min 3 char) for the preset", false);
					return;
				}
			
			String nameNow = presetList.getSelectedItem().toString().trim();
			psh.savePresets(nameNow);
			Tool.trace("New attenuation preset saved: "+nameNow);
			psh.readInPresets();
			presetList.setSelectedItem(nameNow);
		}
	});
	
	// load button
	JButton loadButton = new JButton("L");
	loadButton.setToolTipText("load the selected preset");
	loadButton.addActionListener( new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {
			if ( presetList.getSelectedItem() == null || 
				presetList.getSelectedItem().toString().trim().length()<3) {
					Tool.error("Please enter a name (min 3 char) for the preset", false);
					return;
				}
			
			String nameNow = presetList.getSelectedItem().toString().trim();
			Tool.trace("Loading attenuation preset: "+nameNow);
			psh.loadPreset(nameNow);			
		}
	});

	// delete button
	JButton deleteButton = new JButton("-");
	deleteButton.setToolTipText("remove the selected preset");
	deleteButton.addActionListener( new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {
			if ( presetList.getSelectedItem() == null || 
				presetList.getSelectedItem().toString().trim().length()<3) {
					Tool.error("Please enter a name (min 3 char) for the preset", false);
					return;
				}
			
			String nameNow = presetList.getSelectedItem().toString().trim();
			psh.removePreset(nameNow);			
			Tool.trace("Removing attenuation preset: "+nameNow);
			psh.readInPresets();
		}
	});


	presetPanel.add(loadButton);
	presetPanel.add(addButton);
	presetPanel.add(deleteButton);
	
	p0.add(presetPanel);








	final JDialog attDialog = new JDialog(baseframe,
	    "OTF Attenuation", true);
	
	JPanel p2 = new JPanel();
	
	// set a new OTF from estimate HERE
	JButton ok = new JButton("Set");
	JButton cl = new JButton("Cancel");
	
	ok.addActionListener( new ActionListener() {
	    public void actionPerformed(ActionEvent e) {
		
			int maxBand = (linkBandsBox.isSelected())?(1):(sp.nrBand());

			for (int band = 0 ; band<maxBand; band++) {
				
				// extract only selected filter channels
				int count=0;
				for (int i=0; i<maxAttenuationFilterCount; i++) {
					if (attEnableCB[band][i].isSelected()) count++;
				}
				double [] attValueStr  = new double[count];
				double [] attValueFWHM = new double[count];
				count=0;
				for (int i=0; i<maxAttenuationFilterCount; i++) {
					if (attEnableCB[band][i].isSelected()) {
						attValueStr[count]=attStr[band][i].getVal();
						attValueFWHM[count]=attFWHM[band][i].getVal();
						count++;
					}
				}
				
				// update OTF
				if (maxBand==1) {
					sp.otf().addAttenuation( attValueStr, attValueFWHM);
				} else {
					sp.otf().addAttenuationPerBand(band, attValueStr, attValueFWHM);
				}
			}
		
		
		if (sp.otf().isAttenuate()) {
		    attText.setText( "OTF attenuation set");
		}
		attDialog.dispose();
	    }
	});

	cl.addActionListener( new ActionListener() {
	    public void actionPerformed(ActionEvent e) {
		attDialog.dispose();
	    }
	});
	
	// build the dialog
	

	p2.add(ok);
	p2.add(cl);
	p0.add(p2);
	attDialog.add(p0);
	attDialog.pack();
	attDialog.setVisible(true);

    }

    /** Load an OTF */
    void loadOtf() {

	File fObj = null;
	JFileChooser fc = new JFileChooser();
	int returnVal = fc.showOpenDialog(baseframe);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            fObj = fc.getSelectedFile();
        }
	else {
	    return;
	}
	
	Conf cfg;
	// try to open a conf object
	try {
	    cfg = Conf.loadFile( fObj.getAbsolutePath());
	} catch ( Conf.SomeIOException e ) {
	    JOptionPane.showMessageDialog( baseframe,
	     e.toString(), "Error loading file",
	     JOptionPane.ERROR_MESSAGE);
	    return;
	}
	// try to load the OTF
	OtfProvider otfloaded;
	try {
	    otfloaded = OtfProvider.loadFromConfig( cfg );
	} catch ( Conf.EntryNotFoundException e ) {
	    JOptionPane.showMessageDialog( baseframe,
	     "OTF not complete:\n"+e.toString(), "Error loading OTF",
	     JOptionPane.ERROR_MESSAGE);
	    return;
	}

	if ( otfloaded != null)
	    setOtf( otfloaded );

    }




    /** for testing */
    public static void main(String [] args ) {

	if (args.length>0)
		Tool.setMockKeyValueForDefaultConfig( args[0]);
	
	JFrame test = new JFrame("Test OTF GUI");
	OtfControl oc = new OtfControl(test, SimParamGUI.dummySP());
	test.add(oc.getPanel());
	test.pack();
	test.setVisible(true);
	test.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE);
    }


}

