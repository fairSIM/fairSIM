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

/** Classes implementing this interface provide means of creating 
 * vector objects.
 * By calling the corresponting methods, a vector of a given
 * size and dimensionality is created by the factory. This
 * should e.g. be used when creating vectors that live in
 * native code or on an accel. card (GPU, etc.).
 **/
public interface VectorFactory {

    /** Return a one-dimensional, real-valued vector, with n elements. */
    public Vec.Real createReal(int n);
    
    /** Return a one-dimensional, complex-valued vector, with n elements. */
    public Vec.Cplx createCplx(int n);
   
    
    /** Return a two-dimensional, real-valued vector, sized w x h. */
    public Vec2d.Real createReal2D(int w, int h);
    
    /** Return a two-dimensional, complex-valued vector, sized w x h. */
    public Vec2d.Cplx createCplx2D(int w, int h);
    
    /** Return a two-dimensional, real-valued vector, sized w x h x d. */
    public Vec3d.Real createReal3D(int w, int h, int d);
    
    /** Return a two-dimensional, complex-valued vector, sized w x h x d. */
    public Vec3d.Cplx createCplx3D(int w, int h, int d);


    
    /** Finish whatever parallel / concurrent process is running (for timing CUDA, etc.) */
    public void syncConcurrent();

}


