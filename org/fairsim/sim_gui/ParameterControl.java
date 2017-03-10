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
public class ParameterControl { 
    
    private final JPanel ourContent = new JPanel();  
    private final JLabel ourState = new JLabel("Parameters not known");
    
    private final JFrame baseframe;
    private final SimParam simParam;
    private final SimParamGUI simp;
    private final ImageControl imgc;
    private final ReconstructionControl recc;
    private ImageDisplay.Factory idpFactory ;


    private volatile boolean running = false;

    private int fitVerbosity = 1;   // verbosity of fit output
    private int fitBand	=2;	    // which band to use for fitting
    private double fitExclude=0.6;  // Portion of OTF support to exclude

    public JPanel getPanel() {
	return ourContent;
    }

    /** Contructor, initializes image list. */
    public ParameterControl( final JFrame baseframe, 
	final ImageDisplay.Factory idpFactory,
	final ImageControl imgc, 
	final SimParam simParam, final SimParamGUI simp ,
	final ReconstructionControl recc,
	boolean initialized ) {

	// initialize variables
	this.baseframe	= baseframe;
	this.simParam	= simParam;
	this.idpFactory = idpFactory;
	this.imgc	= imgc;
	this.simp	= simp;
	this.recc	= recc;

	// create our pnael
	ourContent.setLayout(new BoxLayout(ourContent, BoxLayout.PAGE_AXIS));
	ourContent.setBorder(BorderFactory.createTitledBorder("3 - Parameter estimation") );

	// setup label
	ourState.setAlignmentX(Component.CENTER_ALIGNMENT);
	ourState.setForeground(Color.RED);

	JPanel p1 = new JPanel();
	p1.setLayout(new BoxLayout(p1, BoxLayout.LINE_AXIS));

	JButton configEstimationButton = new JButton("setup");
	configEstimationButton.setToolTipText("Change parameters and output for the parameter estimation");
	p1.add(configEstimationButton);
	p1.add(Box.createRigidArea(new Dimension(5,10)));

	JButton runEstimationButton = new JButton("run");
	runEstimationButton.setToolTipText("Start the paramemeter estimation");
	p1.add(runEstimationButton);

	ourContent.add( Box.createRigidArea( new Dimension(0,5)));
	ourContent.add( ourState );
	ourContent.add( Box.createRigidArea( new Dimension(0,5)));
	ourContent.add( p1 );

	// add listener
	runEstimationButton.addActionListener( new ActionListener() {
	    public void actionPerformed(ActionEvent e ) {
		runEstimation();		    
	    }
	});
	// add listener
	configEstimationButton.addActionListener( new ActionListener() {
	    public void actionPerformed(ActionEvent e ) {
		configureEstimation();		    
	    }
	});

	// if initialized (from loaded data), set label and state accordingly
	if ( initialized ) {
		ourState.setText("Loaded from file");
		recc.ourState.setText( "Parameters set, ready" );
		recc.ourState.setForeground(Color.BLUE);
		recc.paramAvailable = true ;
		ourState.setForeground(Color.GREEN.darker());
	}


    }


