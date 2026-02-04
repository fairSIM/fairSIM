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

package org.fairsim.utils;

/* https://en.wikipedia.org/wiki/Base64 */

/** Base64 encode / decode */
public class Base64 {

    private static String codes = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";

    static byte[] decode(String input)    {
        
	byte decoded[] = new byte[((input.length() * 3) / 4) - 
		(input.indexOf('=') > 0 ? (input.length() - input.indexOf('=')) : 0)];
        char[] inChars = input.toCharArray();
        int j = 0;
        int b[] = new int[4];
        for (int i = 0; i < inChars.length; i += 4)     {
            // This could be made faster (but more complicated) by precomputing these index locations
            b[0] = codes.indexOf(inChars[i]);
            b[1] = codes.indexOf(inChars[i + 1]);
            b[2] = codes.indexOf(inChars[i + 2]);
            b[3] = codes.indexOf(inChars[i + 3]);
            decoded[j++] = (byte) ((b[0] << 2) | (b[1] >> 4));
            if (b[2] < 64)      {
                decoded[j++] = (byte) ((b[1] << 4) | (b[2] >> 2));
                if (b[3] < 64)  {
                    decoded[j++] = (byte) ((b[2] << 6) | b[3]);
	        }
            }
        }

        return decoded;
    }

    static String encode(byte[] in)       {
        StringBuffer out = new StringBuffer((in.length * 4) / 3);
        int b;
        for (int i = 0; i < in.length; i += 3)  {
            b = (in[i] & 0xFC) >> 2;
            out.append(codes.charAt(b));
            b = (in[i] & 0x03) << 4;
            if (i + 1 < in.length)      {
                b |= (in[i + 1] & 0xF0) >> 4;
                out.append(codes.charAt(b));
                b = (in[i + 1] & 0x0F) << 2;
                if (i + 2 < in.length)  {
                    b |= (in[i + 2] & 0xC0) >> 6;
                    out.append(codes.charAt(b));
                    b = in[i + 2] & 0x3F;
                    out.append(codes.charAt(b));
                } else  {
                    out.append(codes.charAt(b));
                    out.append('=');
                }
            } else      {
                out.append(codes.charAt(b));
                out.append("==");
            }
        }

        return out.toString();
    }

}
