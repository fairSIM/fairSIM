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
import javax.swing.JCheckBox;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.JSlider;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;
import javax.swing.JProgressBar;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;

import java.awt.Dimension;
import java.awt.ComponentOrientation;
import java.awt.Component;
import java.awt.Color;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import javax.swing.event.PopupMenuListener;
import javax.swing.event.PopupMenuEvent;

import org.fairsim.utils.Tool;
import org.fairsim.utils.ImageSelector;
import org.fairsim.utils.ImageDisplay;

import org.fairsim.sim_algorithm.OtfProvider;
import org.fairsim.sim_algorithm.SimParam;
import org.fairsim.sim_algorithm.SimUtils;
import org.fairsim.sim_algorithm.SimAlgorithm;

import org.fairsim.linalg.Vec2d;
import org.fairsim.linalg.Transforms;

/**
 * GUI elements to set up OTF
 **/
public class ImageControl { 
    
    private final JPanel ourContent = new JPanel();  
    private final JLabel ourState = new JLabel("no image selected");
    private volatile boolean running = false;

    private final ImageSelector imgSelect ;
    //private final ImageSelector.Callback imgSelectCB;
    
    private final JFrame baseframe;
    private final SimParam simParam;
    private final FairSimGUI fsGUI;
    private ImageDisplay.Factory idpFactory ;

    // global references to be accessed both by importImage and showSlices
    private JButton importImageButton	= null;
    private JCheckBox importTimelapseBox= null;
    private JButton batchButton		= null;
    private JButton showSlicesButton	= null;
    private ImageDisplay sliceSelector	= null;
    private Tiles.LComboBox<Integer> sliceBox;
	
    final Tiles.LComboBox<ImageSelector.ImageInfo> imgBox;
    
    private Tiles.LComboBox<String> bgrBox;
    private Tiles.LNSpinner bgrSpinner;

    // video position slider
    private final JSlider videoPosSlider;
    final JLabel  videoPosLabel = new JLabel( String.format("t % 5d", 0));
    final JLabel maxTimePointsLabel;
    final Tiles.LNSpinner zSliceVideoSpinner ; 
    final String [] updateModes = {"off", "widefield", "recon", "recon+par.est."};
    final Tiles.LComboBox<String> videoAutoUpdateMode =
     new Tiles.LComboBox<String>("auto-update", updateModes); 

    // the images
    Vec2d.Real [][] theImages    =null;
    Vec2d.Cplx [][] theFFTImages =null;
    double pxlSize;
    boolean pxlSet;
    
    private ImageDisplay rawDataDisplay   = null;
    private ImageDisplay simPreviewDisplay = null;

    // video functions
    boolean isVideoStack = false;
    int videoStackPositionTime = 0;
    int videoStackPositionZ =0;
    boolean sucessfulImport = false;
    int totalTimePoints = 1;

    AutoUpdateThread autoUpdater = null;

    public JPanel getPanel() {
	return ourContent;
    }

