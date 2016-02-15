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

/**
 * Rudimentary linear algebra
 * and FFTs on 1d- and 2d-data structures, and other
 * math functions.
 * <p>
 * Real- and complex-valued vectors in {@link org.fairsim.linalg.Vec}, extension
 * to 2D in {@link org.fairsim.linalg.Vec2d}. Fourier-transformation of these
 * vectors through methods in {@link org.fairsim.linalg.Transforms}.
 *
 * <p>
 * Complex numbers in {@link org.fairsim.linalg.Cplx} (based on floati and double),
 * Complex-valued matrix in {@link org.fairsim.linalg.MatrixComplex}.
 *
 * <p>
 * Some (re-)implementations of mathematical functions in {@link org.fairsim.linalg.MTool}
 * 
 */
package org.fairsim.linalg;
