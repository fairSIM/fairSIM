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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** OTF attenuation: strength 0..1, FWHM > 0 */
public class OtfAttenuationTest {

	@Test
	void validValuesAccepted() {
		OtfProvider otf = OtfProvider.fromEstimate(1.4, 525, 0.7);
		otf.setAttenuation(0, 1.2);
		otf.setAttenuation(1, 1.2);
		otf.setAttenuation(0.05, 0.01);
		// strength 1 removes DC completely, never below 0
		assertEquals(0, OtfProvider.valAttenuation(0, 1, 1.2), 1e-7);
	}

	@Test
	void invalidValuesRejected() {
		final OtfProvider otf = OtfProvider.fromEstimate(1.4, 525, 0.7);
		assertThrows(IllegalArgumentException.class, () -> otf.setAttenuation(1.5, 1.2));
		assertThrows(IllegalArgumentException.class, () -> otf.setAttenuation(-0.1, 1.2));
		assertThrows(IllegalArgumentException.class, () -> otf.setAttenuation(0.9, 0));
		assertThrows(IllegalArgumentException.class, () -> otf.setAttenuation(Double.NaN, 1.2));
	}
}