    /** Contructor, initializes image list. */
    public ImageControl( final JFrame baseframe, ImageSelector is, 
	final ImageDisplay.Factory imgFactory, 	FairSimGUI fsg, SimParam sp ) {

	// initialize variables
	this.baseframe    = baseframe;
	this.imgSelect    = is;
	this.simParam     = sp;
	this.idpFactory   = imgFactory;
	this.fsGUI        = fsg;

	// create our pnael
	ourContent.setLayout(new BoxLayout(ourContent, BoxLayout.PAGE_AXIS));
	ourContent.setBorder(BorderFactory.createTitledBorder("1 - Image Selector") );

	// setup label
	ourState.setAlignmentX(Component.CENTER_ALIGNMENT);
	ourState.setForeground(Color.RED);

	// setup selector box
	ImageSelector.ImageInfo [] openImages = imgSelect.getOpenImages();
	imgBox = new Tiles.LComboBox<ImageSelector.ImageInfo>(
	    "Img", null, true, openImages);
	importImageButton  = new JButton("import");
	importTimelapseBox  = new JCheckBox("timelapse mode");
	
	zSliceVideoSpinner = 	
	    new Tiles.LNSpinner( "zSlices" , 1, 1, 200, 1 ); 

	imgBox.box.setPrototypeDisplayValue("some really long image filename"+
	    "as these will often be used");

	maxTimePointsLabel = new JLabel("t-max: n/a");
	batchButton = new JButton("batch");

	JPanel row1 = new JPanel();
	row1.setLayout(new BoxLayout(row1, BoxLayout.LINE_AXIS));
	row1.add(Box.createHorizontalGlue());
	row1.add( imgBox );
	row1.add(Box.createRigidArea(new Dimension(5,1)));
	row1.add( importImageButton );
	
	JPanel row2 = new JPanel();
	row2.add(Box.createHorizontalGlue());
	row2.add( importTimelapseBox );
	row2.add( zSliceVideoSpinner);
	row2.add(Box.createRigidArea(new Dimension(5,1)));
	row2.add( maxTimePointsLabel );
	row2.add(Box.createRigidArea(new Dimension(5,1)));
	row2.add( videoAutoUpdateMode );
	row2.add(Box.createHorizontalGlue());
	
	// add the video-select sliders
	JPanel row3 = new JPanel();
	
	videoPosSlider = new JSlider(JSlider.HORIZONTAL, 1,100,1);
	videoPosSlider.setEnabled(false);

	JPanel sliders = new JPanel(new GridBagLayout());
	GridBagConstraints c = new GridBagConstraints();	
	
	c.gridx=0; c.gridy=0; c.gridwidth=10; c.gridheight=1;
	sliders.add( videoPosSlider,c);
	c.gridx=11; c.gridwidth=1;
	sliders.add( videoPosLabel,c );
	c.gridx=12; c.gridwidth=1;
	sliders.add( Box.createRigidArea( new Dimension(5,1) ));
	c.gridx=13; c.gridwidth=1;
	sliders.add( batchButton );

	/*
	endPosSlider   = new JSlider(JSlider.HORIZONTAL, 0,100,5);
	c.gridx=0; c.gridy=1; c.gridwidth=1; c.gridheight=1;
	sliders.add( endPosLabel,c );
	c.gridx=1; c.gridwidth=6;
	sliders.add( endPosSlider,c); */


	
	// add all content to the panel
	ourContent.add(ourState);
	ourContent.add(Box.createRigidArea(new Dimension(1,5)));
	ourContent.add(row1);
	ourContent.add(Box.createRigidArea(new Dimension(1,5)));
	ourContent.add(row2);
	ourContent.add(Box.createRigidArea(new Dimension(1,5)));
	ourContent.add(sliders);
	ourContent.add(Box.createRigidArea(new Dimension(1,5)));

	// ------ CALLBACKS and LOGIC ------

	// update which images are open
	/* // this causes deadlocks, using a manual click on the list now 
	 * 
	imgSelectCB = new ImageSelector.Callback() {
	    public void call() {
		//Tool.trace("Image opened / closed");
		imgBox.newElements( imgSelect.getOpenImages());
	    }
	};
	imgSelect.addCallback( imgSelectCB ); */

	// instead, refresh when opened
	imgBox.box.addPopupMenuListener( new PopupMenuListener() {
	    public void popupMenuCanceled( PopupMenuEvent e ) {};
	    public void popupMenuWillBecomeInvisible( PopupMenuEvent e ) {};
	    public void popupMenuWillBecomeVisible( PopupMenuEvent e ) {
		//System.out.println("note");	
		imgBox.newElements( imgSelect.getOpenImages());
	    };
	});


	// import images / time-lapse
	importImageButton.addActionListener( new ActionListener() {
	    public void actionPerformed( ActionEvent e ) {
		
		if ( isVideoStack ) {
		    // import as time-laspe stack
		    importImageDialog( imgBox.getSelectedItem(), 
			(int)zSliceVideoSpinner.getVal(), videoStackPositionTime );
		    isVideoStack = true;
		} else {
		    // import as stadard stack
		    importImageDialog( 
			imgBox.getSelectedItem(), 
			imgBox.getSelectedItem().depth / simParam.getImgPerZ(), 0 );
		
		    isVideoStack = false;
		}
	    }
	});
	
	// change import mode
	importTimelapseBox.addActionListener( new ActionListener() {
	    public void actionPerformed( ActionEvent e ) {
		changeTimelapseMode( importTimelapseBox.isSelected());
	    }
	});

	// video position slider change position
	videoPosSlider.addChangeListener( new ChangeListener() {
	    public void stateChanged(ChangeEvent e) {
		videoStackPositionTime = videoPosSlider.getValue()-1;
		videoPosLabel.setText( 
		    String.format(" t:  % 5d", videoStackPositionTime+1));
		simParam.signalRuntimeChange();
	    }
	});

	// selected image changed
	imgBox.addSelectListener( 
	    new Tiles.SelectListener<ImageSelector.ImageInfo>() {
	    public void selected( ImageSelector.ImageInfo inf, int i ) {
		updateIfVideoCanBeUsed();	
	    }
	});

	// selected number of z-planes changed
	zSliceVideoSpinner.addNumberListener( new Tiles.NumberListener() {
	    public void number(double d, Tiles.LNSpinner e) {
		updateIfVideoCanBeUsed();
	    }
	});

	// change the auto-update mode
	videoAutoUpdateMode.addSelectListener( new Tiles.SelectListener<String>() {
	    public void selected( String e, int i) {
		
		if (!sucessfulImport) {
		    JOptionPane.showMessageDialog( baseframe,
		     "Please run a successful image import first", "fairSIM error",
		     JOptionPane.ERROR_MESSAGE);
		    videoAutoUpdateMode.setSelectedIndex(0);
		    return;
		}
		
		if (i>1 && simParam.otf() == null) {
		    JOptionPane.showMessageDialog( baseframe,
		     "Please run a successful single SIM reconstruction first", 
		     "fairSIM error",
		     JOptionPane.ERROR_MESSAGE);
		    videoAutoUpdateMode.setSelectedIndex(0);
		    return;
		}
		
		runAutoUpdate(i);
	    
	    }
	});

	// run the batch reconstruction dialog
	batchButton.addActionListener( new ActionListener() {
	    public void actionPerformed( ActionEvent e) {
		if (!sucessfulImport || simParam.otf() == null) {
		    JOptionPane.showMessageDialog( baseframe,
		     "Please run a successful single SIM reconstruction first", 
		     "fairSIM error",
		     JOptionPane.ERROR_MESSAGE);
		    return;
		}
		openBatchDialog();
	    }
	});


	// update the video function
	changeTimelapseMode(false);
    }

