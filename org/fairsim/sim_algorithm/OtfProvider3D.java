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

package org.fairsim.sim_algorithm;

import org.fairsim.linalg.Vec2d;
import org.fairsim.linalg.Vec3d;
import org.fairsim.linalg.Cplx;
import org.fairsim.linalg.MTool;

import org.fairsim.utils.Tool;
import org.fairsim.utils.Conf;
import org.fairsim.utils.SimpleMT;


/**
 * OTFs and associated functions (attenuation, apotization, ...).
 * Provides loading, saving, simple estimation, conversion
 * from 2D (radially symmetric, in phys. units) to 3D vectors.
 * */
public class OtfProvider3D {
    
    // --- internal parameters ----

    // vals[band][idx], where idx = cycles / cyclesPerMicron
    private Vec2d.Cplx [] vals	=  null; 
    
    // physical units
    private double cyclesPerMicronLateral;
    private double cyclesPerMicronAxial;
    private double na, immersion_n=1.518, lambda, cutOffLateral, cutOffAxial;

    // vector size
    private int samplesLateral, samplesAxial;
    private boolean isMultiBand=true;
    private int maxBand=3;
    
    private double vecCyclesPerMicronLateral=-1;
    private double vecCyclesPerMicronAxial=-1;

    // meta information
    String otfName = "no-name-yet";
    String otfMeta = "no extra information";

    // ------ setup / access methods -------

   /** Returns a short description (GUI display, etc) */
    public String printState(boolean html) {
	String ret ="";
	if (html)
	    ret = String.format("NA %4.2f, \u03bb<sub>em</sub> %4.0f, ", na, lambda);
	else 
	    ret = String.format("NA %4.2f, lambda %4.0f, ", na, lambda);
	
	ret+=String.format("(from file)");
	return ret;
    }


    /** Return the OTF cutoff, unit is cycles/micron */
    public double getCutoff() {
	return cutOffLateral;
    } 

    /** Get the OTF value at 'cycl'.  
     *  @param band OTF band
     *  @param xycycl xy-lateral Position in cycles/micron
     *  @param zcycl z-axial Position in cycles/micron
     * */
    public Cplx.Float getOtfVal(int band, double xycycl, double zcycl) {
	// checks
	if ( !this.isMultiBand ) 
	    band=0;
	if ((band >= maxBand)||(band <0))
	    throw new IndexOutOfBoundsException("band idx too high or <0");
	if (( xycycl < 0 ) || (zcycl < 0))
	    throw new IndexOutOfBoundsException("cylc negative!");
	
	// out of support, return 0
	if (( xycycl >= cutOffLateral )||(zcycl >= cutOffAxial ))
	    return Cplx.Float.zero();
	
	final double xpos = xycycl / cyclesPerMicronLateral;
	final double zpos = zcycl  / cyclesPerMicronAxial;
	
	if ( Math.ceil(xpos) >= samplesLateral || Math.ceil(zpos) >= samplesAxial )
	    return Cplx.Float.zero();

    
	// for now, linear interpolation, could be better with a nice cspline
	int lxPos = (int)Math.floor( xpos );	
	int hxPos = (int)Math.ceil(  xpos );
	float fx = (float)(xpos - lxPos);
	
	int lzPos = (int)Math.floor( zpos );	
	int hzPos = (int)Math.ceil( zpos );
	float fz = (float)(zpos - lzPos);
    
	Cplx.Float r1 = vals[band].get(lxPos, lzPos).mult( 1 - fx );
	Cplx.Float r2 = vals[band].get(hxPos, lzPos).mult( fx );
	Cplx.Float r3 = vals[band].get(lxPos, hzPos).mult( 1 - fx );
	Cplx.Float r4 = vals[band].get(hxPos, hzPos).mult( fx );
    
	Cplx.Float r5 = Cplx.add( r1, r2 ).mult( 1 - fz );
	Cplx.Float r6 = Cplx.add( r3, r4 ).mult( fz );
	
	return Cplx.add( r5, r6 );

    }
   
    /** Sets pixel size, for output to vectors
     *	@param cyclesPerMicron Pixel size of output vector, in cycles/micron */
    public void setPixelSize( double cyclesPerMicronLateral, double cyclesPerMicronAxial ) {
	if (cyclesPerMicronLateral<=0 || cyclesPerMicronAxial <= 0)
	    throw new IllegalArgumentException("pxl size must be positive");
	vecCyclesPerMicronLateral=cyclesPerMicronLateral;
	vecCyclesPerMicronAxial  =cyclesPerMicronAxial;
    }

