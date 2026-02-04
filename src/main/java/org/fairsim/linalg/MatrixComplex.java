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



/** A simple implementation of complex-valued matrices. 
 *  (TODO: extent to real-valued types)
 * */
public class MatrixComplex {


    final private Cplx.Double [][] vals;
    final private int m,n;

    /** Initialize a matrix sizex i x j */
    public MatrixComplex(int i, int j) {
	m=i; n=j;
	vals = new Cplx.Double[i][j];
	for (int k1=0;k1<m;k1++)
	for (int k2=0;k2<n;k2++) vals[k1][k2] = Cplx.Double.zero();

    }


    /** Obtail unit-matrix sizes n x n */
    public static MatrixComplex unity(int n) {
	MatrixComplex ret = new MatrixComplex(n,n);
	for (int i=0;i<n;i++) ret.vals[i][i] = new Cplx.Double(1,0);
	return ret;
    }

    /** Set element i,j to a */
    public void	    set(int i, int j, Cplx.Double  a) { vals[i][j] = a; }
    /** Get element i,j */
    public Cplx.Double get(int i, int j ) { return vals[i][j]; }


    /** Returns a pivoted copy of the matrix. */
    public MatrixComplex pivot() {
	
	if (m!=n) throw new RuntimeException("not a square matrix");
	MatrixComplex p = MatrixComplex.unity(n);
	
	for(int i=0;i<n;i++)  {
		int max_j = i;
		for(int j=i; j<n; j++)
			if ( vals[j][i].hypot() > vals[max_j][i].hypot() ) 
			    max_j = j;
 
		if (max_j != i) {
		    for(int k=0;k<n;k++) {
			Cplx.Double tmp = p.vals[i][k];
			p.vals[i][k] = p.vals[max_j][k];
			p.vals[max_j][k] = tmp;
		    }
		}
		
	}
	return p;
    }
 
    /** Computes the LU-decomposition of the matrix.
     *	@return Array containing 3 matrices: L,U,P */
    public MatrixComplex [] decomposeLU()
    {
	if (m!=n) throw new RuntimeException("not a square matrix");
	
	MatrixComplex U = new MatrixComplex(n,n);
	MatrixComplex L = MatrixComplex.unity(n);
	MatrixComplex P = this.pivot();
	MatrixComplex Aprime = mult(P,this);


	for (int i=0;i<n;i++)
	for (int j=0;j<n;j++) {
		if (j <= i) {
		    Cplx.Double s = Cplx.Double.zero();
		    for( int k=0;k<j; k++) {
			s = Cplx.add( s, Cplx.mult( L.vals[j][k], U.vals[k][i] ) );
		    }
		    U.vals[j][i] = Cplx.sub( Aprime.vals[j][i] , s );
		}
		if (j >= i) {
		    Cplx.Double s = Cplx.Double.zero();
		    for( int k=0;k<i; k++)
			s = Cplx.add( s, Cplx.mult ( L.vals[j][k] , U.vals[k][i]));
		    L.vals[j][i] = Cplx.div( Cplx.sub( Aprime.vals[j][i] , s) , U.vals[i][i] );

		}
	}
	return new MatrixComplex [] { L,U,P };
    }

    
   
    /** Multiplies matrices. */
    public MatrixComplex mult(MatrixComplex b) {
	return mult(this,b);
    }

    /** Initialize from real-valued array. Row/col order 
     *  as in C, imaginary component is set to 0. */
    public static MatrixComplex initReal( int m, int n , double [] dat ) {
	MatrixComplex ret = new MatrixComplex(m,n);
	for (int i=0;i<m;i++)
	for (int j=0;j<n;j++)
	    ret.vals[i][j] = new Cplx.Double(dat[i*m+j]);
	return ret;
    }