    /** check if the image is o.k. */
    boolean checkImage( ImageSelector.ImageInfo img, int zSlices ) {
	if ((img==null)||(img.depth==0)) {
	    JOptionPane.showMessageDialog( baseframe,
	     "No image selected", "fairSIM error",
	     JOptionPane.ERROR_MESSAGE);
	    return false;
	}

	int ipz = simParam.getImgPerZ(); 
	if ( img.depth % ipz != 0 || (img.depth/ipz) % zSlices != 0 ) {
	    String err=String.format(
		"<html>Number of images should be a multiple of<br>"+
		"ang x pha (%d) x z (%d), but is %d. </html>",
		simParam.getImgPerZ(), zSlices, img.depth );
	    JOptionPane.showMessageDialog( baseframe,
	     err, "fairSIM error",
	     JOptionPane.ERROR_MESSAGE);
	    return false;
	}
    
	return true;
    }


    /** Update to the user if the stack could be a video */
    void updateIfVideoCanBeUsed() {


	ImageSelector.ImageInfo inf = imgBox.getSelectedItem(); 
	if ( inf == null ) {
	    videoPosSlider.setEnabled(false);
	    importImageButton.setEnabled(false);
	    maxTimePointsLabel.setText("no image");	    
	    return;
	}
	
	int zplSel = (int)zSliceVideoSpinner.getVal();  

	// check if with the currently selected number of images,
	// the image could be imported as video
	
	if ( inf.depth % ( zplSel * simParam.getImgPerZ() ) != 0 ) {
	    videoPosSlider.setEnabled(false);
	    importImageButton.setEnabled(false);
	    videoAutoUpdateMode.setEnabled(false);
	    
	    videoPosLabel.setText("t: n/a");
	    maxTimePointsLabel.setText("t-max: n/a");
	    maxTimePointsLabel.setToolTipText("Total number of images divided by "+
		"number of z-slices is no integer, "+
		"please select matching number of z-slices");
	} else {
	    importImageButton.setEnabled(true);
	    videoPosSlider.setEnabled(true);
	    videoAutoUpdateMode.setEnabled(true);

	    totalTimePoints = inf.depth / zplSel / simParam.getImgPerZ(); 
	    if ( videoPosSlider.getValue() >= totalTimePoints ) {
		videoPosSlider.setValue(0);
	    } else {
		videoPosLabel.setText( 
		    String.format(" t:  % 5d", videoStackPositionTime+1));
	    }
	    videoPosSlider.setMaximum( totalTimePoints );
	    maxTimePointsLabel.setText("t-max: "+ totalTimePoints);
	    maxTimePointsLabel.setToolTipText("Number of available time points "+
		"at selected z");

	}

    }