    // ------ Attenuatson -----

    // TODO: bring back notch filtering for 3D??

    // ------ applying OTF to vectors -------

    /** Multiplies / outputs OTF to a vector. Quite general function,
     *  some wrappers are provided for conveniece. 
     *  @param vec  Vector to write / multiply to
     *	@param band OTF band 
     *	@param kx OTF center position offset x
     *	@param ky OTF center position offset y
     *  @param useAtt if to use attenuation (independent of how {@link #switchAttenuation} is set)
     *  @param write  if set, vector is overridden instead of multiplied
     *  */
    public void otfToVector( final Vec3d.Cplx vec, final int band, 
	final double kx, final double ky, final boolean write ) {
	
	// parameters
	if (vecCyclesPerMicronLateral <=0 || vecCyclesPerMicronAxial <=0 )
	    throw new IllegalStateException("Vector pixel size not initialized");
	final int w = vec.vectorWidth(), h = vec.vectorHeight(), d = vec.vectorDepth();

	//System.out.println(" Cycles: "+vecCyclesPerMicronLateral+" "+vecCyclesPerMicronAxial);

	// loop output vector
	new SimpleMT.StrPFor(0,d) {
	    public void at(int z) {
		for (int y=0; y<h; y++) 
		for (int x=0; x<w; x++) {
		    // wrap to coordinates: x in [-w/2,w/2], y in [-h/2, h/2]
		    double xh = (x<w/2)?( x):(x-w);
		    double yh = (y<h/2)?(-y):(h-y);
		    double zh = (z<d/2)?( z):(d-z);
		    
		    // from these, calculate distance to kx,ky, convert to cycl/microns
		    double rad = MTool.fhypot( xh-kx, yh-ky );
		    double cycllat = rad * vecCyclesPerMicronLateral;
		    double cyclax  = zh  * vecCyclesPerMicronAxial;
		    
		    // over cutoff? just set zero TODO: Math.hypot( lat, ax ) here?
		    if ( cycllat > cutOffLateral || cyclax > cutOffAxial ) {
			vec.set(x,y,z, Cplx.Float.zero());
		    } 
		    // within cutoff?
		    else {
		    
			// get the OTF value
			Cplx.Float val = getOtfVal(band, cycllat, cyclax);

			// multiply to vector or write to vector
			if (!write) {
			    vec.set(x, y, z, vec.get(x,y,z).mult( val.conj() ) );
			    //vec.set(x, y, z, vec.get(x,y,z).mult( val ) );

			} else {
			    vec.set(x, y, z, val );
			}
		    }
		}
	    }
	}; 

    }

    // ------ Applying OTF to vectors ------
    
    /** Multiplied conjugated OTF to a vector.
     *  The desired vector pixel size has to be set (via {@link #setPixelSize}) first.
     *  @param vec  Vector to write to
     *	@param band OTF band */
    public void applyOtf(Vec3d.Cplx vec, final int band) {
	otfToVector( vec, band, 0, 0, false ) ; 
    }

    /** Multiplied conjugated OTF to a vector.
     *  The desired vector pixel size has to be set (via {@link #setPixelSize}) first.
     *  @param vec  Vector to write to
     *	@param band OTF band 
     *	@param kx OTF center position offset x
     *	@param ky OTF center position offset y */
    public void applyOtf(Vec3d.Cplx vec, final int band, double kx, double ky ) {
	otfToVector( vec, band, kx, ky, false ) ; 
    }

    /** Create a 3-dimension, radial symmetric vector of the OTF, centered at kx,ky.
     *  The desired vector pixel size has to be set (via {@link #setPixelSize}) first.
     *  @param vec  Vector to write to
     *	@param band OTF band 
     *	@param kx Position / offset kx
     *	@param ky Poistion / offset ky
     *	*/
    public void writeOtfVector(final Vec3d.Cplx vec, final int band, 
	final double kx, final double ky) {
	otfToVector( vec, band, kx, ky, true ) ; 
    }
    
    
    
    // ------ Apotization ------
    