    /** Multiply matrix. */
    public static MatrixComplex mult( MatrixComplex a, MatrixComplex b ) {
	
	if (a.n!=b.m) throw new RuntimeException("matrix dimension mismatch");
	
	MatrixComplex c = new MatrixComplex(a.m,b.n);
	for (int i=0;i<a.m;i++)
	for (int k=0;k<a.n;k++) 
	for (int j=0;j<b.n;j++) {
	    c.vals[i][j] = Cplx.add( 
		c.vals[i][j], Cplx.mult( a.vals[i][k] , b.vals[k][j]));
	}
	return c;
    }

    /* @deprecated Not sure why this is here */
    @Deprecated
    public Cplx.Double [] mult( Cplx.Double [] b) {
	return mult(this,b);
    }

    /** Creates a matrix sizes g x h, filled with random
     *  values. See {@link Cplx.Double#random}. */
    public static MatrixComplex random(int g, int h) {
	MatrixComplex ret = new MatrixComplex(g,h);
	for (int i=0;i<ret.m; i++)
	for (int j=0;j<ret.n; j++)
	    ret.vals[i][j] = Cplx.Double.random();
	return ret;
    }


    /** Multiply matrix x vector */
    public static Cplx.Double [] mult( MatrixComplex a, Cplx.Double [] b ) {
	
	if (a.n!=b.length) throw new RuntimeException("vector dimension mismatch");
	
	Cplx.Double [] c = new Cplx.Double[a.n];
	for (int i=0;i<c.length;i++) c[i] = Cplx.Double.zero();
	for (int i=0;i<a.m;i++)
	for (int k=0;k<a.n;k++) {
	    c[i] = Cplx.add( c[i], Cplx.mult( a.vals[i][k] , b[k]));
	    
	}
	return c;
    }

    /** Returns x that solves A*x=b via LU-decomposition. Effectively,
     *  this computes the inverse matrix and solves the linear equation system
     *  in one function call. */
    public Cplx.Double [] solve( Cplx.Double [] b) {  
	if (n!=b.length) throw new RuntimeException("vector dimension mismatch");
	if (m!=n) throw new RuntimeException("not a square matrix");
    
	MatrixComplex [] decomp = decomposeLU();
	MatrixComplex L=decomp[0], U=decomp[1], P=decomp[2];
	
	Cplx.Double [] b2 = P.mult(b);

	Cplx.Double [] x = new Cplx.Double[n];
	Cplx.Double [] y = new Cplx.Double[n];

	// forward
	for (int i=0;i<n;i++) {
	    Cplx.Double res = b2[i];
	    for (int j=0;j<i;j++)
		res=Cplx.sub( res, Cplx.mult( L.vals[i][j] , y[j]));
	    y[i] = Cplx.div( res , L.vals[i][i] );
	}

	// backward
	for (int i=n-1;i>=0;i--) {
	    Cplx.Double res = y[i];
	    for (int j=i+1;j<n;j++)
		res=Cplx.sub( res, Cplx.mult( U.vals[i][j], x[j]));
	    x[i] = Cplx.div( res , U.vals[i][i]);
	}

	return x;
    }


    /** Returns the inverse matrix via LU-decomposition. */
    public MatrixComplex inverse() {  
	if (m!=n) throw new RuntimeException("not a square matrix");
    
	MatrixComplex [] decomp = decomposeLU();
	MatrixComplex L=decomp[0], U=decomp[1], P=decomp[2];
	MatrixComplex y = new MatrixComplex(n,n);	
	MatrixComplex x = new MatrixComplex(n,n);	
	// b is given by the pivot

	// forward
	for (int k=0;k<n;k++)
	for (int i=0;i<n;i++) {
	    Cplx.Double res = P.vals[i][k];
	    for (int j=0;j<i;j++)
		res=Cplx.sub( res, Cplx.mult ( L.vals[i][j], y.vals[j][k] ));
	    y.vals[i][k]  = Cplx.div( res , L.vals[i][i] );
	}

	// backward
	for (int k=0;k<n;k++)
	for (int i=n-1;i>=0;i--) {
	    Cplx.Double res = y.vals[i][k];
	    for (int j=i+1;j<n;j++)
		res=Cplx.sub( res, Cplx.mult ( U.vals[i][j], x.vals[j][k] ));
	    x.vals[i][k] = Cplx.div( res  , U.vals[i][i] );
	}

	return x;
    }


