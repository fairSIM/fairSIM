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
import org.fairsim.linalg.Transforms;
import org.fairsim.utils.ImageDisplay;


import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.io.FileNotFoundException;
import java.nio.FloatBuffer;
import java.nio.ByteBuffer;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.util.Random;

import ij.plugin.PlugIn;
import ij.ImageStack;
import ij.ImagePlus;
import ij.IJ;

import ij.gui.GenericDialog;


/** Small plugin that folds images with OTFs.
 *  This plugin lets the user select one or more OTF files,
 *  and folds each image in the currently selected stack with
 *  each OTF.
 * */
public class OtfFromTextFile implements PlugIn {


    public void run(String arg) {

	

	// let the user select an OTF or a folder scanned for OTFs
	JFileChooser fs =  new JFileChooser();
	
	//FileNameExtensionFilter fsFilter 
	//    = new FileNameExtensionFilter("OTF XML files", "xml");
	
	//fs.setMultiSelectionEnabled(true);
	//fs.setFileFilter(fsFilter);

	int ret = fs.showOpenDialog(null);
	if ( ret != JFileChooser.APPROVE_OPTION) {
	    return;
	}


	BufferedReader bfReader;

	try {
	    bfReader = new BufferedReader( new FileReader( fs.getSelectedFile()));
	} catch ( FileNotFoundException  e) {
	    return;
	}
		

	GenericDialog gd = new GenericDialog("OTF converter (from text file)");
	gd.addNumericField("Column", 1,0);
	gd.addNumericField("1/um per step", 0.04,3);
	gd.addNumericField("em. Wavelength", 525, 0, 5, "nm");
	gd.addNumericField("obj. NA", 1.45, 2);
	gd.addCheckbox("Store the OTF", false );

	gd.showDialog();


	int col = (int)gd.getNextNumber();
	double cyclesPerMicron = gd.getNextNumber();
	int wavelength = (int)gd.getNextNumber();
	double objNA = gd.getNextNumber();
	boolean storeOtf = gd.getNextBoolean();


	if (! gd.wasOKed() ) {
	    return;
	}
    
	ArrayList<Double> otfVal = new ArrayList<Double>();

	String line;
	try {
	    double pos = 0;
	    while (	(line = bfReader.readLine()) != null ) {
		if ( line.trim().startsWith("#") || line.trim().isEmpty() ) {
		    continue;
		}
		double val =  Double.parseDouble( line.split("\\s+")[ col -1] );
		IJ.log(String.format(" pos %4.3f val %4.3f", pos ,val)); 
		pos += cyclesPerMicron;
		otfVal.add( val );
	    }
	} catch ( java.io.IOException e ) {
	    IJ.log("IO Exception while parsing file: "+e);
	    return;
	}

	IJ.log(" Read " + otfVal.size() + " entries ");

	
	Conf cfg = new Conf("fairsim");
	Conf.Folder otfCfg = cfg.r().mk("otf2d");
	
	otfCfg.newDbl("NA").setVal( objNA );
	otfCfg.newInt("emission").setVal( wavelength );
	Conf.Folder data =otfCfg.mk("data");
	data.newInt("bands" ).setVal(1);
	data.newDbl("cycles").setVal( cyclesPerMicron );
	data.newInt("samples" ).setVal( otfVal.size() );
	
	byte [] band   = new byte[ 2 * 4 * otfVal.size() ];
	FloatBuffer fb = ByteBuffer.wrap(  band ).asFloatBuffer();
	for (int i=0; i<otfVal.size(); i++) {
	    fb.put( (float)(double)otfVal.get(i) );
	    fb.put( 0.0f );
	}

	data.newData("band-0").setVal( band );

	ImageDisplay idp = new DisplayWrapper(512,512,"OTF visualisation");  

	OtfProvider otfPr;

	try {
	    otfPr = OtfProvider.loadFromConfig( cfg) ;
	} catch ( Conf.EntryNotFoundException e ) {
	    IJ.log("IO Exception in OTF conf: "+e);
	    return;
	}

	Vec2d.Cplx otfVector = Vec2d.createCplx(512,512);
	otfPr.setPixelSize( cyclesPerMicron /2);
	otfPr.writeOtfVector( otfVector, 0,0,0);
	Vec2d.Real tmp  = Vec2d.createReal(512,512);
	tmp.copy( otfVector );
	Transforms.swapQuadrant( tmp );
	idp.addImage( tmp, "OTF" );
	idp.display();

	
	if ( storeOtf ) {
	    JFileChooser fc = new JFileChooser();
	    int returnVal = fc.showSaveDialog(IJ.getInstance());

	    File fSaveObj = null;
	    if (returnVal == JFileChooser.APPROVE_OPTION) {
		fSaveObj = fc.getSelectedFile();
	    }
	    else {
		return;
	    }

	    try {
		cfg.saveFile( fSaveObj.getAbsolutePath());
	    } catch (Exception e) {
		IJ.showMessage("Error saving: "+e);
		return;
	    }
	}

    }

    

    public static void main( String [] args ) {

	ij.ImageJ inst = new ij.ImageJ( ij.ImageJ.EMBEDDED);
	OtfFromTextFile us = new OtfFromTextFile();
	us.run("");

    }


}