    public void apotize( final Vec3d.Cplx vec, 
	final double multipleLateral, 
	final double multipleAxial, 
	final boolean useCos ) {
	
	// parameters
	if (vecCyclesPerMicronLateral <=0 || vecCyclesPerMicronAxial <=0 )
	    throw new IllegalStateException("Vector pixel size not initialized");
	final int w = vec.vectorWidth(), h = vec.vectorHeight(), d = vec.vectorDepth();

	Tool.trace(String.format("cutoff lat %7.5f, axial %7.5f", cutOffLateral, cutOffAxial));

	// loop output vector
	new SimpleMT.StrPFor(0,d) {
	    public void at(int z) {
		for (int y=0; y<h; y++) 
		for (int x=0; x<w; x++) {
		    // wrap to coordinates: x in [-w/2,w/2], y in [-h/2, h/2]
		    double xh = (x<w/2)?( x):(x-w);
		    double yh = (y<h/2)?(-y):(h-y);
		    double zh = (z<d/2)?( z):(d-z);
		    
		    // from these, calculate distance to kx,ky, convert to cycl/microns
		    double rad = MTool.fhypot( xh, yh );
		    double cycllat = rad * vecCyclesPerMicronLateral;
		    double cyclax  = zh  * vecCyclesPerMicronAxial;
		    

		    double distL = cycllat / cutOffLateral / multipleLateral;
		    double distA = cyclax  / cutOffAxial / multipleAxial;
		    double dist  = MTool.fhypot( distL, distA );

		    if ( dist > 1.0 ) {
			vec.set(x,y,z, Cplx.Float.zero());
		    } 
		    // within cutoff?
		    else {
			// multiply by apo factor
			Cplx.Float val = vec.get(x,y,z);
			double factor = (useCos)?( MTool.fcos( dist * Math.PI /2)):(1-dist);
			vec.set(x, y, z, vec.get(x,y,z).mult( factor ) );
		    }
		}
	    }
	}; 

    }

    
    
    // ------ Load / Save operations ------

    /** Copy an existing OTF */
    public OtfProvider3D duplicate() {
	
	OtfProvider3D ret = new OtfProvider3D();

	ret.cyclesPerMicronLateral	= this.cyclesPerMicronLateral;
	ret.cyclesPerMicronAxial	= this.cyclesPerMicronAxial;
	ret.na				= this.na;
	ret.immersion_n			= this.immersion_n;
	ret.lambda			= this.lambda;
	ret.cutOffLateral		= this.cutOffLateral;
	ret.cutOffAxial			= this.cutOffAxial;
	ret.samplesLateral		= this.samplesLateral;
	ret.samplesAxial		= this.samplesAxial;
	ret.isMultiBand			= this.isMultiBand;
	ret.maxBand			= this.maxBand;
	ret.vecCyclesPerMicronLateral	= this.vecCyclesPerMicronLateral;
	ret.vecCyclesPerMicronAxial	= this.vecCyclesPerMicronAxial;

	ret.vals = new Vec2d.Cplx[ this.vals.length ];
	for (  int i=0; i< this.vals.length; i++)
	    ret.vals[i] = this.vals[i].duplicate();

	return ret;
    }


    /** Create an OTF stored in a string representation, usually read from
     *  file. 
     *	@param cfg The config to load from
     *  */
    @Deprecated
    public static OtfProvider3D loadFromConfig( Conf cfg ) 
	throws Conf.EntryNotFoundException {

	Conf.Folder fld = cfg.r().cd("otf3d");
	return loadFromConfig( fld );
    }

    /** Create an OTF stored in a string representation, usually read from
     *  file. 
     *	@param cfg The config to load from
     *  */
    public static OtfProvider3D loadFromConfig( Conf.Folder fld ) 
	throws Conf.EntryNotFoundException {

	OtfProvider3D ret = new OtfProvider3D();

	// main parameters
	ret.na		= fld.getDbl("NA").val();
	
	if (fld.contains("n-immersion")) { // TODO: remove once this is part of the OTF creator?!
	    ret.immersion_n = fld.getDbl("n-immersion").val();
	}
	
	ret.lambda	= fld.getInt("emission").val();
	
	if (!fld.contains("data"))
	    throw new RuntimeException("No data section found, needed for 3d");

	// copy meta data
	if (fld.contains("otf-name"))
	    ret.otfName = fld.getStr("otf-name").val();

	if (fld.contains("otf-meta"))
	    ret.otfMeta = fld.getStr("otf-meta").val();
	    
	// copy parameters
	Conf.Folder data = fld.cd("data");
	ret.maxBand = data.getInt("bands").val();
	ret.isMultiBand = (ret.maxBand>1);
	
	ret.samplesLateral  = data.getInt("samples-lateral").val(); 
	ret.samplesAxial    = data.getInt("samples-axial").val(); 

	ret.cyclesPerMicronLateral = data.getDbl("cycles-lateral").val();
	ret.cyclesPerMicronAxial   = data.getDbl("cycles-axial").val();

	// calculate the cutoff
	ret.calcCutOff();


	// init bands
	ret.vals	= Vec2d.createArrayCplx( ret.maxBand, 
	    ret.samplesLateral, ret.samplesAxial );

	// copy bands
	for (int b=0; b<ret.maxBand; b++) {
	    byte  [] bytes = data.getData(String.format("band-%d",b)).val();
	    float [] val   = Conf.fromByte( bytes );
	  
	    if (val.length != 2*ret.samplesAxial * ret.samplesLateral )
		throw new RuntimeException("OTF read data length mismatch: "+val.length+" "+
		    ret.samplesAxial+" "+ret.samplesLateral);

	    int i=0;
	    for (int  z=0;  z< ret.samplesAxial   ;  z++)  
	    for (int xy=0; xy< ret.samplesLateral ; xy++) { 
		ret.vals[b].set(xy,z , new Cplx.Float( val[2*i], val[2*i+1]));	    
		i++;
	    }
	}

	return ret;
    
    }


