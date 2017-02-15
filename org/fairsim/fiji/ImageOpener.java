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

import org.fairsim.utils.ImageSelector;
//import org.fairsim.utils.Tool;

import java.util.List;
import java.util.ArrayList;

import ij.WindowManager;    // provides methods to query all open images
import ij.ImagePlus;	    
import ij.ImageStack;
import ij.ImageListener;
import ij.measure.Calibration;
import ij.IJ;


// The automated list update (ImageOpen listeners ...) seems to
// cause dead-locks... removing it for now, reverting to "click on the
// list to update..."

/** Provides connectivity to Fiji */ 
public class ImageOpener 
    implements ImageSelector {

    private boolean isActive=true;


    @Override
    public int getOpenImageCount() {
	return ij.WindowManager.getImageCount(); 
    }

    @Override
    public ImageInfo [] getOpenImages() {
	if (!isActive)
	    throw new RuntimeException("Connector was dropped.");
	    
	ImageInfo [] ret = new ImageInfo[ getOpenImageCount() ];
	int [] ids = WindowManager.getIDList();

	if ((ids==null) || (ids.length!=ret.length)) {
	    //Tool.trace("check FijiConnector for this message");
	    return null;
	}

	ArrayList<ImageSelector.ImageInfo> al =
	    new ArrayList<ImageSelector.ImageInfo>(ids.length);

	// loop through all images
        for (int i=0; i<ids.length; i++) {

	    ImagePlus curImg  = ij.WindowManager.getImage(ids[i]);
	    // dont use our own images
	    if (curImg.getProperty("org.fairsim.fiji.DisplayWrapper")!=null)
		continue;

	    // get pxl size
	    double microns = -1;
	    Calibration cb = curImg.getCalibration();
	    if (cb!=null && cb.getUnit()!=null) {
		String unit = cb.getUnit().trim();
		if (unit.startsWith("micro") ||
		    unit.equals("\u00B5m")||
		    unit.equals("um"))
			microns = cb.pixelWidth;
		if (unit.startsWith("nano") ||
		    unit.equals("nm"))
			microns = cb.pixelWidth/1000.;

	    }

	    // create an info object
	    al.add( new ImageSelector.ImageInfo(
		curImg.getWidth(),
		curImg.getHeight(),
		curImg.getStackSize(),	// stack size	
		microns,		// microns
		curImg.getTitle(),
		curImg.getID()
		));
	}   
    
	return al.toArray(new ImageSelector.ImageInfo[0] );
    }

    @Override
    public ImageVector getImage( ImageSelector.ImageInfo info, int pos ) {
	ImagePlus ip = WindowManager.getImage( info.id );
	if (ip==null) return null;
	return ImageVector.copy( ip.getStack().getProcessor( pos+1));
    }

    @Override
    public ImageVector [] getImages( ImageSelector.ImageInfo info ) {
	
	ImageVector [] ret = new ImageVector[ info.depth ];
	for ( int i=0; i<info.depth; i++)
	    ret[i] = getImage( info, i);
	
	return ret;

    }

    // ------ ImageListener interface ------
    
    /* 

    @Override
    public void imageOpened( ImagePlus ip ) {
	//Tool.trace("Image opened "+ip.getID());
	for ( ImageSelector.Callback c : iscb )
	    c.call();
    }

    @Override
    public void imageClosed( ImagePlus ip ) {
	//Tool.trace("Image closed "+ip.getID());
	for ( ImageSelector.Callback c : iscb )
	    c.call();
    }

    @Override
    public void imageUpdated( ImagePlus ip) {

    }
    */

    // ------ ImageSelector interface ------

    /*
    @Override
    public void addCallback( ImageSelector.Callback b ) {
	if (!isActive)
	    throw new RuntimeException("Connector was dropped.");
	iscb.add( b );
    }
    
    @Override
    public void removeCallback( ImageSelector.Callback b ) {
	iscb.remove( b );
    } */

}
