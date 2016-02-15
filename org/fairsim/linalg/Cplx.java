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

package org.fairsim.linalg;

/** Class storing complex numbers with double precision. */
public class Cplx {

    /** Double-valued complex numbers */
    static public class Double {

	/** real component. Final, thus read-only */
	final public double re; 
	/** imaginary component. Final, thus read-only */
	final public double im;
	
	/** Create number from real and imaginary part */ 
	public Double(double r, double i) { re=r; im=i; }
	
	/** Create number from real part, imaginary part 0 */
	public Double(double r) { re=r; im=0; }
	

	/** Return (1,0) */
	public static Cplx.Double one() { return new Cplx.Double(1,0); }
	/** Returns (0,0) */
	public static Cplx.Double zero() { return new Cplx.Double(0,0); }
	/** Returns (0,1) */
	public static Cplx.Double i() { return new Cplx.Double(0,1); }

	/** Returns the absolute value, fast (see {@link #hypot}).
	 *  This just returns Math.sqrt(re*re+im*im), o.k. in most cases, 
	 *  but might become imprecise or even overflow for large re or im. */
	public double abs()   { return  Math.hypot(re, im); }

	/** Returns the absolute value, precise (see {@link #abs}).
	 *  This uses Math.hypot(re,im), thus does not overflow, but
	 *  it slower (see {@link MTool#fhypot}) */
	public double hypot() { return  Math.hypot(re, im); }

	/** Returns the angle in the complex plane (atan2) */
	public double phase() { return  Math.atan2(im, re); }

	/** Returns re^2+im^2 */
	public double absSq() { return re*re + im*im; }

	/** Sets both components random in [-1;1] */
	public static Cplx.Double random() {
	    return new Cplx.Double( Math.random()*2-1, Math.random()*2-1);
	}

	/** Return this number in single prec. */
	public Cplx.Float toFlt() {
	    return new Cplx.Float( (float)re, (float)im);
	}
	

	/** Converts to a 2-component float array */
	public float [] toFlA() { return new float [] { (float)re, (float)im }; }

	/** Returns angle a in complex plane, with unit length 1 */
	public static Cplx.Double fromPhase( double a) {
	    return new Cplx.Double( Math.cos(a), Math.sin(a));
	}

	@Override
	public String toString() {
	    return String.format("[% 3.4f % 3.4f]",re,im);
	}

	/** Returns this + b */
	public Cplx.Double add (  Cplx.Double b) {
	    return Cplx.add(this,b);
	}
	
	/** Returns this - b */
	public Cplx.Double sub ( Cplx.Double b) {
	    return Cplx.sub(this,b);
	}
	
	/** Returns this * b */
	public Cplx.Double mult( Cplx.Double b) {
	    return Cplx.mult(this,b);
	}
	
	/** Returns this * b */
	public Cplx.Double mult( double b) {
	    return Cplx.mult(this,b);
	}


	/** Returns conj(this) */
	public Cplx.Double conj() {
	    return Cplx.conj( this );
	}
	/** Returns 1/this */
	public Cplx.Double reciproc() {
	    return Cplx.reciproc(this);
	}
	/** Returns this/b */
	public Cplx.Double div( Cplx.Double b) {
	    return Cplx.div( this, b);
	}
	/** Returns this/b */
	public Cplx.Double div( double b) {
	    return Cplx.div( this, b);
	}
    }



    /** Float-valued complex numbers */
    static public class Float {

	/** real component. Final, thus read-only */
	final public float re; 
	/** imaginary component. Final, thus read-only */
	final public float im;
	
	/** Create number from real and imaginary part */ 
	public Float(float r, float i) { re=r; im=i; }
	
	/** Create number from real part, imaginary part 0 */
	public Float(float r) { re=r; im=0; }
	

	/** Return (1,0) */
	public static Cplx.Float one() { return new Cplx.Float(1,0); }
	/** Returns (0,0) */
	public static Cplx.Float zero() { return new Cplx.Float(0,0); }
	/** Returns (0,1) */
	public static Cplx.Float i() { return new Cplx.Float(0,1); }

	/** Extracts complex number from packed array at position i. This
	 *  is a convenience method to access arrays with standard structure:
	 *  x[2*i] = real, x[2*i+1] = imag. */
	public static Cplx.Float at( float [] v, int i) {
	    return new Cplx.Float( v[2*i], v[2*i+1] );
	}
	
	/** Returns the absolute value, fast (see {@link #hypot}).
	 *  This just returns Math.sqrt((double)re*re+(double)im*im), 
	 *  which for float should be o.k. in almost every case. */
	public float abs()   { 
	    return  (float)Math.sqrt((double)re*re + (double)im*im); 
	}

	/** Returns the absolute value, precise (see {@link #abs}).
	 *  This uses Math.hypot(re,im), thus does all error handling, but
	 *  it slower (see {@link MTool#fhypot}) */
	public float hypot()   { return  (float)Math.hypot(re, im); }

	/** Returns the angle in the complex plane (atan2) */
	public float phase() { return  (float)Math.atan2(im, re); }

	/** Returns re^2+im^2 */
	public float absSq() { return re*re + im*im; }
	
	/** Sets both components random in [-1;1] */
	public static Cplx.Float random() {
	    return new Cplx.Float( (float)Math.random()*2-1, (float)Math.random()*2-1);
	}

	/** Converts to a 2-component float array */
	public float [] toFlA() { return new float [] { (float)re, (float)im }; }
	
