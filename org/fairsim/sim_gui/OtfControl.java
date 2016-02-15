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
import javax.swing.JLabel;

import javax.swing.Box;
import javax.swing.SwingConstants;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;
import javax.swing.JOptionPane;
import javax.swing.JSpinner;
import javax.swing.JFileChooser;

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
		    attText.setText( String.format("a=%5.3f, FWHM=%4.2f",
			sp.otf().getAttStr(0), sp.otf().getAttFWHM(0)));
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
	
	// NA, lambda, compensation
	final Tiles.LNSpinner naSp = new Tiles.LNSpinner("NA", 1.4,0.5,1.7,0.01);
	final Tiles.LNSpinner ldSp = new Tiles.LNSpinner("\u03bb",  525,380,1200,5);
	ldSp.spr.setToolTipText("emission wavelength");
	naSp.spr.setToolTipText("NA objective");
	
	// compensation
	String [] opts = new String[ 8 ];
	for (int i=0;i<8;i++) opts[i] = String.format("a=%4.2f",0.10+i*0.05);
	opts[0] = "Ideal";
	final Tiles.TComboBox<String> comp = new Tiles.TComboBox<String>(opts);	 // <-- java 1.7
	//final TComboBox comp = new TComboBox(opts); 
	comp.setSelectedIndex(4);
	comp.setToolTipText("<html><b>Sets deviation from ideal OTF</b><br>"+
	    "Lower valus for a's yield more medium frequency dampening (see manual)<br>"+
	    "Typical values are a=0.2..0.4, so try with default first<br>"
	);

	// build the dialog
	final JDialog otfApr = new JDialog(baseframe,
	    "OTF Approximation", true);
	
	JPanel p1 = new JPanel();
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

	JButton ok = new JButton("Set");
	JButton cl = new JButton("Cancel");
	
	// set a new OTF from estimate HERE
	ok.addActionListener( new ActionListener() {
	    public void actionPerformed(ActionEvent e) {
		
		double aValue = comp.getSelectedIndex()*0.05+0.10;
		if (comp.getSelectedIndex()==0) aValue=1;
		
		OtfProvider otf = OtfProvider.fromEstimate( 
		    naSp.getVal(), ldSp.getVal(), aValue );
		setOtf( otf );
		
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

	final Tiles.LNSpinner [] attStr  = new Tiles.LNSpinner[ sp.nrBand() ];
	final Tiles.LNSpinner [] attFWHM = new Tiles.LNSpinner[ sp.nrBand() ];
	//for ( int b=0; b<sp.nrBand(); b++) {
	{
	    // TODO: This is largely set up to support different
	    // values for different bands... complete
	    final int b=0;
	    JPanel p1 = new JPanel();
	    p1.setLayout( new BoxLayout( p1, BoxLayout.LINE_AXIS ));

	    attStr[b]  = new Tiles.LNSpinner( "strength" , sp.otf().getAttStr(b) , 0.1, 1.0, 0.0005);
	    attFWHM[b] = new Tiles.LNSpinner( "FWHM" , sp.otf().getAttFWHM(b) , 0.1, 6.0, 0.05);

	    attStr[b].spr.setEditor( new JSpinner.NumberEditor( attStr[b].spr, "0.0000"));
	    attFWHM[b].spr.setEditor( new JSpinner.NumberEditor( attFWHM[b].spr, "0.00"));
	    

	    attStr[b].setToolTipText("Strength of the attenuation");
	    attFWHM[b].setToolTipText("FWHM of the attenuation");

	     attStr[b].setEnabled( (b==0) );
	    attFWHM[b].setEnabled( (b==0) );

	    p1.add( Box.createHorizontalGlue());
	    p1.add( attStr[b] );
	    p1.add( Box.createRigidArea(new Dimension(5,0)));
	    p1.add( attFWHM[b] );
	    p1.add( Box.createHorizontalGlue());
	    p0.add(p1);
	}
	
	final JDialog attDialog = new JDialog(baseframe,
	    "OTF Attenuation", true);
	
	JPanel p2 = new JPanel();
	
	// set a new OTF from estimate HERE
	JButton ok = new JButton("Set");
	JButton cl = new JButton("Cancel");
	
	ok.addActionListener( new ActionListener() {
	    public void actionPerformed(ActionEvent e) {
		sp.otf().setAttenuation( attStr[0].getVal(), attFWHM[0].getVal());
		if (sp.otf().isAttenuate()) {
		    attText.setText( String.format("a=%5.3f, FWHM=%4.2f",
			sp.otf().getAttStr(0), sp.otf().getAttFWHM(0)));
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
	JFrame test = new JFrame("Test OTF GUI");
	OtfControl oc = new OtfControl(test, SimParamGUI.dummySP());
	test.add(oc.getPanel());
	test.pack();
	test.setVisible(true);
	test.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE);
    }


}