    /** Save the OTF to a conf object */
    public void saveConfig( Conf.Folder fld ) {

	fld.newDbl("NA").setVal( na );
	fld.newInt("emission").setVal( (int)lambda );
	fld.newDbl("n-immersion").setVal( immersion_n );

	fld.newStr("otf-name").val( otfName.trim() );
	fld.newStr("otf-meta").val( otfMeta );

	// write out data
	Conf.Folder data = fld.mk("data");
	data.newInt("bands").setVal( maxBand );


	data.newInt("samples-lateral").setVal( samplesLateral );
	data.newInt("samples-axial").setVal( samplesAxial );

	data.newDbl("cycles-lateral").setVal( cyclesPerMicronLateral );
	data.newDbl("cycles-axial").setVal( cyclesPerMicronAxial );
    
	for (int b=0; b<maxBand; b++) {
	    
	    // write the band to a float array
	    float [] fltBand = new float[  samplesAxial*samplesLateral*2 ];
	    int i=0;

	    for (int  z=0;  z< samplesAxial   ;  z++)  
	    for (int xy=0; xy< samplesLateral ; xy++) { 
		fltBand[i*2+0] = vals[b].get(xy,z).re;	    
		fltBand[i*2+1] = vals[b].get(xy,z).im;	    
		i++;
	    }

	    // store in a 'data' entry
	    data.newData(String.format("band-%d",b)).setVal( Conf.toByte( fltBand));

	}
    }



    /** Initialize OTF from raw float arrays */
    public static OtfProvider3D createFromData( 
	int nrBands,
	float [][] bandsData,
	double cyclMicronLateral, double cyclMicronAxial,
	int samplesLateral, int samplesAxial ) {

	OtfProvider3D ret = new OtfProvider3D();
	
	ret.na = 1.4;
	ret.lambda = 300;

	ret.maxBand = nrBands;

	ret.samplesLateral = samplesLateral;
	ret.samplesAxial   = samplesAxial;

	ret.cyclesPerMicronLateral = cyclMicronLateral;
	ret.cyclesPerMicronAxial   = cyclMicronAxial;

	// calculate the (prob. here wrong) cutoff
	ret.calcCutOff();

	// init bands
	ret.vals	= Vec2d.createArrayCplx( ret.maxBand, 
	    ret.samplesLateral, ret.samplesAxial );
	
	for (int b=0; b<ret.maxBand; b++) {
	    if (bandsData[b].length != 2*ret.samplesAxial * ret.samplesLateral )
		throw new RuntimeException("OTF read data length mismatch: "+bandsData[b].length+" "+
		    ret.samplesAxial+" "+ret.samplesLateral);
	   
	    float [] val = bandsData[b];

	    int i=0;
	    for (int  z=0;  z< ret.samplesAxial   ;  z++)  
	    for (int xy=0; xy< ret.samplesLateral ; xy++) { 
		ret.vals[b].set(xy,z , new Cplx.Float( val[2*i], val[2*i+1]));	    
		i++;
	    }

	}
	return ret;
    }

	
    public String getOtfInfoString() {

	String ret ="OTF (meta)data\n------";
	ret += "\n            name: "+otfName;
	ret += "\n            meta: "+otfMeta;
	ret += "\n              NA: "+String.format("%4.3f", na);
	ret += "\n     n immersion: "+String.format("%4.3f", immersion_n);
	ret += "\n           bands: "+maxBand;
	ret += "\n  em. wavelength: "+lambda;
	ret += "\n  samples lateal: "+samplesLateral;
	ret += "\n   samples axial: "+samplesAxial;
	ret += "\npxl size lateral: "+String.format("%7.5f",cyclesPerMicronLateral);
	ret += "\n  pxl size axial: "+String.format("%7.5f",cyclesPerMicronAxial);
	ret += "\n     lat. cutoff: "
	    +String.format("%7.5f (1/um) -> %5.0f nm", cutOffLateral, 1000/cutOffLateral);
	ret += "\n   axial. cutoff: "
	    +String.format("%7.5f (1/um) -> %5.0f nm", cutOffAxial, 1000/cutOffAxial);

	return ret;
    }







