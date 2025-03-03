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

import org.fairsim.utils.Tool;
import ij.Prefs;

public class KeyValueProperties implements Tool.KeyValueStore {
    
    @Override
    public String retrieveString(String key) {
        return Prefs.getString(Prefs.KEY_PREFIX+"fairsim."+key);
    }
    @Override
    public boolean storeString(String key, String value) {
        Prefs.set("fairsim."+key,value);    
        Prefs.savePreferences();
        return true;
    }

    public String toString() {
        return "ImageJ key/value storage";
    }
    

}