    /** Called by the 'run'-button, performes the parameter estimation */
    void runEstimation() {

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
	     "Estimation running, please wait", "fairSIM error",
	     JOptionPane.ERROR_MESSAGE);
	    return;
	}

	// run
	ourState.setText("Running estimation");
	ourState.setToolTipText("Estimation performed in background");
	ourState.setForeground(Color.BLUE);

	final int bandToFit = Math.min(fitBand, simParam.nrBand()-1);

	(new SwingWorker<Object, Object> () {
	
	    Tool.Timer t1 = Tool.getTimer();

	    @Override
	    public Object doInBackground() {
		running = true;
		//try {
		    SimAlgorithm.estimateParameters( 
			simParam, imgc.theFFTImages, bandToFit, fitExclude, 
			idpFactory, fitVerbosity, t1);
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
		recc.ourState.setText( ((recc.paramAvailable)?("New p"):("P"))+"arameters set, ready");
		recc.ourState.setForeground(Color.BLUE);
		recc.paramAvailable = true ;
		ourState.setToolTipText("Estimation took "+t1);
		ourState.setForeground(Color.GREEN.darker());
	    }
	}).execute();

    }

    /** Called by the setup button, displays a setup dialog */
    void configureEstimation() {
    
	// panel	
	JPanel p1 = new JPanel();
	p1.setLayout(new BoxLayout(p1, BoxLayout.PAGE_AXIS));
	
	// setup box for amount of feedback
	String [] verbosity = new String [] { "none", "standard", "most", "full" };
	final Tiles.LComboBox<String> verbosityBox = 
	    new Tiles.LComboBox<String>("Intermediate results", verbosity );
	verbosityBox.box.setToolTipText("<html>Amount of intermediate result to display<br />"+
	    "(Fit is unaffected by this setting)</html>");
	verbosityBox.box.setSelectedIndex(fitVerbosity);
	p1.add( verbosityBox);
	p1.add(Box.createRigidArea(new Dimension(0,5)));

	// setup which band to use for fits
	Integer [] bands = new Integer [simParam.nrBand()-1];
	for ( int i=0; i<bands.length; i++ )
	    bands[i]=i+1;

	final Tiles.LComboBox<Integer>  fitBandBox  
		= new Tiles.LComboBox<Integer>("Band to use for shift vector est.", bands );
	fitBandBox.setToolTipText("<html>Which band to fit band 0 against<br >"+
	    "for shift vector estimation<br />"+
	    "Band 2 (default) is more precise<br />"+
	    "Band 1 may be more stable.<br />"+
	    "Use band 1 if the band 2 failes<br />"+
	    "i.e. for data with little fine structure");
	fitBandBox.box.setSelectedItem(fitBand);
	p1.add( fitBandBox);
	p1.add(Box.createRigidArea(new Dimension(0,5)));

	// setup how much to exclude
	Double [] excludes = new Double [] { .2,.3,.4,.5,.6,.7,.8 };
	final Tiles.LComboBox<Double> fitExclBox
	     = new Tiles.LComboBox<Double>("Region to exclude from fit",excludes);
	fitExclBox.setToolTipText("<html>In fraction of OTF support, how much<br>"
	    +"of low frequency region not to search for peak<br>"
	    +"(marked by circle in the output)");

	fitExclBox.box.setSelectedIndex( 4 );
	p1.add( fitExclBox );
	p1.add(Box.createRigidArea(new Dimension(0,5)));




	// dialog	
	final JDialog configDialog = new JDialog(baseframe,
	    "Parameter estimation setting", true);
	
	// ok and cancel buttons
	JButton ok = new JButton("Set");
	JButton cl = new JButton("Cancel");
	ok.addActionListener( new ActionListener() {
	    public void actionPerformed(ActionEvent e) {
		
		fitVerbosity = verbosityBox.getSelectedIndex();
		fitBand	     = fitBandBox.getSelectedItem();
		fitExclude   = fitExclBox.getSelectedItem();
		
		configDialog.dispose();
	    }
	});

	cl.addActionListener( new ActionListener() {
	    public void actionPerformed(ActionEvent e) {
		configDialog.dispose();
	    }
	});
   
	JPanel p2 = new JPanel();
	p2.add( ok );
	p2.add( cl );
	    
	// The dialog itself
	JPanel p3 = new JPanel();
	p3.setLayout( new BoxLayout(p3, BoxLayout.PAGE_AXIS));
	p3.setBorder(BorderFactory.createTitledBorder(
	    "Parameter estimation settings") );
	p3.add( p1 );
	p3.add( p2 );

	configDialog.add( p3 );
	configDialog.pack();
	configDialog.setVisible(true);
    
    }


    public int getFitBand() {
	return fitBand;
    }

    public double getFitExclude() {
	return fitExclude;
    }


}