    /** switch timelapse mode on and off */
    void changeTimelapseMode( boolean onoff ) {

	isVideoStack = onoff;
	
	sucessfulImport = false;
	runAutoUpdate(0);
	
	videoPosSlider.setEnabled(isVideoStack);
	zSliceVideoSpinner.setEnabled(isVideoStack);
    
	if (isVideoStack) {
	    updateIfVideoCanBeUsed();
	} else {
	    importImageButton.setEnabled(true);
	    videoAutoUpdateMode.setEnabled(true);
	}

    }


    /** start auto-update */
    void runAutoUpdate( int mode ) {

	if ( autoUpdater != null ) {
	   autoUpdater.updateMode = 0;
	   //autoUpdater.interrupt();
	   try {
		autoUpdater.join();
	   } catch (InterruptedException ex) {
		Tool.trace("Thread join interruption, should not happen");
	   }
	   autoUpdater = null;
	   videoAutoUpdateMode.setBackground( null );	
	   if (simPreviewDisplay != null)
		simPreviewDisplay.drop();
	}
	
	// mode 0 stops the thread and 
	if (mode==0) return;

	// all other modes are written to the thread
	// which is started if not existing
	autoUpdater = new AutoUpdateThread(mode);
	Tool.trace("Starting auto update, mode "+updateModes[mode]);
	autoUpdater.start();
    }


    /** this runs a background thread updating the images on the fly */
    class AutoUpdateThread extends Thread {
	
	int updateMode;
	AutoUpdateThread(int initialMode) {
	    updateMode = initialMode;
	}

	@Override
	public void run() {

	    long lastUpdate = 0;
		    
	    int simWidth  = theFFTImages[0][0].vectorWidth()*2;
	    int simHeight = theFFTImages[0][0].vectorHeight()*2;

	    simPreviewDisplay = 
		idpFactory.create(simWidth, simHeight, "SIM preview");
	    
	    simPreviewDisplay.addImage( Vec2d.createReal(simWidth, simHeight), 
		"SIM preview Image");

	    while(true) {

		// stop the thread if the import was not successful
		if (!sucessfulImport) {
		    videoAutoUpdateMode.setSelectedIndex(0);
		    Tool.trace("AUTO-UPDATE: Stopping auto updater, "+
		    "as last import was unsuccessful");
		    return;
		}

		// stop the thread if the update mode is 0
	        if (updateMode == 0) {
		    Tool.trace("AUTO-UPDATE: Stopping auto updater, as requested");
		    return;
		}


		// see if any updates have to be performed
		boolean doUpdate = simParam.compareRuntimeTimestamp( lastUpdate );

		// wait a few hundred ms if there is nothing to update
		if (!doUpdate) {
		    //Tool.trace("AUTO-UPDATE: no change, nothing to update. ");
		    try {
			sleep(400);
		    } catch (InterruptedException ex) {}
		    continue;
		}

		// run the automatic update for the preview..
		videoAutoUpdateMode.setBackground( Color.YELLOW );	
		lastUpdate = System.currentTimeMillis();
	
		// update the input images
		Tool.trace("AUTO-UPDATE: updating wide-field view ");

		importImages(imgBox.getSelectedItem(), videoStackPositionZ, 
		    videoStackPositionTime, true ); 
	    
		// update the parameter estimation
		if (updateMode>2) {

		    SimAlgorithm.estimateParameters( 
			simParam, theFFTImages, 
			fsGUI.parc.getFitBand(), 
			fsGUI.parc.getFitExclude(), 
			null, 0, null);

		}

		// update the SIM reconstruction
		if (updateMode>1) {

		    if (simParam.otf() != null ) {
		
			Tool.trace("AUTO-UPDATE: updating wide-field view ");

			Vec2d.Real simRecon = SimAlgorithm.runReconstruction( 
			    simParam, theFFTImages, 
			    null,  0, false, SimParam.CLIPSCALE.BOTH, null);
		   
			if (simPreviewDisplay != null) {
			    simPreviewDisplay.setImage( simRecon, 0, "SIM image");
			    simPreviewDisplay.display();
			}
		    
		    } else {
			Tool.trace("AUTO-UPDATE: No OTF / parameters set for SIM");
		    }
		}
		
		videoAutoUpdateMode.setBackground( Color.GREEN );	
	    
	    }
	}
    }





