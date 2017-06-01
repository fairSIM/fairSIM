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


import org.fairsim.utils.ImageDisplay; 
import org.fairsim.utils.Tool; 

import org.fairsim.linalg.Vec;
import org.fairsim.linalg.Vec2d;
import org.fairsim.linalg.Vec3d;

import ij.ImagePlus;
import ij.ImageStack;
import ij.ImageListener;
import ij.gui.ImageWindow;
import ij.gui.StackWindow;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.OvalRoi;
import ij.process.FloatProcessor;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import java.awt.event.WindowEvent;
import java.awt.event.WindowStateListener;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;

/** A wrapper around ImageStack, ImagePlus and 
 *  ImageWindow, for interactive result display */
public class DisplayWrapper implements ImageDisplay, ImageListener  {

    // for convenience
    final private int width, height;
   
    // ImageJ components
    private ImageStack  is;
    private ImagePlus   ip;
    private int currentPosition=0;
    private String ourTitle="[no title]";

    // list of images, labels, markers
    private List<ImageVector>  refs = new ArrayList<ImageVector>();
    private List<String> refLabels = new ArrayList<String>();
    private List< List< ImageDisplay.Marker >> refMarkers =
	new  ArrayList< List< ImageDisplay.Marker >>();
    
    // list of listeners
    private List<ImageDisplay.Notify> ourListeners = 
	new ArrayList<ImageDisplay.Notify>();

    /** Creates a DisplayWrapper.
     * @param w Width of image
     * @param h Height of image
     * @param title Title to display (may be 'null')
     **/
    public DisplayWrapper( int w, int h, String title ) {
	width=w; height=h;
	if (title != null) ourTitle = title;
    }
 
    /** Update the image display. Called when display is (de)activated
     *  or when number of images change. */
    void setState( boolean shouldBeActive ) {

	// if this has already been dropped, or
	// there are no images, do not try to activate
	if (refs==null || refs.size()==0)  
	    return;

	// activate
	if (( shouldBeActive == true )&&( ip == null)) {
	    
	    // create stack
	    is   = new ImageStack(width,height);
	    for (int i=0;i<refs.size(); i++) {
	      is.addSlice( refs.get(i).img() );
	      is.setSliceLabel( refLabels.get(i),i+1);
	    }
	    
	    // create ImagePlus
	    ip = new ImagePlus(ourTitle, is );
	    ip.setProperty("org.fairsim.fiji.DisplayWrapper","yes");
	    ip.setPosition( currentPosition+1 );

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
	    
	    // register us as listener for image updates
	    ImagePlus.addImageListener( this );
	}
	
	// deactivate
	if (shouldBeActive == false) {
	    
	    final DisplayWrapper listener = this;
	    
	    if (SwingUtilities.isEventDispatchThread()) {

		// deregister our Listener
		ImagePlus.removeImageListener(listener);
    
		// throw away our ImageJ components
		if (ip!=null) {
		    ip.changes = false;
		    ip.close();
		}

	    } else {
		// TODO: for reasons currently unclear to me, this does not work
		// at all, and just blocks the whole program. For now, only
		// call 'drop' from the Swing thread, I guess
		throw new RuntimeException("drop() called from Thread other"+
		    "then EventDispatch, currently not supported");

		/*
		Tool.trace("DisplayWrapper: switching to EventDispatch");
	
		try {
		    SwingUtilities.invokeAndWait(new Runnable() {
			public void run() {
		
			    ImagePlus.removeImageListener(listener);
			    ip.changes = false;
			    ip.close();
			}
		    }); 
		} catch (InterruptedException ex) {
		    Tool.trace("ERR: DisplayWrapper interrupted");
		} catch (InvocationTargetException ex) {
		    Tool.trace("ERR: DisplayWrapper called wrong target");
		} */
	    }


	    // display the ImagePlus, do this in the Swing Thread
	    /*
	    try {
		SwingUtilities.invokeAndWait(new Runnable() {
		    public void run() {
		
			// deregister our Listener
			ImagePlus.removeImageListener(listener);
	    
			// throw away our ImageJ components
			if (ip!=null) {
			    ip.changes = false;
			    ip.close();
			}
		    }
		});
	    } catch (InterruptedException ex) {
		Tool.trace("ERR: DisplayWrapper interrupted");
	    } catch (InvocationTargetException ex) {
		Tool.trace("ERR: DisplayWrapper called wrong target");
	    } */
	    is=null; ip=null;
	}
    }

    // ------ our ImageDisplay interface ------
   
    /** Creates and destroys the ImageWindow. */
    @Override
    public void display() {
	setState(true);
    }

    /** Drops all stored data, including listeners */
    @Override
    public void drop() {
	setState(false);
	refs=null;
	refLabels=null;
	refMarkers=null;
	if (ourListeners!=null)
	    ourListeners.clear();
	ourListeners=null;
    }


    /** {@inheritDoc} */
    @Override
    public int getCount() { return refs.size(); }
    
    /** {@inheritDoc} */
    @Override
    public int width() { return width; }
    
    /** {@inheritDoc} */
    @Override
    public int height() { return height; }


