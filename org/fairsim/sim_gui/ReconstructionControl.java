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
import javax.swing.JOptionPane;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;
import javax.swing.SwingWorker;

import java.awt.Dimension;
import java.awt.ComponentOrientation;
import java.awt.Component;
import java.awt.Color;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import org.fairsim.utils.Tool;
import org.fairsim.utils.ImageSelector;
import org.fairsim.utils.ImageDisplay;

import org.fairsim.sim_algorithm.OtfProvider;
import org.fairsim.sim_algorithm.SimParam;
import org.fairsim.sim_algorithm.SimAlgorithm;

import org.fairsim.linalg.Vec2d;

/**
 * GUI elements to control parameter fitting
 **/
public class ReconstructionControl { 
    
    private final JPanel ourContent = new JPanel();  
    final JLabel ourState  = new JLabel("Parameters not known");
    boolean paramAvailable = false;

    private final JFrame baseframe;
    private final SimParam simParam;
    private final SimParamGUI simp;
    private final ImageControl imgc;
    private ImageDisplay.Factory idpFactory ;

    private String [] verbosityString
	= new String [] { "result only", "standard", "more", "most", "full" };

    private volatile boolean running = false;

    int verbosity=0;

    public JPanel getPanel() {
	return ourContent;
    }

    /** Contructor, initializes image list. */
    public ReconstructionControl( final JFrame baseframe, 
	final ImageDisplay.Factory idpFactory,
	final ImageControl imgc, 
	final SimParam simParam, final SimParamGUI simp ) {

	// initialize variables
	this.baseframe	= baseframe;
	this.simParam	= simParam;
	this.idpFactory = idpFactory;
	this.imgc	= imgc;
	this.simp	= simp;

	// create our pnael
	ourContent.setLayout(new BoxLayout(ourContent, BoxLayout.PAGE_AXIS));
	ourContent.setBorder(BorderFactory.createTitledBorder("5 - Reconstruction") );

	// setup label
	ourState.setAlignmentX(Component.CENTER_ALIGNMENT);
	ourState.setForeground(Color.RED);

	JPanel p1 = new JPanel();
	p1.setLayout(new BoxLayout(p1, BoxLayout.LINE_AXIS));

	JButton setupRecon = new JButton("setup");
	p1.add(setupRecon);
	p1.add(Box.createRigidArea(new Dimension(5,0)));

	JButton runRecon = new JButton("run");
	p1.add(runRecon);

	ourContent.add( Box.createRigidArea( new Dimension(0,5)));
	ourContent.add( ourState );
	ourContent.add( Box.createRigidArea( new Dimension(0,5)));
	ourContent.add( p1 );

	// add listener
	runRecon.addActionListener( new ActionListener() {
	    public void actionPerformed(ActionEvent e ) {
		runRecon();		    
	    }
	});
	// add listener
	setupRecon.addActionListener( new ActionListener() {
	    public void actionPerformed(ActionEvent e ) {
		displayDialog();		    
	    }
	});

    }