    /** set the currently selected image */
    void importImageDialog( final ImageSelector.ImageInfo img, 
	final int zSlices, final int tPosition ) {

	Tool.trace("zSlice: "+zSlices);

	// TODO: Image size checks!
	if (!checkImage( img, zSlices ))
	    return;
	importImageButton.setEnabled(false);

	totalTimePoints = img.depth / zSlices / simParam.getImgPerZ();

	pxlSet  = ( img.micronsPerPxl > 0);
	pxlSize = ( pxlSet )?(img.micronsPerPxl*1000):(80);
	
	if (( pxlSize < 20 ) ||( pxlSize > 500 )) {
	    JOptionPane.showMessageDialog(baseframe,
		"<html>Pixel size in file not within 20nm..500nm<br >"+
		"Size in file is: "+String.format("%5.2f</html>", pxlSize),
		"Ignoring pixel size",
		JOptionPane.WARNING_MESSAGE		
		);
	    pxlSet  = false;
	    pxlSize = 80;
	}
	
	// slice selector - only shown if the image has enough slices
	JPanel p1 = new JPanel();
	p1.setLayout(new BoxLayout(p1, BoxLayout.LINE_AXIS));
	p1.setBorder(BorderFactory.createTitledBorder("Slice") );

	sliceBox = new Tiles.LComboBox<Integer>("#",1);
	sliceBox.box.setPrototypeDisplayValue("123");
	
	if (img.depth/totalTimePoints > simParam.getImgPerZ() ) {
	    // only enable the box if there is more than one slice
	    Integer [] n = new Integer[ 
		img.depth/simParam.getImgPerZ()/totalTimePoints ];
	    for (int i=0; i<n.length; i++) {
		n[i] = i+1;
	    }
	    sliceBox.newElements( n.length/2, n );
	    sliceBox.box.setEnabled(true );
	} else {
	    sliceBox.box.setEnabled(false );
	}
 
	// button to display slice projection
	showSlicesButton = new JButton("show slices");
	showSlicesButton.setToolTipText("Display wide-field projection of slices");
	showSlicesButton.addActionListener( new ActionListener() {
	    public void actionPerformed(ActionEvent e) {
		showSlices(img, zSlices);
	    }
	});

	p1.add( Box.createHorizontalGlue());
	p1.add( sliceBox );
	p1.add( Box.createRigidArea( new Dimension(5,0)));
	p1.add( showSlicesButton );
	p1.add( Box.createHorizontalGlue());

	// pixel size selector
	JPanel p2 = new JPanel();
	p2.setLayout(new BoxLayout(p2, BoxLayout.PAGE_AXIS));
	p2.setBorder(BorderFactory.createTitledBorder("Pixel size") );

	final JLabel pxlSizeLabel = new JLabel("from image file");
	pxlSizeLabel.setForeground(Color.BLUE);
	pxlSizeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

	Tiles.LNSpinner pxlSizeSpinner = 
	    new Tiles.LNSpinner( "nm" , pxlSize, 10, 500, 0.1 ); 

	if ( !pxlSet ) {
	    pxlSizeLabel.setText("not set");
	    pxlSizeLabel.setForeground(Color.RED);
	} 
	
	pxlSizeSpinner.addNumberListener( new Tiles.NumberListener() {
	    public void number( double pxl, Tiles.LNSpinner ign ) {
		pxlSize = pxl;
		pxlSet = true;
		pxlSizeLabel.setText("set manually to");
		pxlSizeLabel.setForeground(Color.GREEN.darker());
	    }
	});
	
	p2.add( pxlSizeLabel );
	p2.add( Box.createRigidArea( new Dimension( 5,5)));
	p2.add( pxlSizeSpinner );

	// background subtraction
	bgrSpinner = new Tiles.LNSpinner( "val" , 50, 0, 5000, 5 ); 
	bgrBox = new Tiles.LComboBox<String>("Use?", "no", "yes");
	bgrBox.box.setToolTipText("Subtract a constant background value?");

	JPanel bgrPanel = new JPanel();
	bgrPanel.setLayout( new BoxLayout( bgrPanel, BoxLayout.LINE_AXIS));
	bgrPanel.setBorder(BorderFactory.createTitledBorder("Background substraction") );

	bgrPanel.add( bgrBox );
	bgrPanel.add( bgrSpinner );

	// dialog	
	final JDialog imageDialog = new JDialog(baseframe,
	    "Image import settings", false);

	// bugfix: re-enable the select button if the dialog is closed
	imageDialog.addWindowListener( new WindowAdapter() {
	    @Override
	    public void windowClosed(WindowEvent e) {
			importImageButton.setEnabled(true);
	    }
	});


	JButton ok = new JButton("Import");
	JButton cl = new JButton("Cancel");
	JPanel p0 = new JPanel();
	p0.add( ok );
	p0.add( cl );

	// perform the image import
	ok.addActionListener( new ActionListener() {
	    public void actionPerformed(ActionEvent e) {
		if (!pxlSet) {	
		    JOptionPane.showMessageDialog(baseframe,
			"No pixel size set",
			"Pixel size",
			JOptionPane.ERROR_MESSAGE
		    );
		    return;
		}
		if (running) {	
		    JOptionPane.showMessageDialog(baseframe,
			"Import still running",
			"Import active",
			JOptionPane.ERROR_MESSAGE
		    );
		    return;
		}
		imageDialog.dispose();
		(new SwingWorker<Object, Object> () {
		    @Override
		    public Object doInBackground() {
			running = true;	
			videoStackPositionZ = sliceBox.getSelectedItem()-1; 
			importImages(img, videoStackPositionZ, tPosition,true);
			return null;
		    }
		    @Override
		    protected void done() {
			running = false;
			importImageButton.setEnabled(true);
			sucessfulImport = true;
			simParam.signalRuntimeChange();
		    }
		}).execute();
		
	    }
	});

	cl.addActionListener( new ActionListener() {
	    public void actionPerformed(ActionEvent e) {
		importImageButton.setEnabled(true);
		imageDialog.dispose();
	    }
	});

	// pack and display dialog
	JPanel dialog = new JPanel();
	dialog.setLayout( new BoxLayout( dialog, BoxLayout.PAGE_AXIS));
	dialog.add( p1 );
	dialog.add( p2 );
	dialog.add( bgrPanel );
	dialog.add( p0 );
	
	imageDialog.add( dialog );
	imageDialog.pack();
	imageDialog.setVisible(true);

    }

    



