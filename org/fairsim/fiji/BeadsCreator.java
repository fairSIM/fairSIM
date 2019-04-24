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




/** 
 * Small plugin that generate bead surfaces
 * */
public class BeadsCreator {



    final int width,height,oversample;
    final double pxlSize;

    Vec2d.Real groundTruth;

    Random posPRNG;

    public BeadsCreator( int w, int h, double pxl, int over ){
	width  = w*over;
	height = h*over;
	pxlSize = pxl/over;
	oversample = over;
    
	groundTruth = Vec2d.createReal( width, height );
    
	posPRNG = new Random(1337);

    }



    public void addBead( double xPos, double yPos, double rad, boolean hollow ) {

	Vec2d.Real dat = groundTruth;

	int s = (int)(2*rad/pxlSize+2);

	for (int y = (int)yPos-s ; y<(int)yPos+s; y++) {
	    for (int x = (int)xPos-s ; x<(int)xPos+s; x++) {

		double dist = Math.hypot( x-xPos, y-yPos ) / (rad/pxlSize);


		if (y<0 || y>= height || x<0 || x>= height) continue;
		
		if (dist>1) {
		    if (dist<2) {
			dat.set( x,y,dat.get(x,y)+(float)1e-6);
		    }
		    continue;
		}


		if (!hollow) {
		    if (dist<0.5) {
			dat.set(x,y,dat.get(x,y)+1);
		    } else {
			dat.set(x,y,dat.get(x,y) + (float)(Math.cos( ((dist-0.5)*2*Math.PI))+1)*.5f);
		    }
		} else {
		    if (dist>.2)
		    dat.set(x,y,dat.get(x,y) + (float)(Math.sin( (dist-.2)/.8*Math.PI)));
		}

	    }
	}

    }


    public boolean addRandomBead( double rad, boolean hollow ) {

	boolean success = false;

	for (int i=0; i<20; i++) {
	    
	    double x = posPRNG.nextDouble()*width;
	    double y = posPRNG.nextDouble()*height;

	    if ( groundTruth.get((int)x,(int)y)<1e-8 && !success ) {
		addBead( x,y , rad, hollow);
		success = true; // don't return to call PRNG equally often each time
	    }
	}
    
	return success;
    }

    public static void simPattern(Vec2d.Cplx dat,
	    final double kx, final double ky, final double pha ) {

	    final int len = dat.vectorWidth();
	    
	    for (int y=0;y<len; y++)
	    for (int x=0;x<len; x++) {
		double v = Math.cos( 2*Math.PI* (-kx*x + ky*y)/(len) + pha );
		dat.set(x,y,new Cplx.Float( (float)(v+1)*0.3f+.4f, 0f)) ;
	    }
    }


    

    public static void main( String [] args ) {

	
	final double simResImpr = 1.02;	// TIRF
	final int outputSize = 256;
	final int photons = 25;

	ij.ImageJ inst = new ij.ImageJ( ij.ImageJ.EMBEDDED);
    

	BeadsCreator bc = new BeadsCreator(outputSize,outputSize,0.078,8);
	DisplayWrapper dwSI = new DisplayWrapper( outputSize, outputSize,"Simulated data");
	DisplayWrapper dwGT = new DisplayWrapper( bc.width, bc.height,"Bead Simulation: full res");

	Random hollowPRNG = new Random(1234);
	Random noisePRNG  = new Random(4321);


	for (int i=0; i<300; i++) {
	    //bc.addRandomBead(.2, hollowPRNG.nextBoolean());
	    bc.addRandomBead(.2, true);
	}
	   
	dwGT.addImage( bc.groundTruth, "Ground truth");
	
	// initialize OTF
	final double cyclPerMicron = 1./(bc.pxlSize * bc.width);
	OtfProvider otf = OtfProvider.fromEstimate(1.4, 525, .7);
	otf.setPixelSize( cyclPerMicron );


	// create wide-field
	Vec2d.Cplx img = Vec2d.createCplx( bc.width, bc.height );

	img.copy( bc.groundTruth );
	img.fft2d(false);
	otf.applyOtf( img,0 );

	dwGT.addImage( SimUtils.spatial( img ), "Widefield");
	dwGT.display();


	// create SIM
	Vec2d.Cplx sim  = Vec2d.createCplx( bc.width, bc.height );
	Vec2d.Real tmp  = Vec2d.createReal( bc.width, bc.height );
	
	Vec2d.Real tmp2 = Vec2d.createReal( outputSize, outputSize );

	final double abbeLimit = 1. / otf.getCutoff();
	final double simPttrLen = abbeLimit / simResImpr ;

	final double k0 = otf.getCutoff()*simResImpr / cyclPerMicron;

	Tool.trace(" abbe: "+abbeLimit+" sim: "+simPttrLen+" k0: "+k0);


	for ( int a=0; a<3; a++) { 
	    
	    double kx = Math.sin( (a/3.+.2)*Math.PI*2 )*k0;
	    double ky = Math.cos( (a/3.+.2)*Math.PI*2 )*k0;
	   
	    Tool.trace("kx "+kx+" ky "+ky);
    

	    for ( int p=0; p<3; p++) {
		
		simPattern( sim, kx, ky, p*Math.PI * 2 / 3.);
		tmp.copy( sim );

		dwGT.addImage(  tmp , "SIM field  a:"+a+" p:"+p);
		
		img.copy( bc.groundTruth );
		img.times( sim );
		tmp.copy( img );
		dwGT.addImage( tmp , "SIM signal a:"+a+" p:"+p);
		img.fft2d( false );
		otf.applyOtf( img,0 );
		img.fft2d( true );
		tmp.copy( img );
		dwGT.addImage( tmp, "SIM folded a:"+a+" p:"+p);

		// convert to photons
		tmp2.zero();

		for ( int y=0;y<outputSize;y++) {
		    for ( int x=0;x<outputSize;x++) {
    
			// down-sample
			float acc = 0;
			
			for ( int y1 = 0; y1< bc.oversample; y1++) {
			    for ( int x1 = 0; x1< bc.oversample; x1++) {
				acc += tmp.get( x*bc.oversample + x1, y*bc.oversample+y1); 
			    }
			}

			acc *= photons;
			acc += (float)(noisePRNG.nextGaussian() * Math.sqrt( acc )); 

			acc += (float)(98 + noisePRNG.nextGaussian()*25);

			tmp2.set( x,y,acc);
		    }
		}

		dwSI.addImage( tmp2, "meas TIRF SIM a:"+a+" p:"+p);
	    }
	}

	dwSI.display();
    }




}
