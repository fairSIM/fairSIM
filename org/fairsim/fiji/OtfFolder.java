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

import org.fairsim.sim_algorithm.OtfProvider;
import org.fairsim.sim_algorithm.SimUtils;

import org.fairsim.utils.Conf;
import org.fairsim.utils.Tool;
import org.fairsim.linalg.Vec2d;
import org.fairsim.linalg.Vec;
import org.fairsim.linalg.Cplx;

import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.util.Random;

import ij.plugin.PlugIn;
import ij.ImageStack;
import ij.ImagePlus;
import ij.IJ;




/** Small plugin that folds images with OTFs.
 *  This plugin lets the user select one or more OTF files,
 *  and folds each image in the currently selected stack with
 *  each OTF.
 * */
public class OtfFolder implements PlugIn {

    ImageStack inputStack ;
    int width, height;

    double pxlNM = 80;
    double wParam = 0.05;

    public void run(String arg) {

	ImagePlus curImg = IJ.getImage();
	if ( curImg == null ) {
	    return;
	}

	inputStack = curImg.getStack();
	if (inputStack == null) {
	    return;
	}   

	width  = inputStack.getWidth();
	height = inputStack.getHeight();

	try {
	    foldWithOtfs();
	} catch (Exception e) {
	    Tool.trace("OTF File Error: "+e);
	    e.printStackTrace( System.err );
	}

    }

    void foldWithOtfs()
	throws  Conf.SomeIOException, Conf.EntryNotFoundException {


	// let the user select an OTF or a folder scanned for OTFs
	JFileChooser fs =  new JFileChooser();
	FileNameExtensionFilter fsFilter 
	    = new FileNameExtensionFilter("OTF XML files", "xml");
	
	fs.setMultiSelectionEnabled(true);
	fs.setFileFilter(fsFilter);

	int ret = fs.showOpenDialog(null);
	if ( ret != JFileChooser.APPROVE_OPTION) {
	    return;
	}

	final File [] otfFileList = fs.getSelectedFiles();

	// output the OTFs and images
	ImageStack otfStack = new ImageStack( width, height );
	ImageStack outStack = new ImageStack( width, height );


	// loop through the OTFs
	for ( File otfFile : otfFileList ) {

	    Conf otfCfg = Conf.loadFile( otfFile ); 
	    OtfProvider otf = OtfProvider.loadFromConfig( otfCfg );

	    otf.setPixelSize( 1./(width*pxlNM/1000.) );
	   
	    Vec2d.Cplx otfVector = Vec2d.createCplx(width,height);
	    ImageVector otfImg = ImageVector.create(width, height);
	    otf.writeOtfVector( otfVector, 0,0,0);
	    otfImg.copy(otfVector);

	    otfStack.addSlice( otfFile.toString(), otfImg.img() );

	    // loop through the images
	    for ( int i=1; i<= inputStack.getSize(); i++) {
		
		for (int noise=0; noise<2; noise++) {
		
		    
		    Vec2d.Cplx iVec  = Vec2d.createCplx( width, height);
		    ImageVector inputImageVector =
			ImageVector.copy( inputStack.getProcessor(i)) ;

		    iVec.copy( inputImageVector );

		    iVec.fft2d(true);
		    otf.applyOtf( iVec ,0);
		    iVec.fft2d(false);

		    

		    // if noise == 1, apply Poisson-like noise
		    Random rnd = new Random(2342);
		    if ( noise == 1 ) {
			for (int k=0; k<iVec.vectorSize(); k++) {
			    float val = iVec.get(k).abs();
			    val = val + (float)(rnd.nextGaussian() * Math.sqrt(val));
			    iVec.set(k, new Cplx.Float(val));
			}
		    }


		    ImageVector outImg = ImageVector.create( width, height );
		    outImg.copy(   iVec );
		    SimUtils.clipAndScale( outImg, false, true );

		    // do some Wiener filtering
		    iVec.fft2d(true);
		    Vec2d.Cplx wDenom = Vec2d.createCplx( width, height );
		    for (int j=0; j<width*height; j++) {
			wDenom.set( j, new Cplx.Double(wParam) );
		    }
		    wDenom.addSqr( otfVector );
		    
		    ImageVector wImg = ImageVector.create( width, height );
		    wImg.copy( wDenom );
		    otfStack.addSlice( "wf", wImg.img() );
		    
		    wDenom.reciproc();
		    
		    otf.applyOtf( iVec ,0);
		    iVec.times( wDenom );
		    Vec2d.Cplx apo = Vec2d.createCplx( width, height );
		    otf.writeApoVector( apo, .6, 2 );
		    iVec.times( apo );
		    iVec.fft2d( false );

		    ImageVector outImgFiltered = ImageVector.create( width, height );
		    outImgFiltered.copy(  iVec  );
		    SimUtils.clipAndScale( outImgFiltered, false, true );

		    outStack.addSlice( "i: "+i+" orig ("+otfFile.getName()+")", inputImageVector.img());
		    outStack.addSlice( "i: "+i+" fold ("+otfFile.getName()+")", outImg.img());
		    outStack.addSlice( "i: "+i+" filt ("+otfFile.getName()+")", outImgFiltered.img());
		}
	    }


	}


	// show the results
	ImagePlus otfDisplay = new ImagePlus("OTFs", otfStack);
	otfDisplay.show();
	ImagePlus outDisplay = new ImagePlus("folded images", outStack);
	outDisplay.show();


	

    }

    

    public static void main( String [] args ) {

	ij.ImageJ inst = new ij.ImageJ( ij.ImageJ.EMBEDDED);
    
	if (args.length==0) {
	    IJ.open();
	} else {
	    IJ.open(args[0]);
	}


	OtfFolder us = new OtfFolder();
	us.run("");

    }




}
