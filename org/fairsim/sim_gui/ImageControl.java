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
import javax.swing.SwingWorker;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;

import java.awt.Dimension;
import java.awt.ComponentOrientation;
import java.awt.Component;
import java.awt.Color;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

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
    private ImageDisplay.Factory idpFactory ;

    // global references to be accessed both by importImage and showSlices
    private JButton importImageButton	= null;
    private JButton showSlicesButton	= null;
    private ImageDisplay sliceSelector	= null;
    private Tiles.LComboBox<Integer> sliceBox;
    
    private Tiles.LComboBox<String> bgrBox;
    private Tiles.LNSpinner bgrSpinner;


    // the images
    Vec2d.Real [][] theImages    =null;
    Vec2d.Cplx [][] theFFTImages =null;
    double pxlSize;
    boolean pxlSet;

    public JPanel getPanel() {
	return ourContent;
    }

    /** Contructor, initializes image list. */
    public ImageControl( JFrame baseframe, ImageSelector is, 
	final ImageDisplay.Factory imgFactory,
	SimParam sp ) {

	// initialize variables
	this.baseframe = baseframe;
	this.imgSelect = is;
	this.simParam  = sp;
	this.idpFactory = imgFactory;

	// create our pnael
	ourContent.setLayout(new BoxLayout(ourContent, BoxLayout.PAGE_AXIS));
	ourContent.setBorder(BorderFactory.createTitledBorder("1 - Image Selector") );

	// setup label
	ourState.setAlignmentX(Component.CENTER_ALIGNMENT);
	ourState.setForeground(Color.RED);

	// setup selector box
	ImageSelector.ImageInfo [] openImages = imgSelect.getOpenImages();
	final Tiles.LComboBox<ImageSelector.ImageInfo> imgBox
	    = new Tiles.LComboBox<ImageSelector.ImageInfo>("Img", null, true, openImages);
	importImageButton  = new JButton("SELECT");

	imgBox.box.setPrototypeDisplayValue("some really long image filename"+
	    "as these will often be used");

	JPanel row1 = new JPanel();
	row1.setLayout(new BoxLayout(row1, BoxLayout.LINE_AXIS));
	row1.add(Box.createHorizontalGlue());
	row1.add( imgBox );
	row1.add(Box.createRigidArea(new Dimension(5,1)));
	row1.add( importImageButton );
	row1.add(Box.createHorizontalGlue());
	
	// add all content to the panel
	ourContent.add(ourState);
	ourContent.add(Box.createRigidArea(new Dimension(1,5)));
	ourContent.add(row1);
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


	// import an image
	importImageButton.addActionListener( new ActionListener() {
	    public void actionPerformed( ActionEvent e ) {
		importImageDialog( imgBox.getSelectedItem() );
	    }
	});

    }

    /** check if the image is o.k. */
    boolean checkImage( ImageSelector.ImageInfo img ) {
	if ((img==null)||(img.depth==0)) {
	    JOptionPane.showMessageDialog( baseframe,
	     "No image selected", "fairSIM error",
	     JOptionPane.ERROR_MESSAGE);
	    return false;
	}

	int ipz = simParam.getImgPerZ(); 
	if ( img.depth % ipz != 0 ) {
	    String err=String.format(
		"<html>Number of images should be a multiple of<br>"+
		"%d ( ang x pha ), but is %d. </html>",
		simParam.getImgPerZ(), img.depth );
	    JOptionPane.showMessageDialog( baseframe,
	     err, "fairSIM error",
	     JOptionPane.ERROR_MESSAGE);
	    return false;
	}
    
	return true;
    }


    /** set the currently selected image */
    void importImageDialog( final ImageSelector.ImageInfo img ) {

	// TODO: Image size checks!
	if (!checkImage( img ))
	    return;
	importImageButton.setEnabled(false);

	pxlSet  = ( img.micronsPerPxl > 0);
	pxlSize = ( pxlSet )?(img.micronsPerPxl*1000):(80);
	
	if (( pxlSize < 20 ) ||( pxlSize > 500 )) {
	    JOptionPane.showMessageDialog(baseframe,
		"<html>Pixel size in file not within 20nm..200nm<br >"+
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
	
	if (img.depth > simParam.getImgPerZ() ) {
	    // only enable the box if there is more than one slice
	    Integer [] n = new Integer[ img.depth/simParam.getImgPerZ() ];
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
		showSlices(img);
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
	bgrSpinner = new Tiles.LNSpinner( "val" , 50, 0, 1000, 5 ); 
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
	
	// set a new OTF from estimate HERE
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
			importImages(img);
			return null;
		    }
		    @Override
		    protected void done() {
			running = false;
			importImageButton.setEnabled(true);
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



    /** import the images for reconstruction */
    void importImages( final ImageSelector.ImageInfo img ) {

	// images per z-plane, z-planes
	Vec2d.Real [] images = imgSelect.getImage( img );
	int ipz  = simParam.getImgPerZ(); 
	int zpl  = images.length / ipz ;
	int curZ = sliceBox.getSelectedItem()-1; 
	


	theImages    = new Vec2d.Real[ simParam.nrDir() ][];
	theFFTImages = new Vec2d.Cplx[ simParam.nrDir() ][];

	ourState.setText("Importing images...");
	ourState.setForeground(Color.BLUE);
	ourState.repaint(20);

	// square images
	final boolean doResize = ((img.width != img.height) | ( img.width % 32 != 0));
	int imgSize = next32( Math.max(img.width, img.height) );
	
	final ImageDisplay curSlice = idpFactory.create( 
	    imgSize, imgSize, "Slice to reconstruct");
	
	for (int d=0; d<simParam.nrDir(); d++) {
	    
	    theImages[d]    = new Vec2d.Real[ simParam.dir(d).nrPha() ];
	    theFFTImages[d] = new Vec2d.Cplx[ simParam.dir(d).nrPha() ];

	    for (int p=0; p<simParam.dir(d).nrPha(); p++) {
	    
		int pos = simParam.getImgSeq().calcPos( d,p,curZ,
		    simParam.nrDir(), simParam.dir(d).nrPha(), zpl );

		// import and store images  // TODO: move the FFT to somewhere else
		Vec2d.Real curImg   = images[pos].duplicate();
		SimUtils.fadeBorderCos( curImg , 10);

		// if we have to do background subtraction
		if (bgrBox.box.getSelectedIndex()==1) {
		    double r = SimUtils.subtractBackground( curImg,
			bgrSpinner.getVal());
		}


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
		curSlice.addImage( theImages[d][p], String.format( 
		"Image p: %2d/%2d a: %2d/%2d z: %2d/%2d ", p+1, simParam.dir(d).nrPha(),
		    d+1, simParam.nrDir(), curZ+1, zpl ));
	    }
	}
    
	curSlice.display();
	ourState.setText(String.format("%s (slice %d, plx %3.0fnm)", img.name, curZ+1, pxlSize)); 
	ourState.setForeground(Color.GREEN.darker());
	simParam.setPxlSize( theImages[0][0].vectorWidth(), pxlSize/1000. );

    }


    /** shows the slice selector */
    void showSlices( final ImageSelector.ImageInfo img ) {
	if (!checkImage( img ))
	    return;

	showSlicesButton.setEnabled(false);
	showSlicesButton.setText("Working...");

	// close the old selector
	if (sliceSelector != null) 
	    sliceSelector.drop();


	// images per z-plane, z-planes
	Vec2d.Real [] images = imgSelect.getImage( img );
	if (images == null) {
	    Tool.tell("Image has been closed!");
	    showSlicesButton.setText("No image!");
	    return;
	}
	
	//Tool.trace("Name: "+img.name+": "+img.id+" "+images.length);
	Tool.tell("computing widefield projection");

	int ipz = simParam.getImgPerZ(); 
	int zpl = images.length / ipz ;

	//Tool.trace("ipz, zpl "+ipz+" "+zpl);

	// sum up images for z-plane
	Vec2d.Real imgZ   = Vec2d.createReal( images[0] );

	sliceSelector = idpFactory.create( 
	    imgZ.vectorWidth(), imgZ.vectorHeight(), "Slice selector"); 


	for (int z=0; z< zpl; z++) {
	    imgZ.zero();
	    for (int d=0; d<simParam.nrDir(); d++) {
		for (int p=0; p<simParam.dir(d).nrPha(); p++) {
	    
		    int pos = simParam.getImgSeq().calcPos( d,p,z,
			simParam.nrDir(), simParam.dir(d).nrPha(), zpl );
		  
		    imgZ.add( images[ pos ] );
		
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
	


	int nImg = ( arg.length > 0 )?(Integer.parseInt( arg[0] )):(4);
	ImageControl ic  = new ImageControl(
	    main,
	    new ImageSelector.Dummy(nImg) , 
	    org.fairsim.fiji.DisplayWrapper.getFactory(),
	    SimParamGUI.dummySP() );
	
	
	main.add( ic.getPanel() );
	main.pack();
	main.setVisible(true);
	main.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE);
    }


}

