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
import ij.gui.GenericDialog;

import ij.process.ImageProcessor;

import org.fairsim.utils.Args;



/** 
 * Small plugin that generate bead surfaces
 * */
public class SimPatternSimulator implements PlugIn {

    
    public void run(String attr) { 


	ImageProcessor ip = IJ.getProcessor();

	if ( ip.getWidth() != ip.getHeight() ) {
	    IJ.showMessage("Please use a square image");
	    return;
	}

	if ( IJ.getImage().getType() != ImagePlus.GRAY8 &&
	     IJ.getImage().getType() != ImagePlus.GRAY16 &&
	     IJ.getImage().getType() != ImagePlus.GRAY32 ) {
	    IJ.showMessage("Please use 8bit, 16bit or float gray scale");
	}


	String imgName = IJ.getImage().getTitle();

	// parse the parameters
	// TODO: This could be done with less code using reflections, but...
	GenericDialog gd = new GenericDialog("SIM Simulator");

	gd.addNumericField("downsample", 4, 0);
	gd.addNumericField("pixel size (nm)", 80, 3);
	gd.addNumericField("angles", 3, 0);
	gd.addNumericField("phases", 3, 0);
	gd.addNumericField("SIM res. improvement", 1.9, 1);
	gd.addNumericField("SIM modulation depth", 0.78,2);

	gd.addNumericField("obj. NA", 1.4, 2);
	gd.addNumericField("em. wavelength (nm)", 525, 0);
	gd.addNumericField("photons per input count", 1, 2);

	gd.addNumericField("camera (offset)", 100, 0);
	gd.addNumericField("camera (noise)", 2, 0);


	gd.addNumericField("PRNG seed", 42, 0);

	gd.showDialog();
	if (gd.wasCanceled()) {
	    return;
	}

	final int downSample	    = (int)gd.getNextNumber();
	final double pxlSize	    = gd.getNextNumber();
	final int angles	    = (int)gd.getNextNumber();
	final int phases    	    = (int)gd.getNextNumber();
	final double simResImpr     = gd.getNextNumber()-1;
	final double simModDepth    = gd.getNextNumber();
	
	final double NA	            = gd.getNextNumber();
	final double wavelength     = gd.getNextNumber();
	final double photons	    = gd.getNextNumber();
	
	final double bgrOffset	    = gd.getNextNumber();
	final double bgrNoise	    = gd.getNextNumber();
	
	final int prngSeed	    = (int)gd.getNextNumber();


	final int inputSize	    = ip.getWidth();
	if (inputSize % downSample != 0 ) {
	    IJ.showMessage("image size has to be a multiple of 'downsample'");
	}
	final int outputSize	    = ip.getWidth()/downSample;


	Vec2d.Real groundTruth = ImageVector.copy( ip );
	Random noisePRNG  = new Random(prngSeed*2+123);

	FairSim_ImageJplugin.setLog(true);

	// run the simulation
	DisplayWrapper dwSI = new DisplayWrapper( 
	    outputSize, outputSize,imgName+"_simulSIM");
	DisplayWrapper dwWF = new DisplayWrapper( 
	    outputSize, outputSize,imgName+"_simulWF");
	DisplayWrapper dwGT = new DisplayWrapper( 
	    inputSize, inputSize,imgName+"_simul_intermediate");
	DisplayWrapper dwIL = new DisplayWrapper( 
	    inputSize, inputSize,imgName+"_sim_field");

	dwSI.setPixelSize( pxlSize );
	dwWF.setPixelSize( pxlSize );
	dwGT.setPixelSize( pxlSize / downSample );
	dwIL.setPixelSize( pxlSize / downSample );


	   
	dwGT.addImage( groundTruth, "Ground truth");
	
	// initialize OTF
	final double cyclPerMicron = 1./(pxlSize/1000. * outputSize);
	OtfProvider otf = OtfProvider.fromEstimate(NA, wavelength, .7);
	otf.setPixelSize( cyclPerMicron );


	// create wide-field
	Vec2d.Cplx img = Vec2d.createCplx( groundTruth );

	img.copy( groundTruth );
	img.fft2d(false);
	otf.applyOtf( img,0 );

	dwGT.addImage( SimUtils.spatial( img ), "Widefield");


	// create SIM
	Vec2d.Cplx sim  = Vec2d.createCplx( groundTruth );
	Vec2d.Real tmp  = Vec2d.createReal( groundTruth );
	
	ImageVector tmp2  = ImageVector.create( outputSize, outputSize );
	ImageVector resWF = ImageVector.create( outputSize, outputSize );

	final double abbeLimit = 1. / otf.getCutoff();
	final double simPttrLen = abbeLimit / simResImpr ;

	final double k0 = otf.getCutoff()*simResImpr / cyclPerMicron;


	Tool.trace(" Abbe (um): "+abbeLimit+" sim spacing (um): "+simPttrLen+" k0 (len): "+k0);


	for ( int a=0; a<angles; a++) { 
	    
	    double kx = Math.sin( (a/3.+.2)*Math.PI*2 )*k0;
	    double ky = Math.cos( (a/3.+.2)*Math.PI*2 )*k0;
	   
    

	    for ( int p=0; p<phases; p++) {
	    
		Tool.trace(String.format("calculating SIM for: kx %5.3f ky %5.3f phase %6.3f",
		    kx,ky,(p*Math.PI*2/(double)phases)));
		
		simPattern( sim, kx, ky, p*Math.PI * 2 / (double)phases, simModDepth);
		tmp.copy( sim );

		dwIL.addImage(  tmp , "SIM field  a:"+a+" p:"+p);
		
		img.copy( groundTruth );
		img.times( sim );
		tmp.copy( img );
		dwGT.addImage( tmp , "SIM signal a:"+a+" p:"+p);
		img.fft2d( false );
		otf.applyOtf( img,0 );
		img.fft2d( true );
		tmp.copy( img );
		dwGT.addImage( tmp, "SIM band limited a:"+a+" p:"+p);

		// convert to photons
		tmp2.zero();

		for ( int y=0;y<outputSize;y++) {
		    for ( int x=0;x<outputSize;x++) {
    
			// down-sample
			float acc = 0;
			
			for ( int y1 = 0; y1< downSample; y1++) {
			    for ( int x1 = 0; x1< downSample; x1++) {
				acc += tmp.get( x*downSample + x1, y*downSample+y1); 
			    }
			}

			acc *= photons/downSample/downSample;
			acc += (float)(noisePRNG.nextGaussian() * Math.sqrt( acc )); 

			acc += (float)(bgrOffset + noisePRNG.nextGaussian()*bgrNoise);

			if (acc<0) {
			    acc=0;
			}

			tmp2.set( x,y,(int)acc);
		    }
		}


		dwSI.addImage( tmp2, "simulated SIM a:"+a+" p:"+p);
		resWF.add(tmp2);
	    }
	}

	dwWF.addImage(resWF,imgName+"_simulWF");
	dwGT.display();
	dwIL.display();
	dwSI.doMarkAsDisplayWrapper(false);
	dwSI.display();
	dwWF.display();
    }

    private static void simPattern(Vec2d.Cplx dat,
	    final double kx, final double ky, final double pha, final double modDepth ) {

	    final int len = dat.vectorWidth();
	    
	    for (int y=0;y<len; y++)
	    for (int x=0;x<len; x++) {
		double v = Math.cos( 2*Math.PI* (-kx*x + ky*y)/(len) + pha );
		dat.set(x,y,new Cplx.Float( (float)((v+1)*0.5*modDepth) + (float)(1-modDepth), 0f)) ;
	    }
    }




}