    /** {@inheritDoc} */
    @Override
    public void addImage( Vec2d.Real v, String label, ImageDisplay.Marker ... m) {

	// check the marker list for null pointers
	for ( ImageDisplay.Marker i : m ) {
	    if (i==null)
		throw new NullPointerException("Marker list contains an elements that is null");
	}

	// copy over the image content
	ImageVector img = ImageVector.create( width, height );
	Vec2d.failSize( img, v );
	img.copy(v);
	String l = (label==null)?("no label"):label;

	// add content, label and markers to our storage
	refs.add( img );
	refLabels.add(  l );
	refMarkers.add( new ArrayList<ImageDisplay.Marker>(Arrays.asList(m)) );

	if (is!=null && ip!=null) {
	    is.addSlice( img.img() );
	    ip.setPosition( getCount());
	}
    }
    
    /** {@inheritDoc} */
    @Override
    public void addImage( Vec3d.Real v, boolean project, String label, ImageDisplay.Marker ... m) {
	
	String l = (label==null)?("no label"):label;
	
	// check the marker list for null pointers
	for ( ImageDisplay.Marker i : m ) {
	    if (i==null)
		throw new NullPointerException("Marker list contains an elements that is null");
	}
	
	// copy over the image content
	if (project) {
	    ImageVector img = ImageVector.create( width, height );
	    img.project(v);

	    // add content, label and markers to our storage
	    refs.add( img );
	    refLabels.add("(3d-proj):"+  l );
	    refMarkers.add( new ArrayList<ImageDisplay.Marker>(Arrays.asList(m)) );
	} else {
	    for (int z=0; z<v.vectorDepth(); z++) {
		ImageVector img = ImageVector.create( width, height );
		img.slice(v,z);

		// add content, label and markers to our storage
		refs.add( img );
		refLabels.add("(z"+z+"): "+  l );
		refMarkers.add( new ArrayList<ImageDisplay.Marker>(Arrays.asList(m)) );
	    }
	}
    }


    /** Set the image at the n'th position. 
     *	@param v Vector content, gets copied
     *	@param n Position, zero-based
     *	@param label Label of the image, if 'null' label remains unchanged
     *	@param m Image markers to draw
     *  */
    @Override
    public void setImage( Vec2d.Real v, int n, String label, ImageDisplay.Marker ... m) {
	
	// check the marker list for null pointers
	for ( ImageDisplay.Marker i : m ) {
	    if (i==null)
		throw new NullPointerException("Marker list contains an elements that is null");
	}
	
	// copy over content
	ImageVector img = refs.get(n);
	Vec2d.failSize( img, v );
	img.copy(v);

	// copy over label 
	if ( label != null ) 
	    refLabels.set( n, label);
	if (( label != null ) && ( is != null) )
	    is.setSliceLabel( label, n+1 );

	// copy over markers
	if (m.length!=0) {
	    refMarkers.set( n, new ArrayList<ImageDisplay.Marker>(Arrays.asList(m)) ); 
	} else {
	    refMarkers.set( n, new ArrayList<ImageDisplay.Marker>() ); 
	}

	// update the image if we currently display it
	if ( ip != null && ( ip.getCurrentSlice()-1 == n) ) {
	    ip.updateAndDraw();
	}
    }


    // ------ Object passable as ImageDisplay.Factory ------

    /** Returns a Factory for DisplayWrappers, and starts an
     * embedded instance of ImageJ */
    public static ImageDisplay.Factory getTestingFactory() {
	ij.ImageJ ijInstance  = new ij.ImageJ(ij.ImageJ.EMBEDDED);
	return DisplayWrapper.getFactory();
    }
    

    /** Returns a Factory for DisplayWrappers */
    public static ImageDisplay.Factory getFactory() {
	return new ImageDisplay.Factory() {
	    public ImageDisplay create(int w, int h, String title) {
		return new DisplayWrapper(w,h, title);	
	    }
	};
    }

    // ------ Our ImageListener interface ------
   
    /** Listeners added here get notified on image position change */
    public void addListener( ImageDisplay.Notify n ) {
	ourListeners.add( n );
    }
    /** Listeners added here get notified on image position change */
    public void removeListener( ImageDisplay.Notify n ) {
	ourListeners.remove( n );
    }


    // ------ Fijis ImageListener interface ------
    
    public void imageClosed(ImagePlus imp) {
	if (imp!=ip) return;
	
	// this deadlocks (rarely, but sometimes), 
	// as setState(false) tries to close the image
	//setState(false);
	
	// instead, only de-register our and clear references
	ImagePlus.removeImageListener(this);
	is=null; ip=null;
    }
    
    public void imageOpened(ImagePlus imp)  {
    
    }
    
    public void imageUpdated(ImagePlus imp)  {
	// check if the notification is for us
	if (imp!=ip) return;

	//Tool.trace("Image update listener called");

	if (currentPosition == ip.getCurrentSlice()-1)
	    return;

	// update the position
	currentPosition = ip.getCurrentSlice()-1;
	for ( ImageDisplay.Notify l : ourListeners )
	    l.newPosition( this, currentPosition );

	// Add markers to ImagePlus as Overlays
	List<ImageDisplay.Marker > markers = refMarkers.get(currentPosition);	
	
	if (markers.size()>0) {
	    Overlay ov = new Overlay();
	    for (ImageDisplay.Marker m : markers) {
		if (m.drawElipse) {
		    ov.add( new OvalRoi( m.x-m.w/2, m.y-m.h/2, m.w, m.h));
		} else {
		    ov.add( new Roi( m.x-m.w/2, m.y-m.h/2, m.w, m.h));
		}

	    }
	    ip.setOverlay(ov);
	} else {
	    ip.setOverlay(null);
	    //ip.setHideOverlay(true);
	}
    
    }



}