    /** Returns a conjugated, transposed copy of the matrix */
    public MatrixComplex conjTranspose() {
	MatrixComplex ret = new MatrixComplex(n,m);
	for (int i=0;i<m;i++)
	for (int j=0;j<n;j++)
	    ret.vals[j][i] = Cplx.conj( vals[i][j] );
	return ret;
    }



    /** Returns the Moore-Penrose pseudo-inverse.
     *  This is calculated directly by A+ = (A* A)^-1 A* */
    public MatrixComplex pseudoInverse() {
	MatrixComplex adj = this.conjTranspose();
	//output(adj);
	MatrixComplex adjxa = adj.mult( this );
	//output(adjxa);
	
	MatrixComplex inv = adjxa.inverse();
	return inv.mult(adj);
    }


    /* @deprecated Not sure why this is here */
    @Deprecated
    static void output( double [] a ) {
	    String ret="";
	    for (int j=0;j<a.length;j++)
		ret+=String.format("%3.5f ",a[j]);
	    System.out.println(ret);
	System.out.println("---");

    }

    /* @deprecated Maybe use toString() instead?  */
    @Deprecated
    static void output( MatrixComplex a) { a.output(); }

    /** Computes sum |A_ij-E_ij|, to help with inversion testing */
    double diffToEye() {
	double ret =0;
	for (int i=0;i<m;i++)
	for (int j=0;j<n;j++)
	    if (i==j)
		ret += Cplx.sub( vals[i][j] , new Cplx.Double(1)).hypot();
	    else 
		ret += vals[i][j].hypot();
	return ret;
    }

    
    /** Writes the matrix to System.out. TODO: Why not toString? */
    void output() {
	for (int i=0;i<m;i++) {
	    String ret="";
	    for (int j=0;j<n;j++)
		ret+=" "+vals[i][j];
	    System.out.println(ret);
	}
	System.out.println("---");
    }
   

    /** Tests the inversion routine */
    public static void testInverse( int n ) {

	for (int i=0;i<5;i++) {
	    MatrixComplex a = MatrixComplex.random(n,n);
	    MatrixComplex inv = a.inverse();
	    System.out.println( String.format("%3.5e" ,
		inv.mult(a).diffToEye() ));
	}
    }

    /** Tests the pseudo inversion routine */
    public static void testPseudoInverse( int m, int n ) {

	for (int i=0;i<5;i++) {
	    MatrixComplex a = MatrixComplex.random(m,n);
	    MatrixComplex inv = a.pseudoInverse();
	    System.out.println( String.format("%3.5e" ,
		inv.mult(a).diffToEye() ));
	}
    }


    /** Small main routine checking the pseudo-inverse
     *  and inversion routines for correctness. */
    public static void main(String [] args) {
	
	if (args.length<2) {
	    System.out.println("provide n,m");
	    return;
	}

	int mIn = Integer.parseInt( args[0]);
	int nIn = Integer.parseInt( args[1]);
	
	MatrixComplex A4 = MatrixComplex.random(mIn,nIn);
	output( A4 );
	output( A4.pseudoInverse() );
	//A4.pseudoInverse() ;
	output( mult( A4.pseudoInverse(), A4));

	if (args.length==2) {
	    testPseudoInverse( mIn, nIn );
	}

    }


    /** Outputs the matrix as (somewhat) formatted string */
    public String toString() {
	String ret="[";
	for (int i=0;i<vals.length;i++) {
	    ret+=" [";
	    for (int j=0;j<vals[i].length;j++)
		ret+=vals[i][j];
	    ret+="]";
	}
	return ret;

    }


}