    /** import a set of images to be reconstructed */ 
    void importImages( final ImageSelector.ImageInfo img, 
	int zPos, int tPos, boolean showOutput ) {
    
	// images per z-plane, z-planes
	int ipz  = simParam.getImgPerZ(); 
	int zpl  = img.depth / ipz / totalTimePoints ;

	theImages    = new Vec2d.Real[ simParam.nrDir() ][];
	theFFTImages = new Vec2d.Cplx[ simParam.nrDir() ][];

	ourState.setText("Importing images...");
	ourState.setForeground(Color.BLUE);
	ourState.repaint(20);

	// square images
	final boolean doResize =
	    ((img.width != img.height) | ( img.width % 32 != 0));
	int imgSize = next32( Math.max(img.width, img.height) );
	
	final ImageDisplay curSlice = (showOutput)?(idpFactory.create( 
	    imgSize, imgSize, "Slice to reconstruct")):(null);
    
	// setup dislay for raw data
	if (showOutput) {
	    // reset and close for size mismatched
	    if ( rawDataDisplay != null && 
		(	rawDataDisplay.width() != imgSize 
		    ||	rawDataDisplay.height() != imgSize
		    ||	rawDataDisplay.getCount() != ipz+1 )) {
		rawDataDisplay.drop();
		rawDataDisplay = null;
	    }
	    // create with right size
	    if (rawDataDisplay == null) {
		rawDataDisplay = idpFactory.create( imgSize, imgSize, 
		"SIM: raw input data");
		for (int i=0; i<ipz+1; i++)
		    rawDataDisplay.addImage( Vec2d.createReal(imgSize, imgSize), 
			"Placeholder");

	    }
	} else {
	    if (rawDataDisplay != null)
		rawDataDisplay.drop();
	    rawDataDisplay=null;
	}

	Vec2d.Real widefield = Vec2d.createReal( imgSize, imgSize );
	int imageCount=0;

	for (int d=0; d<simParam.nrDir(); d++) {
	    
	    theImages[d]    = new Vec2d.Real[ simParam.dir(d).nrPha() ];
	    theFFTImages[d] = new Vec2d.Cplx[ simParam.dir(d).nrPha() ];

	    for (int p=0; p<simParam.dir(d).nrPha(); p++) {
	    
		int pos = simParam.getImgSeq().calcPosWithTime( d,p,zPos, tPos,
		    simParam.nrDir(), simParam.dir(d).nrPha(), zpl );

		imageCount++;

		// import and store images  // TODO: move the FFT to somewhere else
		Vec2d.Real curImg   = imgSelect.getImage(img,pos).duplicate();

		// if we have to do background subtraction
		if (bgrBox.box.getSelectedIndex()==1) {
		    double r = SimUtils.subtractBackground( curImg,
			bgrSpinner.getVal());
		}
		
		SimUtils.fadeBorderCos( curImg , 10);

		// if we have to rescale the images...
		if (doResize) {
		    Tool.trace("Resizing input to square "+imgSize+"x"+imgSize);
		    Tool.tell("Input resize to "+imgSize+"x"+imgSize);
		    theImages[d][p] = Vec2d.createReal( imgSize, imgSize);
		    theImages[d][p].paste(curImg,0,0, false);
		} else {
		    theImages[d][p] = curImg;
		}
		
		
		theFFTImages[d][p] = Vec2d.createCplx( theImages[d][p] );
		theFFTImages[d][p].copy( theImages[d][p] );
		Transforms.fft2d( theFFTImages[d][p], false );
		
		if (rawDataDisplay != null) {
		    
		    rawDataDisplay.setImage( theImages[d][p], imageCount, 
		    String.format( 
		    "Image p: %2d/%2d a: %2d/%2d z: %2d/%2d t: %2d",
		    p+1, simParam.dir(d).nrPha(),
		    d+1, simParam.nrDir(), zPos+1, zpl, tPos+1 ));
		    
		    widefield.add( theImages[d][p]);
		}
	    }
	}
    
	if (rawDataDisplay!=null) {
	    widefield.scal( 1.f/simParam.getImgPerZ() );
	    rawDataDisplay.setImage( widefield, 0, "Image: proj. widefield");
	    rawDataDisplay.display();
	}

	ourState.setText(String.format("%s (slice z %d, t %d plx %3.0fnm)", img.name, zPos+1, tPos+1, pxlSize)); 
	ourState.setForeground(Color.GREEN.darker());

	simParam.setPxlSize( theImages[0][0].vectorWidth(), pxlSize/1000. );
    }


