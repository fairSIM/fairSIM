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
public class FairSimGUI {

    final JFrame baseframe;
    final private JPanel mainPanel;
    
    final OtfControl		otfc; 
    final SimParamGUI		simp;
    final ImageControl		imgc;
    final ParameterControl	parc;
    final ReconstructionControl recc;

    // create and pack the control interface
    public FairSimGUI( 
	SimParam sp, ImageSelector is, 
	ImageDisplay.Factory imgFactory,
	boolean initialized
	) {
	baseframe = new JFrame("fairSIM");
	mainPanel = new JPanel();
	mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.PAGE_AXIS));

	// set the logo
	URL logo = getClass().getResource("/org/fairsim/resources/fairSimLogo.png");
	if ( logo != null) {
	    baseframe.setIconImage(new ImageIcon( logo ).getImage());
	}

	imgc = new ImageControl(baseframe, is, imgFactory, this, sp );
	simp = new SimParamGUI(baseframe, sp );
	otfc = new OtfControl(baseframe, sp);
	recc = new ReconstructionControl(baseframe, imgFactory, imgc, sp, simp );
	parc = new ParameterControl(baseframe, imgFactory, imgc, sp, simp, recc, initialized );
	

	mainPanel.add( imgc.getPanel() );
	mainPanel.add( otfc.getPanel() );
	
	JPanel p1 = new JPanel();
	//p1.setLayout(new BoxLayout(p1, BoxLayout.LINE_AXIS));
	p1.setLayout(new GridLayout(1,2));
	p1.add( parc.getPanel() );
	p1.add( recc.getPanel() );
	
	mainPanel.add( p1 );
	mainPanel.add( simp.getPanel() );


	baseframe.add(mainPanel);
	baseframe.pack();
	baseframe.setVisible(true);
    }



    /** Show a dialog to setup a new SimParam object */
    public static SimParam newSpDialog(final Frame baseframe) {

	// build the dialog
	final JDialog simDialog = new JDialog(baseframe, true);
	final Integer [] pha2Band = new Integer [] {3,4,5,6,7,8,9};
	final Integer [] pha3Band = new Integer [] {5,6,7,8,9};
	

	JPanel p1 = new JPanel();
	JPanel p2 = new JPanel();
	JPanel p3 = new JPanel();
	p3.setBorder(BorderFactory.createTitledBorder(
	    "New SIM reconstruction") );

	final Tiles.LComboBox<SimParam.IMGSEQ> imgSeq = 
	    new Tiles.LComboBox<SimParam.IMGSEQ>("Type/Img.Seq.", SimParam.IMGSEQ.values());
	imgSeq.box.setSelectedIndex(1);
	imgSeq.box.setToolTipText("<html><b>Sequence of images</b><br />"+
	    "(i.e. order of angles, phases, z-stack)<br />"+
	    "if set to a microscope type, also sets number of<br />"+
	    "angles, bands and phases accordingly<html>");
	    

	final Tiles.LComboBox<Integer> nrBands = 
	    new Tiles.LComboBox<Integer>("beams", new Integer [] {2,3});
	nrBands.box.setSelectedItem(3);
	
	final Tiles.LComboBox<Integer> nrDir = 
	    new Tiles.LComboBox<Integer>("angles", new Integer [] {1,2,3,4,5,6,7});
	nrDir.box.setSelectedItem(3);
	
	final Tiles.LComboBox<Integer> nrPha = 
	    new Tiles.LComboBox<Integer>("phases", pha3Band );
	nrPha.box.setSelectedItem(5);


	p3.setLayout(new BoxLayout(p3, BoxLayout.PAGE_AXIS));

	p3.add( imgSeq );	
	p3.add( Box.createRigidArea(new Dimension(0,5)));
	p1.setLayout(new BoxLayout(p1, BoxLayout.LINE_AXIS));
	p1.add( Box.createHorizontalGlue());
	p1.add( nrBands );	
	p1.add( Box.createRigidArea(new Dimension(3,5)));
	p1.add( nrDir );	
	p1.add( Box.createRigidArea(new Dimension(3,5)));
	p1.add( nrPha );	
	p1.add( Box.createHorizontalGlue());

	JButton ok = new JButton("Set");
	JButton cl = new JButton("Cancel");
	p2.add(ok);
	p2.add(cl);
	p3.add(p1);
	p3.add( Box.createRigidArea(new Dimension(0,5)));
	p3.add(p2);
	
	// automatically change to defaults if a microscope is selected
	imgSeq.addSelectListener( new Tiles.SelectListener<SimParam.IMGSEQ> () {
	    public void selected( SimParam.IMGSEQ w, int ignore ) {
		// for omx, set defaults
		if ( w == SimParam.IMGSEQ.PZA ) {
		    nrBands.box.setSelectedItem(3);
		    nrDir.box.setSelectedItem(3);
		    nrPha.box.setSelectedItem(5);
		}
		// for zeiss, set defaults
		if ( w == SimParam.IMGSEQ.ZAP ) {
		    nrBands.box.setSelectedItem(3);
		    nrDir.box.setSelectedItem(5);
		    nrPha.box.setSelectedItem(5);
		}
	    }
	});
   
	// only allow NrOfPhases that makes sense
	nrBands.addSelectListener( new Tiles.SelectListener<Integer> () {
	    public void selected( Integer bands, int idxIgnore ) {
		if (bands==2)
		    nrPha.newElements( pha2Band );
		if (bands==3)
		    nrPha.newElements( pha3Band );
	    }
	});

	final Tiles.Container<Boolean> isOk = new Tiles.Container<Boolean>(false);

	ok.addActionListener( new ActionListener() {
	    public void actionPerformed(ActionEvent e) {
		isOk.set(true);
		simDialog.dispose();
	    }
	});
	
	cl.addActionListener( new ActionListener() {
	    public void actionPerformed(ActionEvent e) {
		simDialog.dispose();
	    }
	});

	// display the dialog
	simDialog.add(p3);
	simDialog.pack();
	simDialog.setVisible(true);

	// create the response
	if (isOk.get()) {
	    SimParam sp = SimParam.create( 
		nrBands.getSelectedItem(),
		nrDir.getSelectedItem(),
		nrPha.getSelectedItem(),
		512,
		0.2,
		null);
	    sp.setImgSeq( imgSeq.getSelectedItem());
	    Tool.trace("Initialized fairSIM: new reconstruction");
	    return sp;
	
	}

	return null;
    }

    /** Initialize a new SimParam by displaying a FileChooser */
    public static SimParam fromFileChooser( final Frame baseframe ) 
	throws Conf.SomeIOException, Conf.EntryNotFoundException {

	JFileChooser fc = new JFileChooser();
	int returnVal = fc.showOpenDialog(baseframe);

        if (returnVal == JFileChooser.APPROVE_OPTION) {
            String fname = fc.getSelectedFile().getAbsolutePath();
	    Conf cfg = Conf.loadFile( fname );

	    SimParam sp = SimParam.loadConfig( cfg.r() );
	    if ( cfg.r().contains("otf2d")) {
		OtfProvider otf = OtfProvider.loadFromConfig( cfg );
		sp.otf( otf );
	    }
	    return sp;
        }
	
	return null;
    }




    /** for testing */
    public static void main( String [] arg ) 
	throws Exception {

	int nImg = ( arg.length > 1 )?(Integer.parseInt( arg[1] )):(4);
	
	if (arg.length<1) {
	    System.out.println("Use: n - new,  l - load , [nrImages] ");
	    return;
	}   

	JFrame tmp = new JFrame();
	tmp.pack();

	SimParam sp = null;
	boolean load=false;
	
	if ( arg[0].equals("n") ) {
	    sp = newSpDialog( tmp );
	}
	if ( arg[0].equals("l") ) {
	    sp = fromFileChooser( tmp );
	    load=true;
	}


	if (sp==null) return;

	FairSimGUI a =  new FairSimGUI( 
	    //SimParamGUI.dummySP(), 
	    sp,
	    new ImageSelector.Dummy(nImg),
	    DisplayWrapper.getTestingFactory(),
	    load
	    );
	a.baseframe.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE);
    }


}



