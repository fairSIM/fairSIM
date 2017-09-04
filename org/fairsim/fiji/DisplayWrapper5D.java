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

package org.fairsim.fiji;

import org.fairsim.utils.ImageStackOutput;
import org.fairsim.utils.ImageSelector;
import org.fairsim.utils.Tool;

import org.fairsim.linalg.Vec2d;
import org.fairsim.linalg.Vec3d;

import ij.ImageStack;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.ImageListener;
import ij.IJ;
import ij.ImageJ;
import ij.measure.Calibration;
import ij.process.LUT;
import ij.CompositeImage;


import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;


public class DisplayWrapper5D implements ImageStackOutput, ImageListener {


    final int width,height,depth,channels,timepoints;

    // ImageJ components
    private ImageStack  is=null;
    private CompositeImage   ip=null;
    private int currentPosition=0;
    private String ourTitle="[no title]";

    // spatial calibration data
    private double muLat=-1, muAx=-1;
  
    // spectral calibration data
    private double [] wavelengths = null;


    // internal storage
    final private ImageVector [] imageVectors ;
    final private String [] labels;

    private int idx(int z, int c, int t ) {
	int pos = c + z * channels + t * channels*depth;
	return pos;
    }
    

    public DisplayWrapper5D( int w, int h, int d, int c, int t, String title ) {
	
	if ( w<1 || h <1 || d<1 || c<1 || t < 1 ) {
	    throw new IndexOutOfBoundsException("size should not be < 1");
	}

	width  = w;
	height = h;
	depth  = d;
	channels = c;
	timepoints = t;
	ourTitle = title;

	imageVectors = new ImageVector[d*c*t ];
	for ( int i=0 ; i< imageVectors.length; i++ ) {
	    imageVectors[i] = ImageVector.create(w ,h);
	}
    
	labels = new String[d*c*t];
    }

    @Override
    public void setPixelSize( double muLateral, double muAxial ) {
	muLat = muLateral;
	muAx  = muAxial;
    }

    @Override
    public void setWavelengths( double [] wl ) {
	if (wl == null) {
	    wavelengths=null;
	    return;
	}
	if (wl.length != channels ) {
	    throw new RuntimeException("wavelength array length != number of channels");
	}
	wavelengths = wl;

    }


    @Override
    public void setImage( Vec2d.Real img, int z, int c, int t, String title ) {
	imageVectors[ idx(z,c,t) ].copy(img);	
	labels[ idx(z,c,t) ] = title;
    }
    
    @Override
    public void setImage( Vec3d.Real img, int c, int t , String title ) {
	for (int z=0; z<depth; z++) {
	    imageVectors[idx( z,c,t) ].slice( img, z );
	    labels[ idx(z,c,t) ] = title+"[z:"+z+"]";
	}
    }


    @Override
    public void update() {

	if (ip==null) {
	    
	    // create stack
	    is   = new ImageStack(width,height);
	    for (int i=0; i<imageVectors.length ; i++) {
		is.addSlice( imageVectors[i].img() );
		is.setSliceLabel( labels[i],i+1);
	    }
	    
	    // create ImagePlus
	    ip = new CompositeImage( new ImagePlus(ourTitle, is ), CompositeImage.COMPOSITE);
	    ip.setProperty("org.fairsim.fiji.DisplayWrapper","yes");
	    ip.setDimensions( channels, depth, timepoints );
	    ip.setOpenAsHyperStack( true );
	    
	    // set the scale (if set)
	    if (muLat >0 && muAx >0) {

		Tool.trace(String.format("Setting pixel size, lateral %7.3f nm, axial %7.3f nm",
		    muLat*1000, muAx*1000));
		Calibration cal = new Calibration();
		cal.pixelWidth  = muLat;
		cal.pixelHeight = muLat;
		cal.pixelDepth  = muAx;
		cal.setUnit("micron");
		ip.setCalibration( cal );
	    }
	    
	    // set the LUT (if wavelength data is available)
	    if ( wavelengths != null ) {
		LUT [] luts = new LUT[ channels ];
		for (int c=0; c<channels; c++) {
		    luts[c] = getLUT4IJbyWavelength( wavelengths[c] );
		}
		ip.setLuts( luts );

	    }

	    // set min/max // TODO: selectable zero-clipping?
	    for (int c=0 ; c<channels; c++) {
		ip.setC( c+1 );
		ip.setDisplayRange( 0, imageVectors[c].maxEntry());
	    }
	    ip.setC(1);


	    // display the ImagePlus, do this in the Swing Thread
	    if (SwingUtilities.isEventDispatchThread()) {
		ip.show();
	    } else {
	    try {
		SwingUtilities.invokeAndWait(new Runnable() {
		    public void run() {
			ip.show();
		    }
		}); 
	    } catch (InterruptedException ex) {
		Tool.trace("ERR: DisplayWrapper interrupted");
	    } catch (InvocationTargetException ex) {
		Tool.trace("ERR: DisplayWrapper called wrong target");
	    } }
	    
	} else {
	    ip.show();
	}

    }