    /** shows the slice selector */
    void showSlices( final ImageSelector.ImageInfo img, final int zpl ) {
	if (!checkImage( img, zpl))
	    return;

	showSlicesButton.setEnabled(false);
	showSlicesButton.setText("Working...");

	// close the old selector
	if (sliceSelector != null) 
	    sliceSelector.drop();


	// images per z-plane, z-planes
	int ipz = simParam.getImgPerZ(); 
	
	Tool.tell("computing widefield projection");

	// sum up images for z-plane
	Vec2d.Real imgZ   = Vec2d.createReal( img.width, img.height );

	sliceSelector = idpFactory.create( 
	    imgZ.vectorWidth(), imgZ.vectorHeight(), "Slice selector"); 

	for (int z=0; z< zpl; z++) {
	    imgZ.zero();
	    for (int d=0; d<simParam.nrDir(); d++) {
		for (int p=0; p<simParam.dir(d).nrPha(); p++) {
	    
		    int pos = simParam.getImgSeq().calcPos( d,p,z,
			simParam.nrDir(), simParam.dir(d).nrPha(), zpl );
		  
		    Vec2d.Real imgSlice = imgSelect.getImage( img, pos );
		    
		    if (imgSlice == null) {
			Tool.tell("Image has been closed!");
			showSlicesButton.setText("No image!");
			return;
		    }

		    imgZ.add( imgSlice );
		
		}
	    }
	    sliceSelector.addImage( imgZ, "Z="+z ); 
	}

	// set the callback 
	sliceSelector.addListener( new ImageDisplay.Notify() {
	    final ImageSelector.ImageInfo ourImg = img;
	    public void newPosition( ImageDisplay e, int p ) {
		if (sliceBox != null )
		    sliceBox.box.setSelectedIndex( p );
	    }
	});

	sliceSelector.display();
	showSlicesButton.setEnabled(true);
	showSlicesButton.setText("show slices");

    }


