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

import ij.plugin.PlugIn;
import ij.Prefs;

import org.fairsim.utils.Tool;
import org.fairsim.utils.Conf;

/**
 * Small Fiji plugin, running all parameter estimation and reconstruction
 * steps. Good starting point to look at the code w/o going through all the
 * GUI components.
 */
public class TestConfigStorage implements PlugIn {

	/**
	 * Called by Fiji to start the plugin.
	 */
	public void run(String arg) {

	}

	/** Start from the command line to run the plugin */
	public static void main(String[] arg) {

		new ij.ImageJ(ij.ImageJ.EMBEDDED);
		Tool.setKeyValueStore(new KeyValueProperties());

		if (arg.length < 2) {
			System.out.println("Usage: test-A key [value]");
			System.out.println("If no value is provided, key is queried and value returned");
			System.out.println("If value is provided, value is set for key");
			System.out.println("Usage: test-B read/write");
			System.out.println("Read from or write to the default config");
			return;
		}

		if (arg[0].startsWith("test-A")) {

			if (arg.length == 2) {
				System.out.println("Reading key: " + arg[1]);
				String res = Tool.getString(arg[1]);
				if (res == null) {
					System.out.println("-- key not found --");
				} else {
					System.out.println(res);
				}
			}
			if (arg.length == 3) {
				System.out.println("Setting key " + arg[1] + "=" + arg[2]);
				Tool.setString(arg[1], arg[2]);
			}

		}

		if (arg[0].startsWith("test-B")) {

			if (arg[1].startsWith("read")) {
				Conf cfg = Tool.getDefaultConfig();
				System.out.println(cfg.visualizedString());
			}

			if (arg[1].startsWith("write")) {
				Conf cfg = Tool.getDefaultConfig();
				cfg.r().newStr("timestamp").setVal(
						Tool.readableTimeStampSeconds(System.currentTimeMillis() / 1000, true));
				Tool.writeDefaultConfig(cfg);

			}
		}

	}

}