    // ------ Fijis ImageListener interface ------
    
    public void imageClosed(ImagePlus imp) {
	if (imp!=ip) return;

	// if our ImagePlus has been closed, deregister us and throw
	// away our data
	ImagePlus.removeImageListener(this);
	is=null; ip=null;
    }
    
    public void imageOpened(ImagePlus imp)  {
    
    }
    
    public void imageUpdated(ImagePlus imp)  {

    }


    // re-implementation of standard LUTs, as ImageJ keeps us from
    // accessing them directly
    static LUT getLUT4IJ(int color) {

	    byte [][] values = new byte[3][256];

	    // gray
	    if (color==0) {
		for (short i=0; i<256; i++) {
                    values[0][i] = (byte)i; // blue
                    values[1][i] = (byte)i; // green
                    values[2][i] = (byte)i; // red
		}
	    } else {
	    // 1-6: blue, green, cyan, red, magenta, yellow
            for (short i=0; i<256; i++) {
		if ((color&4)!=0)
                    values[2][i] = (byte)i; // red
            	if ((color&2)!=0)
                    values[1][i] = (byte)i; // green
            	if ((color&1)!=0)
                    values[0][i] = (byte)i; // blue
        	}
	    }
    
	return new LUT( values[2], values[1], values[0]);
    }

    /** Returns a LUT depending on (not representing!) wavelength.
     *    0 - 480: blue
     *  480 - 560: green
     *  560 - 640: red
     *  640 - inf: grey */
    static LUT getLUT4IJbyWavelength( double wl ) {
	if (wl < 480) return getLUT4IJ( 1 );
	if (wl < 560) return getLUT4IJ( 2 );
	if (wl < 640) return getLUT4IJ( 4 );
	return getLUT4IJ(7);
    }



    // testing stuff
    public static void main(String [] args ) {
	
	ImageJ ij = new ImageJ( ImageJ.EMBEDDED );

	    
	IJ.open(args[0]);

	ImageOpener io = new ImageOpener();

	ImageSelector.ImageInfo iInfo = io.getOpenImages()[0];


	DisplayWrapper5D displ  = new DisplayWrapper5D(
	    iInfo.width, iInfo.height, iInfo.nrSlices, iInfo.nrChannels, iInfo.nrTimepoints, "test vec2d");
	DisplayWrapper5D displ2 = new DisplayWrapper5D(
	    iInfo.width, iInfo.height, iInfo.nrSlices, iInfo.nrChannels, iInfo.nrTimepoints, "test vec3d");


	// copy the images
	for (int z=0 ; z < iInfo.nrSlices; z++) {
	    for (int ch=0 ; ch < iInfo.nrChannels; ch++) {
	        for (int t =0; t< iInfo.nrTimepoints; t++) {
		    displ.setImage( io.getImage( iInfo , z, ch, t ), 
				    z,ch,t,
				    String.format("z:%d, c:%d, t%d",z,ch,t));
		}
	    }
	}

	// test 3D vectors
	for (int ch=0 ; ch < iInfo.nrChannels; ch++) {
	    for (int t =0; t< iInfo.nrTimepoints; t++) {

		Vec3d.Real imgs = Vec3d.createReal( iInfo.width, iInfo.height, iInfo.nrSlices );
	
		for (int z=0 ; z < iInfo.nrSlices; z++) {
		    imgs.setSlice( z, io.getImage( iInfo , z, ch, t ) );
		}

		displ2.setImage( imgs , ch, t , String.format("c:%d, t%d", ch, t));
	    }
	}
	    

	displ.update();
	displ2.update();

    }



}