    // lots of setters / getters

    @Override
    public String toString() {
	return otfName.trim()+ " (@" +String.format("%4d nm)",(int)lambda);
    }


    public String getName() {
	return otfName;
    }
    public String getMeta() {
	return otfMeta;
    }

    public void setName( String name ){
	otfName = name.trim();
    }
    
    public void setMeta( String meta ){
	otfMeta = meta.trim();
    }

    public double getNA() {
	return na;
    }

    public double getLambda() {
	return lambda;
    }

    public void setNA( double in_na ) {
	na = in_na;
	calcCutOff();
    }
    
    public void setLambda( double in_lambda ) {
	lambda = in_lambda;
	calcCutOff();
    }


    void calcCutOff() {
	cutOffLateral = 1000 / (lambda / na /2);
	double n = immersion_n;
	cutOffAxial = (n - Math.sqrt( n*n - na*na ))/(lambda/1000);
    }



    // ------ Testing ------

    /** For testing, outputs some OTF to stdout */
    public static void main( String args [] ) throws Exception {

	if (args.length==0) {
	    System.out.println("Use: io - Input/Output OTF ");
	    return;
	}

	// timing
	/*
	if (args[0].equals("t")) {

	    otf.setPixelSize(0.023, 0.133);
	    Vec3d.Cplx [] test = Vec3d.createArrayCplx(15,512,512);

	    for (int loop=0; loop<5; loop++) {

		// Test1: Apply OTF on the fly
		Tool.Timer t2 = Tool.getTimer();
		t2.start();
		for (int i=0;i<15; i++) 
		    otf.applyOtf( test[i], 0 ) ;
		t2.stop();
		
		Tool.trace("OTF on-fly: "+t2);
	    }
	} */
	

	// output
	if (args[0].equals("io")) {
	    
	    Conf cfg = Conf.loadFile( args[1] );
	    OtfProvider3D otf = OtfProvider3D.loadFromConfig( cfg ); 
	
	    System.out.println("# Size: "+otf.cyclesPerMicronLateral+" "+otf.cyclesPerMicronAxial+
		 "( "+otf.vals[0].vectorWidth()+" "+ otf.vals[0].vectorHeight()+" ) ");
	    

	    // output the vector as read in
	    for (int l=1; l<otf.vals[0].vectorWidth();l++) {
		
		double accu0 = 0;
		double accu1 = 0;

		for (int z=1; z<otf.vals[0].vectorHeight();z++)  {
			System.out.println(String.format(" %5.3f %5.3f %6.4f %6.4f %6.4f #A",
			    l*otf.cyclesPerMicronLateral, z*otf.cyclesPerMicronAxial,
			    //l*1., z*1.,
			    otf.vals[0].get(l,z).re,
			    otf.vals[1].get(l,z).re,
			    otf.vals[2].get(l,z).re ));

			accu0 += otf.vals[0].get(l,z).abs();
		}
		System.out.println(String.format(" %5.3f %6.4f %6.4f %6.4f %6.4f %6.4f #A2d", 
		    l*otf.cyclesPerMicronLateral, accu0+otf.vals[0].get(l,0).abs(), accu0,
		    otf.vals[0].get(l,0).abs(),
		    otf.vals[0].get(l,1).abs(),
		    otf.vals[0].get(l,2).abs()
		    ));
	    }

	    for (float cycllat = 0; cycllat < 8; cycllat +=0.025 ) {
		for (float cyclax = 0; cyclax < 8; cyclax +=0.025 ) {
		    System.out.println(String.format(" %5.3f %5.3f %6.4f %6.4f %6.4f #B", 
			cycllat, cyclax, 
			otf.getOtfVal(0, cycllat, cyclax).re, 
			otf.getOtfVal(1, cycllat, cyclax).re, 
			otf.getOtfVal(2, cycllat, cyclax).re ));
		}
		System.out.println("   #B");
	    }
	}

	Tool.shutdown();
    }

}