    /** display the options dialog */
    void displayDialog() {

	final JDialog dialog = new JDialog(baseframe,"Setup reconstruction",true);

	JPanel p1 = new JPanel(); 
	p1.setLayout(new BoxLayout( p1, BoxLayout.PAGE_AXIS));
	
	// setup box for amount of feedback
	// used in dialog ...
	final Tiles.LComboBox<String> verbosityBox = 
	    new Tiles.LComboBox<String>("Intermediate results", verbosityString );
	verbosityBox.box.setToolTipText("<html>Amount of intermediate result to display</html>");
	verbosityBox.box.setSelectedIndex(verbosity+1);
	p1.add( verbosityBox );
	p1.add(Box.createRigidArea(new Dimension(0,5)));
	
	// image scaling box
	final Tiles.LComboBox<SimParam.CLIPSCALE> imgScaleBox = 
	    //new Tiles.LComboBox<String>("Output", "raw", "clip", "clip&scale" );
	    new Tiles.LComboBox<SimParam.CLIPSCALE>("Output", 
		SimParam.CLIPSCALE.values() );
	imgScaleBox.box.setToolTipText("<html>raw: no scaling, keep negative values<br />"+
	    "clip zeros: remove negative values<br />"+
	    "clip&scale: scale output to 0..255<br />"+
	    "(only effects 3 main results, not intermediate output)");
	imgScaleBox.box.setSelectedIndex( 0 );
	
	p1.add( imgScaleBox );
	p1.add(Box.createRigidArea(new Dimension(0,5)));

	// Filter selection
	final Tiles.LComboBox<SimParam.FilterStyle> filterTypeBox = 
	    new Tiles.LComboBox<SimParam.FilterStyle>("Filter type",
		SimParam.FilterStyle.values());

	p1.add( filterTypeBox );
	p1.add(Box.createRigidArea(new Dimension(0,5)));

	// Wiener parameter
	final Tiles.LNSpinner wienerParam = new Tiles.LNSpinner("Wiener parameter",
	    simParam.getWienerFilter(), 
	    Math.min( 0.005, simParam.getWienerFilter()), 
	    Math.max(   0.5, simParam.getWienerFilter()), 0.0025);
	p1.add( wienerParam );
	p1.add(Box.createRigidArea(new Dimension(0,5)));

	// APO cutoff
	final Tiles.LNSpinner apoCutOff = new Tiles.LNSpinner("APO cutoff",
	    simParam.getApoCutoff(), 1.0, 2.5, 0.1 );
	apoCutOff.spr.setToolTipText("<html>Cutoff freq. of the apotization<br />"
	    +"as factor of OTF cutoff.<br /> Set below 2 if the"
	    +"dataset does not reach full resolution enhancement");
	
	p1.add( apoCutOff );
	p1.add(Box.createRigidArea(new Dimension(0,5)));
	    
	// RL max iteration counter
	final Tiles.LNSpinner rlInterationCount = new Tiles.LNSpinner("RL iterations",
	    simParam.getRLiterations(), 1, 200, 1 );

	p1.add( rlInterationCount );
	p1.add(Box.createRigidArea(new Dimension(0,5)));

	
	// ok and cancel buttons
	JButton ok = new JButton("Set");
	JButton cl = new JButton("Cancel");
	ok.addActionListener( new ActionListener() {
	    public void actionPerformed(ActionEvent e) {
		verbosity = verbosityBox.getSelectedIndex()-1;
		simParam.setFilterStyle( filterTypeBox.getSelectedItem() );
		simParam.setWienerFilter( wienerParam.getVal());
		simParam.setApoCutoff( apoCutOff.getVal());
		simParam.setRLiterations( (int)rlInterationCount.getVal());	
		simParam.setClipScale( imgScaleBox.getSelectedItem());
		dialog.dispose();
	    }
	});

	cl.addActionListener( new ActionListener() {
	    public void actionPerformed(ActionEvent e) {
		dialog.dispose();
	    }
	});
   
	JPanel p2 = new JPanel();
	p2.add( ok );
	p2.add( cl );
	    
	// The dialog itself
	JPanel p3 = new JPanel();
	p3.setLayout( new BoxLayout(p3, BoxLayout.PAGE_AXIS));
	p3.setBorder(BorderFactory.createTitledBorder("Reconstruction"));
	p3.add( p1 );
	p3.add( p2 );

	dialog.add( p3);
	dialog.pack();
	dialog.setVisible(true);

    }



    /** just here to not clutter the ActionPerformed event with so much code */
    void runRecon() {

	// rudimentare checks 
	if ( simParam.otf() == null ) {
	    JOptionPane.showMessageDialog( baseframe,
	     "No OTF available", "fairSIM error",
	     JOptionPane.ERROR_MESSAGE);
	    return;
	}
	if ( imgc.theFFTImages == null ) {
	    JOptionPane.showMessageDialog( baseframe,
	     "No images available", "fairSIM error",
	     JOptionPane.ERROR_MESSAGE);
	    return;
	}
	if ( running ) {
	    JOptionPane.showMessageDialog( baseframe,
	     "Reconstruction running, please wait", "fairSIM error",
	     JOptionPane.ERROR_MESSAGE);
	    return;
	}
	if ( !paramAvailable ) {
	    JOptionPane.showMessageDialog( baseframe,
	     "<html>Reconstruction parameters not set<br>Run fit first</html>", 
	     "fairSIM error",
	     JOptionPane.ERROR_MESSAGE);
	    return;
	}

	// run
	ourState.setText("Running reconstruction");
	ourState.setToolTipText("Reconstruction performed in background");
	ourState.setForeground(Color.BLUE);

	/*
	Tool.Timer t1 = Tool.getTimer();
	SimAlgorithm.runReconstruction( 
		    simParam, imgc.theFFTImages, idpFactory,  2, false, t1);
		ourState.setText("Complete");
		ourState.setToolTipText("Reconstruction took "+t1);
		ourState.setForeground(Color.GREEN.darker());

	*/
	(new SwingWorker<Object, Object> () {
	
	    Tool.Timer t1 = Tool.getTimer();

	    @Override
	    public Object doInBackground() {
		running = true;	
		//try {
		    SimAlgorithm.runReconstruction( 
			simParam, imgc.theFFTImages, 
			idpFactory,  verbosity, false, 
			simParam.getClipScale(), t1);
		/*} catch (Exception e) {
		    Tool.trace("Problem: "+e);
		    e.printStackTrace();
		}*/
		return null;
	    }
	    @Override
	    protected void done() {
		running = false;
		simp.refreshTable();
		ourState.setText("Complete");
		ourState.setToolTipText("Reconstruction took "+t1);
		ourState.setForeground(Color.GREEN.darker());
	    }
	}).execute();
    }

	
}