    /** set the currently selected image */
    void openBatchDialog( ) {

	JPanel p1 = new JPanel();
	p1.setLayout(new BoxLayout(p1, BoxLayout.LINE_AXIS));
	p1.setBorder(BorderFactory.createTitledBorder("Range") );

	final Tiles.LNSpinner startSpinner
	    = new Tiles.LNSpinner("start", 1, 1, totalTimePoints, 1); 
	final Tiles.LNSpinner stopSpinner
	    = new Tiles.LNSpinner("stop", totalTimePoints, 1, totalTimePoints, 1); 

	p1.add( startSpinner );
	p1.add( stopSpinner );

	// dialog	
	final JDialog imageDialog = new JDialog(baseframe,
	    "Batch reconstruction", false);

	imageDialog.addWindowListener( new WindowAdapter() {
	    @Override
	    public void windowClosed(WindowEvent e) {
			// TODO: put stop thread here
	    }
	});

	final JButton ok = new JButton("Start reconstruction");
	final JButton cl = new JButton("Cancel");
	JPanel p0 = new JPanel();
	p0.add( ok );
	p0.add( cl );
    
	JProgressBar progressBar = new JProgressBar(0,1000);
	JPanel p2 = new JPanel();
	p2.setLayout(new BoxLayout(p2, BoxLayout.LINE_AXIS));
	p2.setBorder(BorderFactory.createTitledBorder("Progress") );
	p2.add(progressBar);

	final BatchReconstructionThread brt = new BatchReconstructionThread(
	    progressBar, ok, cl, videoAutoUpdateMode.getSelectedIndex() );

	// perform the image import
	ok.addActionListener( new ActionListener() {
	    public void actionPerformed(ActionEvent e) {
		brt.setStartStop(
		    (int)startSpinner.getVal()-1,
		    (int)stopSpinner.getVal());
		ok.setEnabled(false);
		ok.setText("running");
		brt.start();
	    }
	});

	cl.addActionListener( new ActionListener() {
	    public void actionPerformed(ActionEvent e) {
		brt.cancel = true;
		try {
		    brt.join();
		} catch (InterruptedException ex) {
		    Tool.trace("Thread join interrupted");
		}
		imageDialog.dispose();
	    }
	});

	// pack and display dialog
	JPanel dialog = new JPanel();
	dialog.setLayout( new BoxLayout( dialog, BoxLayout.PAGE_AXIS));
	dialog.add( p1 );
	dialog.add( p2 );
	dialog.add( p0 );
	
	imageDialog.add( dialog );
	imageDialog.pack();
	imageDialog.setVisible(true);

    }

   
    class BatchReconstructionThread extends Thread {

	int start, stop;
	boolean cancel=false;
	JProgressBar jpBar;
	JButton okButton, clButton;

	final int updateMode ;

	BatchReconstructionThread( JProgressBar jp, JButton ok, JButton cl, int updateMode ) {
	    jpBar = jp;
	    okButton = ok;
	    clButton = cl;
	    this.updateMode = updateMode;
	}

	void setStartStop( int start, int stop) {
	    this.start = start;
	    this.stop  = stop;
	}

	@Override
	public void run() {

	    int simWidth  = theFFTImages[0][0].vectorWidth()*2;
	    int simHeight = theFFTImages[0][0].vectorHeight()*2;

	    ImageDisplay simOutputDisplay = 
		idpFactory.create(simWidth, simHeight, "SIM batch results");
	    
	    for (int timePos=start; timePos<stop; timePos++) {

		importImages(imgBox.getSelectedItem(), videoStackPositionZ, 
		    timePos, true ); 
	
		if (updateMode>2) {
		    SimAlgorithm.estimateParameters( 
			simParam, theFFTImages, 
			fsGUI.parc.getFitBand(), 
			fsGUI.parc.getFitExclude(), 
			null, 0, null);
		}


		Vec2d.Real simRecon = SimAlgorithm.runReconstruction( 
		    simParam, theFFTImages, null,  0, false, 
		    simParam.getClipScale(), null);
		
		simOutputDisplay.addImage( simRecon,"timeslice t:"+timePos);

		jpBar.setValue( ((timePos-start+1)*1000) / (stop-start));

		if (timePos>start) simOutputDisplay.display();
		if (cancel) break;
	    }

	    simOutputDisplay.display();
	    okButton.setText("Done.");
	    clButton.setText("close");
	}

    }





    /** The lowest multiple of 32 larger or equal to 'in' */
    int next32( int in ) {
	for (int i=0 ; i<1048576; i+=32 )
	    if (i>=in)
		return i;
	return -1;
    }


    /** for testing */
    public static void main( String [] arg ) {
	JFrame main = new JFrame("Test");

	int nImg = ( arg.length > 0 )?(Integer.parseInt( arg[0] )):(5);
	ImageControl ic  = new ImageControl(
	    main,
	    new ImageSelector.Dummy(nImg) , 
	    org.fairsim.fiji.DisplayWrapper.getFactory(), null, 
	    SimParamGUI.dummySP() );
	
	
	main.add( ic.getPanel() );
	main.pack();
	main.setVisible(true);
	main.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE);
    }


}