	/** Return this number in double prec. */
	public Cplx.Double toDbl() {
	    return new Cplx.Double( re, im);
	}

	/** Returns angle a in complex plane, with unit length 1 */
	public static Cplx.Float fromPhase( double a) {
	    return new Cplx.Float( (float)Math.cos(a), (float)Math.sin(a));
	}

	@Override
	public String toString() {
	    return String.format("[% 3.4f % 3.4f]",re,im);
	}


	/** Returns this + b */
	public Cplx.Float add (  Cplx.Float b) {
	    return Cplx.add(this,b);
	}
	/** Returns this - b */
	public Cplx.Float sub ( Cplx.Float b) {
	    return Cplx.sub(this,b);
	}
	/** Returns this * b */
	public Cplx.Float mult( Cplx.Float b) {
	    return Cplx.mult(this,b);
	}
	
	/** Returns this * b */
	public Cplx.Float mult( float b) {
	    return Cplx.mult(this,b);
	}

	/** Returns conj(this) */
	public Cplx.Float conj() {
	    return Cplx.conj( this );
	}
	/** Returns 1/this */
	public Cplx.Float reciproc() {
	    return Cplx.reciproc(this);
	}
	/** Returns this/b */
	public Cplx.Float div( Cplx.Float b) {
	    return Cplx.div( this, b);
	}
	/** Returns this/b */
	public Cplx.Float div( float b) {
	    return Cplx.div( this, b);
	}

    }

    // ==========================================================


    /** Extracts complex number from packed array at position i. This
     *  is a convenience method to access arrays with standard structure:
     *  x[2*i] = real, x[2*i+1] = imag. */
    public static Cplx.Float at( float [] v, int i) {
	return new Cplx.Float( v[2*i], v[2*i+1] );
    }
    
    /** Extracts complex number from packed array at position i. This
     *  is a convenience method to access arrays with standard structure:
     *  x[2*i] = real, x[2*i+1] = imag. */
    public static Cplx.Double at( double [] v, int i) {
	return new Cplx.Double( v[2*i], v[2*i+1] );
    }


    /** Returns a + b */
    public static Cplx.Double add ( Cplx.Double a, Cplx.Double b) {
	return new Cplx.Double( a.re+b.re , a.im+b.im );
    }
    /** Returns a - b */
    public static Cplx.Double sub ( Cplx.Double a, Cplx.Double b) {
	return new Cplx.Double( a.re-b.re , a.im-b.im );
    }
    /** Returns a * b */
    public static Cplx.Double mult( Cplx.Double a, Cplx.Double b) {
	return new Cplx.Double( a.re * b.re - a.im * b.im, a.re * b.im + a.im * b.re);
    }
    
    /** Returns a * b */
    public static Cplx.Double mult( Cplx.Double a, double b) {
	return new Cplx.Double( a.re * b, a.im * b );
    }
    
    /** Returns conj(a) */
    public static Cplx.Double conj( Cplx.Double a) {
	return new Cplx.Double( a.re, -a.im);
    }
    /** Returns 1/a */
    public static Cplx.Double reciproc( Cplx.Double a ) {
	double s = a.re*a.re + a.im*a.im;
	return new Cplx.Double( a.re/s, -a.im/s);
    }
    /** Returns a/b */
    public static Cplx.Double div( Cplx.Double a, Cplx.Double b) {
	return ( mult( a , reciproc(b)));	
    }
    /** Returns a/b */
    public static Cplx.Double div( Cplx.Double a, double b) {
	return new Cplx.Double( a.re / b, a.im / b );
    }

    /** Returns a + b */
    public static Cplx.Float add ( Cplx.Float a, Cplx.Float b) {
	return new Cplx.Float( a.re+b.re , a.im+b.im );
    }
    /** Returns a - b */
    public static Cplx.Float sub ( Cplx.Float a, Cplx.Float b) {
	return new Cplx.Float( a.re-b.re , a.im-b.im );
    }
    /** Returns a * b */
    public static Cplx.Float mult( Cplx.Float a, Cplx.Float b) {
	return new Cplx.Float( a.re * b.re - a.im * b.im, a.re * b.im + a.im * b.re);
    }
    /** Returns a * b */
    public static Cplx.Float mult( Cplx.Float a, float b) {
	return new Cplx.Float( a.re * b, a.im * b );
    }
    
    /** Returns conj(a) */
    public static Cplx.Float conj( Cplx.Float a) {
	return new Cplx.Float( a.re, -a.im);
    }
    /** Returns 1/a */
    public static Cplx.Float reciproc( Cplx.Float a ) {
	float s = a.re*a.re + a.im*a.im;
	return new Cplx.Float( a.re/s, -a.im/s);
    }
    /** Returns a/b */
    public static Cplx.Float div( Cplx.Float a, Cplx.Float b) {
	return ( mult( a , reciproc(b)));	
    }
    /** Returns a/b */
    public static Cplx.Float div( Cplx.Float a, float b) {
	return new Cplx.Float( a.re / b, a.im / b );
    }

    
    /** Helper function, real part of complex mult */
    static float multReal( float Xr, float Xi, float Yr, float Yi ) {
	    return ((Xr * Yr ) - (Xi*Yi));
    }
    /** Helper function, imag part of complex mult */
    static float multImag( float Xr, float Xi, float Yr, float Yi ) {
	    return ((Xi * Yr ) + (Xr*Yi));
    }



}
