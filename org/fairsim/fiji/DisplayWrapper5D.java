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
import ij.ImageListener;
import ij.IJ;
import ij.ImageJ;


import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;


public class DisplayWrapper5D implements ImageStackOutput, ImageListener {


    final int width,height,depth,channels,timepoints;

    // ImageJ components
    private ImageStack  is=null;
    private ImagePlus   ip=null;
    private int currentPosition=0;
    private String ourTitle="[no title]";

   

    // internal storage
    final private ImageVector [] imageVectors ;
    final private String [] labels;

    private int idx(int z, int c, int t ) {
	int pos = c + z * channels + t * channels*depth;
	return pos;
    }
    

    DisplayWrapper5D( int w, int h, int d, int c, int t, String title ) {
	
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
	    ip = new ImagePlus(ourTitle, is );
	    ip.setProperty("org.fairsim.fiji.DisplayWrapper","yes");
	    ip.setDimensions( channels, depth, timepoints );
	    ip.setOpenAsHyperStack( true );

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
